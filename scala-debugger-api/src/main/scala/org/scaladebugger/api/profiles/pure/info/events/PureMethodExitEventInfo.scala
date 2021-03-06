package org.scaladebugger.api.profiles.pure.info.events

import com.sun.jdi._
import com.sun.jdi.event.MethodExitEvent
import org.scaladebugger.api.lowlevel.JDIArgument
import org.scaladebugger.api.profiles.traits.info.events.MethodExitEventInfo
import org.scaladebugger.api.profiles.traits.info.{InfoProducer, MethodInfo, ValueInfo}
import org.scaladebugger.api.virtualmachines.ScalaVirtualMachine

/**
 * Represents a pure implementation of a method exit event info
 * profile that adds no custom logic on top of the standard JDI.
 *
 * @param scalaVirtualMachine The high-level virtual machine containing the
 *                            array
 * @param infoProducer The producer of info-based profile instances
 * @param methodExitEvent The method exit event to wrap in the profile
 * @param jdiArguments The request and event arguments tied to the provided
 *                     event
 * @param _method The method being exited
 * @param _returnValue The value being returned by the method
 * @param _virtualMachine The low-level virtual machine where the event
 *                        originated
 * @param _thread The thread where the event originated
 * @param _threadReferenceType The reference type of the thread where the
 *                             event originated
 * @param _location The location of the event occurrence
 */
class PureMethodExitEventInfo(
  override val scalaVirtualMachine: ScalaVirtualMachine,
  override protected val infoProducer: InfoProducer,
  private val methodExitEvent: MethodExitEvent,
  private val jdiArguments: Seq[JDIArgument] = Nil
)(
  _method: => Method,
  _returnValue: => Value,
  _virtualMachine: => VirtualMachine,
  _thread: => ThreadReference,
  _threadReferenceType: => ReferenceType,
  _location: => Location
) extends PureLocatableEventInfo(
  scalaVirtualMachine = scalaVirtualMachine,
  infoProducer = infoProducer,
  locatableEvent = methodExitEvent,
  jdiArguments = jdiArguments
)(
  _virtualMachine = _virtualMachine,
  _thread = _thread,
  _threadReferenceType = _threadReferenceType,
  _location = _location
) with MethodExitEventInfo {
  /**
   * Returns whether or not this info profile represents the low-level Java
   * implementation.
   *
   * @return If true, this profile represents the low-level Java information,
   *         otherwise this profile represents something higher-level like
   *         Scala, Jython, or JRuby
   */
  override def isJavaInfo: Boolean = true

  /**
   * Converts the current profile instance to a representation of
   * low-level Java instead of a higher-level abstraction.
   *
   * @return The profile instance providing an implementation corresponding
   *         to Java
   */
  override def toJavaInfo: MethodExitEventInfo = {
    val jep = infoProducer.eventProducer.toJavaInfo
    jep.newMethodExitEventInfo(
      scalaVirtualMachine = scalaVirtualMachine,
      methodExitEvent = methodExitEvent,
      jdiArguments = jdiArguments
    )(
      method = _method,
      returnValue = _returnValue,
      virtualMachine = _virtualMachine,
      thread = _thread,
      threadReferenceType = _threadReferenceType,
      location = _location
    )
  }

  /**
   * Returns the JDI representation this profile instance wraps.
   *
   * @return The JDI instance
   */
  override def toJdiInstance: MethodExitEvent = methodExitEvent

  /**
   * Returns the method that was exited.
   *
   * @return The information profile about the method
   */
  override def method: MethodInfo = infoProducer.newMethodInfo(
    scalaVirtualMachine = scalaVirtualMachine,
    method = _method
  )

  /**
   * Returns the value that the method will return.
   *
   * @return The information profile about the value
   */
  override def returnValue: ValueInfo = infoProducer.newValueInfo(
    scalaVirtualMachine = scalaVirtualMachine,
    value = _returnValue
  )
}
