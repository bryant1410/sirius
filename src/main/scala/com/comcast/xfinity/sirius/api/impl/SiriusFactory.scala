package com.comcast.xfinity.sirius.api.impl

import java.io.File
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.util.{HashMap => JHashMap}

import com.comcast.xfinity.sirius.admin.ObjectNameHelper
import com.comcast.xfinity.sirius.api.RequestHandler
import com.comcast.xfinity.sirius.api.SiriusConfiguration
import com.comcast.xfinity.sirius.info.SiriusInfo
import com.comcast.xfinity.sirius.writeaheadlog.CachedSiriusLog
import com.comcast.xfinity.sirius.writeaheadlog.SiriusLog
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import akka.actor.ActorRef
import akka.actor.ActorSystem
import javax.management.ObjectName
import com.comcast.xfinity.sirius.uberstore.segmented.SegmentedUberStore
import com.comcast.xfinity.sirius.uberstore.UberStore

/**
 * Provides the factory for [[com.comcast.xfinity.sirius.api.impl.SiriusImpl]] instances
 */
object SiriusFactory {

  /**
   * SiriusImpl factory method, takes parameters to construct a SiriusImplementation and the dependent
   * ActorSystem and return the created instance.  Calling shutdown on the produced SiriusImpl will also
   * shutdown the dependent ActorSystem.
   *
   * @param requestHandler the RequestHandler containing callbacks for manipulating the system's state
   * @param siriusConfig a SiriusConfiguration containing configuration info needed for this node.
   * @see SiriusConfiguration for info on needed config.
   *
   * @return A SiriusImpl constructed using the parameters
   */
  def createInstance(requestHandler: RequestHandler, siriusConfig: SiriusConfiguration): SiriusImpl = {
    val uberStoreDir = siriusConfig.getProp[String](SiriusConfiguration.LOG_LOCATION) match {
      case Some(dir) => dir
      case None =>
        throw new IllegalArgumentException(SiriusConfiguration.LOG_LOCATION + " must be set on config")
    }

    val backendLog = {
      siriusConfig.getProp(SiriusConfiguration.LOG_VERSION_ID, "") match {
        case version if version == SegmentedUberStore.versionId => SegmentedUberStore(uberStoreDir, siriusConfig)
        case _ => UberStore(uberStoreDir)
      }
    }

    val log: SiriusLog = {
      if (siriusConfig.getProp(SiriusConfiguration.LOG_USE_WRITE_CACHE, true)) {
        val cacheSize = siriusConfig.getProp(SiriusConfiguration.LOG_WRITE_CACHE_SIZE, 10000)
        CachedSiriusLog(backendLog, cacheSize)
      } else {
        backendLog
      }
    }

    createInstance(requestHandler, siriusConfig, log)
  }

  /**
   * USE ONLY FOR TESTING HOOK WHEN YOU NEED TO MOCK OUT A LOG.
   * Real code should use the two argument factory method.
   *
   * @param requestHandler the RequestHandler containing callbacks for manipulating the system's state
   * @param siriusConfig a SiriusConfiguration containing configuration info needed for this node.
   * @see SiriusConfiguration for info on needed config.
   * @param siriusLog the persistence layer to which events should be committed to and replayed from.
   *
   * @return A SiriusImpl constructed using the parameters
   */
  private[sirius] def createInstance(requestHandler: RequestHandler, siriusConfig: SiriusConfiguration,
                   siriusLog: SiriusLog): SiriusImpl = {

    val systemName = siriusConfig.getProp(SiriusConfiguration.AKKA_SYSTEM_NAME, "sirius-system")

    implicit val actorSystem = ActorSystem(systemName, createActorSystemConfig(siriusConfig))

    // inject an mbean server, without regard for the one that may have been there
    val mbeanServer = ManagementFactory.getPlatformMBeanServer
    siriusConfig.setProp(SiriusConfiguration.MBEAN_SERVER, mbeanServer)

    // here it is! the real deal creation
    val impl = SiriusImpl(requestHandler, siriusLog, siriusConfig)

    // create a SiriusInfo MBean which will remain registered until we explicity shutdown sirius
    val (siriusInfoObjectName, siriusInfo) = createSiriusInfoMBean(actorSystem, impl.supervisor)
    mbeanServer.registerMBean(siriusInfo, siriusInfoObjectName)

    // need to shut down the actor system and unregister the mbeans when sirius is done
    impl.onShutdown({
      actorSystem.shutdown()
      actorSystem.awaitTermination()
      mbeanServer.unregisterMBean(siriusInfoObjectName)
    })

    impl
  }

  private def createSiriusInfoMBean(actorSystem: ActorSystem, siriusSup: ActorRef): (ObjectName, SiriusInfo) = {
    val siriusInfo = new SiriusInfo(actorSystem, siriusSup)
    val objectNameHelper = new ObjectNameHelper
    val siriusInfoObjectName = objectNameHelper.getObjectName(siriusInfo, siriusSup, actorSystem)
    (siriusInfoObjectName, siriusInfo)
  }

  /**
   * Creates configuration for the ActorSystem. The config precedence is as follows:
   *   1) host/port config trump all
   *   2) siriusConfig supplied external config next
   *   3) sirius-akka-base.conf, packaged with sirius, loaded with ConfigFactory.load
   */
  private def createActorSystemConfig(siriusConfig: SiriusConfiguration): Config = {
    val hostPortConfig = createHostPortConfig(siriusConfig)
    val externalConfig = createExternalConfig(siriusConfig)

    val baseAkkaConfig = ConfigFactory.load("sirius-akka-base.conf")

    hostPortConfig.withFallback(externalConfig).withFallback(baseAkkaConfig)
  }

  private def createHostPortConfig(siriusConfig: SiriusConfiguration): Config = {
    val configMap = new JHashMap[String, Any]()

    configMap.put("akka.remote.netty.hostname",
      siriusConfig.getProp(SiriusConfiguration.HOST, InetAddress.getLocalHost.getHostName))
    configMap.put("akka.remote.netty.port",
      siriusConfig.getProp(SiriusConfiguration.PORT, 2552))

    // this is just so that the intellij shuts up
    ConfigFactory.parseMap(configMap.asInstanceOf[JHashMap[String, _ <: AnyRef]])
  }

  /**
   * If siriusConfig is such configured, will load up an external configuration
   * for the Akka ActorSystem which is created. The filesystem is checked first,
   * then the classpath, if neither exist, or siriusConfig is not configured as
   * much, then an empty Config object is returned.
   */
  private def createExternalConfig(siriusConfig: SiriusConfiguration): Config =
    siriusConfig.getProp[String](SiriusConfiguration.AKKA_EXTERN_CONFIG) match {
      case None => ConfigFactory.empty()
      case Some(externConfig) =>
        val externConfigFile = new File(externConfig)
        if (externConfigFile.exists()) {
          ConfigFactory.parseFile(externConfigFile)
        } else {
          ConfigFactory.parseResources(externConfig)
        }
    }
}