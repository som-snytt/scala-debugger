package org.senkbeil.debugger.api.profiles.pure.threads

import com.sun.jdi.event.{Event, EventQueue}
import com.sun.jdi.request.EventRequestManager
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FunSpec, Matchers, OneInstancePerTest}
import org.senkbeil.debugger.api.lowlevel.events.EventManager
import org.senkbeil.debugger.api.lowlevel.events.data.JDIEventDataResult
import org.senkbeil.debugger.api.lowlevel.events.filters.UniqueIdPropertyFilter
import org.senkbeil.debugger.api.lowlevel.requests.JDIRequestArgument
import org.senkbeil.debugger.api.lowlevel.requests.properties.UniqueIdProperty
import org.senkbeil.debugger.api.lowlevel.threads.ThreadStartManager
import org.senkbeil.debugger.api.pipelines.Pipeline
import org.senkbeil.debugger.api.utils.LoopingTaskRunner
import test.JDIMockHelpers
import org.senkbeil.debugger.api.lowlevel.events.EventType.ThreadStartEventType

import scala.util.{Failure, Success}

class PureThreadStartProfileSpec extends FunSpec with Matchers
with OneInstancePerTest with MockFactory with JDIMockHelpers
{
  private val TestRequestId = java.util.UUID.randomUUID().toString

  // Workaround - see https://github.com/paulbutcher/ScalaMock/issues/33
  private class ZeroArgThreadStartManager extends ThreadStartManager(
    stub[EventRequestManager]
  )
  private val mockThreadStartManager = mock[ZeroArgThreadStartManager]

  // Workaround - see https://github.com/paulbutcher/ScalaMock/issues/33
  private class ZeroArgEventManager extends EventManager(
    stub[EventQueue],
    stub[LoopingTaskRunner],
    autoStart = false
  )
  private val mockEventManager = mock[ZeroArgEventManager]

  private val pureThreadStartProfile = new Object with PureThreadStartProfile {
    private var requestId: String = _
    def setRequestId(requestId: String): Unit = this.requestId = requestId

    // NOTE: If we set a specific request id, return that, otherwise use the
    //       default behavior
    override protected def newThreadStartRequestId(): String =
      if (requestId != null) requestId else super.newThreadStartRequestId()

    override protected val threadStartManager = mockThreadStartManager
    override protected val eventManager: EventManager = mockEventManager
  }

  describe("PureThreadStartProfile") {
    describe("#onThreadStartWithData") {
      it("should create a new request if one has not be made yet") {
        val arguments = Seq(mock[JDIRequestArgument])

        val uniqueIdProperty = UniqueIdProperty(id = TestRequestId)
        val uniqueIdPropertyFilter = UniqueIdPropertyFilter(id = TestRequestId)

        // Set a known test id so we can validate the unique property is added
        import scala.language.reflectiveCalls
        pureThreadStartProfile.setRequestId(TestRequestId)

        inSequence {
          // Memoized request function first checks to make sure the cache
          // has not been invalidated underneath (first call will always be
          // empty since we have never created the request)
          (mockThreadStartManager.threadStartRequestList _)
            .expects()
            .returning(Nil).once()

          // NOTE: Expect the request to be created with a unique id
          (mockThreadStartManager.createThreadStartRequest _)
            .expects(uniqueIdProperty +: arguments)
            .returning(Success(TestRequestId)).once()

          (mockEventManager.addEventDataStream _)
            .expects(ThreadStartEventType, Seq(uniqueIdPropertyFilter))
            .returning(Pipeline.newPipeline(
              classOf[(Event, Seq[JDIEventDataResult])]
            )).once()
        }

        pureThreadStartProfile.onThreadStartWithData(
          arguments: _*
        )
      }

      it("should capture exceptions thrown when creating the request") {
        val expected = Failure(new Throwable)
        val arguments = Seq(mock[JDIRequestArgument])

        val uniqueIdProperty = UniqueIdProperty(id = TestRequestId)

        // Set a known test id so we can validate the unique property is added
        import scala.language.reflectiveCalls
        pureThreadStartProfile.setRequestId(TestRequestId)

        inSequence {
          // Memoized request function first checks to make sure the cache
          // has not been invalidated underneath (first call will always be
          // empty since we have never created the request)
          (mockThreadStartManager.threadStartRequestList _)
            .expects()
            .returning(Nil).once()

          // NOTE: Expect the request to be created with a unique id
          (mockThreadStartManager.createThreadStartRequest _)
            .expects(uniqueIdProperty +: arguments)
            .throwing(expected.failed.get).once()
        }

        val actual = pureThreadStartProfile.onThreadStartWithData(
          arguments: _*
        )

        actual should be (expected)
      }

      it("should create a new request if the previous one was removed") {
        val arguments = Seq(mock[JDIRequestArgument])

        // Set a known test id so we can validate the unique property is added
        import scala.language.reflectiveCalls
        pureThreadStartProfile.setRequestId(TestRequestId)

        inSequence {
          // Set a known test id so we can validate the unique property is added
          import scala.language.reflectiveCalls
          pureThreadStartProfile.setRequestId(TestRequestId)

          val uniqueIdProperty = UniqueIdProperty(id = TestRequestId)
          val uniqueIdPropertyFilter =
            UniqueIdPropertyFilter(id = TestRequestId)

          // Memoized request function first checks to make sure the cache
          // has not been invalidated underneath (first call will always be
          // empty since we have never created the request)
          (mockThreadStartManager.threadStartRequestList _)
            .expects()
            .returning(Nil).once()

          // NOTE: Expect the request to be created with a unique id
          (mockThreadStartManager.createThreadStartRequest _)
            .expects(uniqueIdProperty +: arguments)
            .returning(Success(TestRequestId)).once()

          (mockEventManager.addEventDataStream _)
            .expects(ThreadStartEventType, Seq(uniqueIdPropertyFilter))
            .returning(Pipeline.newPipeline(
              classOf[(Event, Seq[JDIEventDataResult])]
            )).once()
        }

        pureThreadStartProfile.onThreadStartWithData(
          arguments: _*
        )

        inSequence {
          // Set a known test id so we can validate the unique property is added
          import scala.language.reflectiveCalls
          pureThreadStartProfile.setRequestId(TestRequestId + "other")

          val uniqueIdProperty = UniqueIdProperty(id = TestRequestId + "other")
          val uniqueIdPropertyFilter =
            UniqueIdPropertyFilter(id = TestRequestId + "other")

          // Return empty this time to indicate that the thread start request
          // was removed some time between the two calls
          (mockThreadStartManager.threadStartRequestList _)
            .expects()
            .returning(Nil).once()

          // NOTE: Expect the request to be created with a unique id
          (mockThreadStartManager.createThreadStartRequest _)
            .expects(uniqueIdProperty +: arguments)
            .returning(Success(TestRequestId + "other")).once()

          (mockEventManager.addEventDataStream _)
            .expects(ThreadStartEventType, Seq(uniqueIdPropertyFilter))
            .returning(Pipeline.newPipeline(
              classOf[(Event, Seq[JDIEventDataResult])]
            )).once()
        }

        pureThreadStartProfile.onThreadStartWithData(
          arguments: _*
        )
      }

      it("should not create a new request if the previous one still exists") {
        val arguments = Seq(mock[JDIRequestArgument])

        inSequence {
          // Set a known test id so we can validate the unique property is added
          import scala.language.reflectiveCalls
          pureThreadStartProfile.setRequestId(TestRequestId)

          val uniqueIdProperty = UniqueIdProperty(id = TestRequestId)
          val uniqueIdPropertyFilter =
            UniqueIdPropertyFilter(id = TestRequestId)

          // Memoized request function first checks to make sure the cache
          // has not been invalidated underneath (first call will always be
          // empty since we have never created the request)
          (mockThreadStartManager.threadStartRequestList _)
            .expects()
            .returning(Nil).once()

          // NOTE: Expect the request to be created with a unique id
          (mockThreadStartManager.createThreadStartRequest _)
            .expects(uniqueIdProperty +: arguments)
            .returning(Success(TestRequestId)).once()

          (mockEventManager.addEventDataStream _)
            .expects(ThreadStartEventType, Seq(uniqueIdPropertyFilter))
            .returning(Pipeline.newPipeline(
              classOf[(Event, Seq[JDIEventDataResult])]
            )).once()
        }

        pureThreadStartProfile.onThreadStartWithData(
          arguments: _*
        )

        inSequence {
          // Set a known test id so we can validate the unique property is added
          import scala.language.reflectiveCalls
          pureThreadStartProfile.setRequestId(TestRequestId + "other")

          val uniqueIdPropertyFilter =
            UniqueIdPropertyFilter(id = TestRequestId)

          // Return collection of matching arguments to indicate that we do
          // still have the request
          val internalId = java.util.UUID.randomUUID().toString
          (mockThreadStartManager.threadStartRequestList _)
            .expects()
            .returning(Seq(internalId)).once()
          (mockThreadStartManager.getThreadStartRequestArguments _)
            .expects(internalId)
            .returning(Some(arguments)).once()

          (mockEventManager.addEventDataStream _)
            .expects(ThreadStartEventType, Seq(uniqueIdPropertyFilter))
            .returning(Pipeline.newPipeline(
              classOf[(Event, Seq[JDIEventDataResult])]
            )).once()
        }

        pureThreadStartProfile.onThreadStartWithData(
          arguments: _*
        )
      }
    }
  }
}
