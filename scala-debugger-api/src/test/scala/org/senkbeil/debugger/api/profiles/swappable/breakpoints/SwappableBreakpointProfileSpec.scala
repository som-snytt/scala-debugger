package org.senkbeil.debugger.api.profiles.swappable.breakpoints

import org.scalamock.scalatest.MockFactory
import org.scalatest.{OneInstancePerTest, Matchers, FunSpec}
import org.senkbeil.debugger.api.lowlevel.JDIArgument
import org.senkbeil.debugger.api.profiles.ProfileManager
import org.senkbeil.debugger.api.profiles.swappable.SwappableDebugProfile
import org.senkbeil.debugger.api.profiles.traits.DebugProfile

class SwappableBreakpointProfileSpec extends FunSpec with Matchers
  with OneInstancePerTest with MockFactory
{
  private val mockDebugProfile = mock[DebugProfile]
  private val mockProfileManager = mock[ProfileManager]

  private val swappableDebugProfile = new Object with SwappableDebugProfile {
    override protected val profileManager: ProfileManager = mockProfileManager
  }

  describe("SwappableBreakpointProfile") {
    describe("#onBreakpointWithData") {
      it("should invoke the method on the underlying profile") {
        val fileName = "some file"
        val lineNumber = 999
        val arguments = Seq(mock[JDIArgument])

        (mockProfileManager.retrieve _).expects(*)
          .returning(Some(mockDebugProfile)).once()

        (mockDebugProfile.onBreakpointWithData _).expects(
          fileName,
          lineNumber,
          arguments
        ).once()

        swappableDebugProfile.onBreakpointWithData(
          fileName,
          lineNumber,
          arguments: _*
        )
      }

      it("should throw an exception if there is no underlying profile") {
        val fileName = "some file"
        val lineNumber = 999
        val arguments = Seq(mock[JDIArgument])

        (mockProfileManager.retrieve _).expects(*).returning(None).once()

        intercept[AssertionError] {
          swappableDebugProfile.onBreakpointWithData(
            fileName,
            lineNumber,
            arguments: _*
          )
        }
      }
    }
  }
}