package com.comcast.xfinity.sirius.uberstore

import java.io.File
import org.scalatest.BeforeAndAfterAll
import com.comcast.xfinity.sirius.NiceTest
import com.comcast.xfinity.sirius.api.impl.{Delete, OrderedEvent}

class UberStoreITest extends NiceTest with BeforeAndAfterAll {

  val tempDir: File = {
    val tempDirName = "%s/uberstore-itest-%s".format(
      System.getProperty("java.io.tmpdir"),
      System.currentTimeMillis()
    )
    val dir = new File(tempDirName)
    dir.mkdirs()
    dir
  }

  override def afterAll {
    tempDir.delete()
  }

  // XXX: not these sub tasks are not parallelizable
  describe("During an interesting series of events...") {
    var uberStore = UberStore(tempDir.getAbsolutePath)

    it ("must have no events when empty") {
      assert(Nil === getAllEvents(uberStore))
    }

    it ("must properly report the next sequence number when empty") {
      assert(1L === uberStore.getNextSeq)
    }

    val events1Through100 = generateEvents(1, 100)
    it ("must be able to accept and retain a bunch of Delete events") {
      events1Through100.foreach(uberStore.writeEntry(_))
      assert(events1Through100 === getAllEvents(uberStore))
    }

    it ("must not accept an event out of order") {
      intercept[IllegalArgumentException] {
        uberStore.writeEntry(OrderedEvent(1, 5, Delete("is so fat")))
      }
    }

    it ("must properly report the next sequence number when dataful") {
      assert(101L === uberStore.getNextSeq)
    }

    val events101Through200 = generateEvents(101, 200)
    it ("must cleanly transfer to a new handle") {
      // XXX: One UberStore to rule them all, close and hide the other one so
      //      we dont' accidentally use it
      uberStore.close()
      uberStore = UberStore(tempDir.getAbsolutePath)

      assert(101L === uberStore.getNextSeq)
      assert(events1Through100 === getAllEvents(uberStore))

      events101Through200.foreach(uberStore.writeEntry(_))
      assert(201L === uberStore.getNextSeq)
      assert(events1Through100 ++ events101Through200 === getAllEvents(uberStore))
    }

    it ("must be able to recover from a missing index") {
      val file = new File(tempDir, "1.index")
      assert(file.exists(), "Your test is hosed, expecting 1.index to exist")
      file.delete()
      assert(!file.exists(), "Your test is hosed, expecting 1.index to be bye bye")

      assert(events1Through100 ++ events101Through200 === getAllEvents(uberStore))
    }
  }

  private def generateEvents(start: Long, end: Long): List[OrderedEvent] =
    List.range(start, end + 1).map(n => OrderedEvent(n, n + 1000L, Delete(n.toString)))

  private def getAllEvents(uberStore: UberStore): List[OrderedEvent] = {
    val reversedEvents = uberStore.foldLeft(List[OrderedEvent]())((acc, e) => e :: acc)
    reversedEvents.reverse
  }
}