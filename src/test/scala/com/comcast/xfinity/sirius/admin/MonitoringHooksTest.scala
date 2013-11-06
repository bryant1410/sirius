package com.comcast.xfinity.sirius.admin

import com.comcast.xfinity.sirius.api.SiriusConfiguration
import javax.management.{ObjectName, MBeanServer}
import com.comcast.xfinity.sirius.{TimedTest, NiceTest}
import org.mockito.Mockito._
import org.mockito.Matchers.{eq => meq, any}
import akka.testkit.TestActorRef
import java.util.{HashMap => JHashMap, Hashtable => JHashtable}
import com.typesafe.config.ConfigFactory
import akka.actor.{ActorRef, ActorSystem, Actor}

object MonitoringHooksTest {

  class DummyMonitor {}

  class MonitoredActor(monitor: => Any, config: SiriusConfiguration) extends Actor with MonitoringHooks {
    def receive = {
      // ping/pong message to verify that the nod has finished preStart
      case 'register => registerMonitor(monitor, config)
      case 'unregister => unregisterMonitors(config)
    }
  }
}

class MonitoringHooksTest extends NiceTest with TimedTest {

  import MonitoringHooksTest._

  implicit val actorSystem = ActorSystem("MonitoringHooksTest")

  it ("should register all monitors as expected when registered to a local actor system," +
      "and properly clean up on exit, if we are configured to do so") {
    val mockMbeanServer = mock[MBeanServer]
    val siriusConfig = new SiriusConfiguration
    siriusConfig.setProp(SiriusConfiguration.MBEAN_SERVER, mockMbeanServer)

    val monitor = new DummyMonitor

    val mockObjectNameHelper = mock[ObjectNameHelper]

    val monitoredActor = TestActorRef(
      new MonitoredActor(monitor, siriusConfig) {
        override val objectNameHelper = mockObjectNameHelper
      }, "test"
    )

    val expectedObjectName = new ObjectName("com.comcast.xfinity.sirius:name=Sprinkles")
    doReturn(expectedObjectName).when(mockObjectNameHelper).
      getObjectName(meq(monitor), meq(monitoredActor), meq(actorSystem))

    monitoredActor ! 'register

    verify(mockObjectNameHelper).getObjectName(meq(monitor), any[ActorRef], meq(actorSystem))

    verify(mockMbeanServer).registerMBean(meq(monitor), meq(expectedObjectName))

    monitoredActor ! 'unregister
    verify(mockMbeanServer).unregisterMBean(meq(expectedObjectName))

  }

  it ("should do nothing if the MBeanServer is not configured") {
    var wasCalled = false
    val monitoredActor = TestActorRef(
      new MonitoredActor(
        {wasCalled = true; new DummyMonitor},
        new SiriusConfiguration
      ), "test"
    )

    monitoredActor ! 'register
    assert(false === wasCalled)
  }
}