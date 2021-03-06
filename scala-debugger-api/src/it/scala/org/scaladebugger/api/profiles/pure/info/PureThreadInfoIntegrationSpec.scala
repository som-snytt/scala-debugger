package org.scaladebugger.api.profiles.pure.info

import org.scaladebugger.api.lowlevel.events.misc.NoResume
import org.scaladebugger.api.profiles.pure.PureDebugProfile
import org.scaladebugger.api.profiles.traits.info.{IndexedVariableInfo, ThreadInfo}
import org.scaladebugger.api.utils.JDITools
import org.scaladebugger.api.virtualmachines.DummyScalaVirtualMachine
import org.scaladebugger.test.helpers.ParallelMockFunSpec
import test.{ApiTestUtilities, VirtualMachineFixtures}

class PureThreadInfoIntegrationSpec extends ParallelMockFunSpec
  with VirtualMachineFixtures
  with ApiTestUtilities
{
  describe("PureThreadInfo") {
    it("should be able to get a list of frames for the suspended thread") {
      val testClass = "org.scaladebugger.test.info.Frames"
      val testFile = JDITools.scalaClassStringToFileString(testClass)

      @volatile var t: Option[ThreadInfo] = None
      val s = DummyScalaVirtualMachine.newInstance()

      // NOTE: Do not resume so we can check the stack frames
      s.withProfile(PureDebugProfile.Name)
        .getOrCreateBreakpointRequest(testFile, 25, NoResume)
        .foreach(e => t = Some(e.thread))

      withVirtualMachine(testClass, pendingScalaVirtualMachines = Seq(s)) { (s) =>
        logTimeTaken(eventually {
          val thread = t.get

          // Should be 5
          val totalFrames = thread.totalFrames

          val index = 1
          val length = 2

          // Valid list of frames
          thread.frames.slice(index, length + 1).map(_.toPrettyString) should
            be (thread.frames(index, length).map(_.toPrettyString))

          // Length too long when retrieving should revert to all frames
          thread.frames.slice(index, totalFrames).map(_.toPrettyString) should
            be (thread.frames(index, totalFrames + 1).map(_.toPrettyString))

          // Length of -1 should return all remaining frames
          thread.frames.slice(index, totalFrames).map(_.toPrettyString) should
            be (thread.frames(index, -1).map(_.toPrettyString))
        })
      }
    }

    it("should be able to find a variable by its name") {
      val testClass = "org.scaladebugger.test.info.Variables"
      val testFile = JDITools.scalaClassStringToFileString(testClass)

      @volatile var t: Option[ThreadInfo] = None
      val s = DummyScalaVirtualMachine.newInstance()

      // NOTE: Do not resume so we can check the variables at the stack frame
      s.withProfile(PureDebugProfile.Name)
        .getOrCreateBreakpointRequest(testFile, 32, NoResume)
        .foreach(e => t = Some(e.thread))

      withVirtualMachine(testClass, pendingScalaVirtualMachines = Seq(s)) { (s) =>
        logTimeTaken(eventually {
          val thread = t.get

          // Should support retrieving local variables
          val localVariable = thread.findVariableByName("a").get
          localVariable.isLocal should be (true)
          localVariable.name should be ("a")

          // Should support retrieving fields
          val field = thread.findVariableByName("z1").get
          field.isField should be (true)
          field.name should be ("z1")
        })
      }
    }

    it("should be able to find a variable by its index") {
      val testClass = "org.scaladebugger.test.info.Variables"
      val testFile = JDITools.scalaClassStringToFileString(testClass)

      @volatile var t: Option[ThreadInfo] = None
      val s = DummyScalaVirtualMachine.newInstance()

      // NOTE: Do not resume so we can check the variables at the stack frame
      s.withProfile(PureDebugProfile.Name)
        .getOrCreateBreakpointRequest(testFile, 32, NoResume)
        .foreach(e => t = Some(e.thread))

      withVirtualMachine(testClass, pendingScalaVirtualMachines = Seq(s)) { (s) =>
        logTimeTaken(eventually {
          val thread = t.get

          // Local variables are always indexed
          val localVariable = thread.findVariableByName("a").get
            .asInstanceOf[IndexedVariableInfo]

          val indexedVariable = thread.findVariableByIndex(
            localVariable.frameIndex,
            localVariable.offsetIndex
          ).get

          // Loaded variable should be the same
          indexedVariable.name should be (localVariable.name)
        })
      }
    }
  }
}
