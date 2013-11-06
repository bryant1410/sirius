package com.comcast.xfinity.sirius.writeaheadlog

import com.comcast.xfinity.sirius.NiceTest
import org.mockito.Mockito._
import com.comcast.xfinity.sirius.api.impl.OrderedEvent
import com.comcast.xfinity.sirius.api.impl.Delete

class CachedSiriusLogTest extends NiceTest {

  describe("CachedSiriusLog") {
    it("should add writes to in-mem cache") {
      val mockLog = mock[SiriusLog]

      val underTest = new CachedSiriusLog(mockLog, 1000)

      assert(underTest.writeCache.isEmpty)

      val event = OrderedEvent(1, 1, Delete("blah"))

      underTest.writeEntry(event)

      assert(underTest.writeCache.containsKey(1L))
    }

    it("should trim in-mem cache if entries exceeds maxCache") {
      val mockLog = mock[SiriusLog]

      val underTest = new CachedSiriusLog(mockLog, 2)

      assert(underTest.writeCache.isEmpty)

      val event1 = OrderedEvent(1, 1, Delete("blah"))
      val event2 = OrderedEvent(2, 1, Delete("blah"))
      val event3 = OrderedEvent(3, 1, Delete("blah"))

      underTest.writeEntry(event1)
      underTest.writeEntry(event2)

      assert(underTest.writeCache.size === 2)
      assert(underTest.writeCache.containsKey(1L))
      assert(underTest.writeCache.containsKey(2L))
      assert(underTest.firstSeq === 1)
      assert(underTest.lastSeq === 2)

      underTest.writeEntry(event3)

      assert(underTest.writeCache.size == 2)
      assert(!underTest.writeCache.containsKey(1L))
      assert(underTest.writeCache.containsKey(2L))
      assert(underTest.writeCache.containsKey(3L))
      assert(underTest.firstSeq === 2)
      assert(underTest.lastSeq === 3)
    }

    it("should return a range from the write cache if possible") {
      val mockLog = mock[SiriusLog]

      val underTest = new CachedSiriusLog(mockLog, 1000)

      // events in the write cache
      val events = Seq[OrderedEvent](
        OrderedEvent(1, 1, Delete("blah")), OrderedEvent(2, 1, Delete("blah")),
        OrderedEvent(3, 1, Delete("blah")), OrderedEvent(4, 1, Delete("blah"))
      )
      events.foreach(underTest.writeEntry(_))

      val foldedEvents = underTest.foldLeftRange(2, 3)(List[OrderedEvent]())((acc, event) => acc :+ event)
      assert(foldedEvents(0).sequence == 2L)
      assert(foldedEvents(1).sequence == 3L)
    }

    it("should attempt to return a range from the on-disk log if not in the write cache") {
      val mockLog = mock[SiriusLog]

      val underTest = new CachedSiriusLog(mockLog, 2)

      // events in the write cache; only 3 and 4 will remain (MAX_CACHE_SIZE == 2)
      val events = Seq[OrderedEvent](
        OrderedEvent(1, 1, Delete("blah")), OrderedEvent(2, 1, Delete("blah")),
        OrderedEvent(3, 1, Delete("blah")), OrderedEvent(4, 1, Delete("blah"))
      )
      events.foreach(underTest.writeEntry(_))

      val foldFun = (unit: Unit, event: OrderedEvent) => unit
      underTest.foldLeftRange(2, 3)(())(foldFun)

      // verify it is pushed down to backend log
      verify(mockLog).foldLeftRange(2, 3)(())(foldFun)
    }
  }
}