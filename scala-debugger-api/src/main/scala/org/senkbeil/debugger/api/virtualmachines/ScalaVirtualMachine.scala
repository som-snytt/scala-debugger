package org.senkbeil.debugger.api.virtualmachines

import org.senkbeil.debugger.api.lowlevel.ManagerContainer
import org.senkbeil.debugger.api.lowlevel.events.EventType
import org.senkbeil.debugger.api.lowlevel.utils.JDIHelperMethods
import org.senkbeil.debugger.api.profiles.pure.PureDebugProfile
import org.senkbeil.debugger.api.profiles.{ProfileManager, SwappableDebugProfile}
import org.senkbeil.debugger.api.utils.{LoopingTaskRunner, Logging}
import org.senkbeil.debugger.api.lowlevel.wrappers.Implicits
import Implicits._
import com.sun.jdi._
import com.sun.jdi.event.ClassPrepareEvent

/**
 * Represents a virtual machine running Scala code.
 *
 * @param _virtualMachine The underlying virtual machine
 * @param uniqueId A unique id assigned to the Scala virtual machine on the
 *                 client (library) side to help distinguish multiple VMs
 */
class ScalaVirtualMachine(
  protected val _virtualMachine: VirtualMachine,
  protected val profileManager: ProfileManager,
  val uniqueId: String = java.util.UUID.randomUUID().toString
) extends JDIHelperMethods with SwappableDebugProfile with Logging {

  /** Builds a string with the identifier of this virtual machine. */
  private def vmString(message: String) = s"(Scala VM $uniqueId) $message"

  logger.debug(vmString("Initializing Scala virtual machine!"))

  /**
   * Initializes extra events that this virtual machine should receive.
   *
   * @note Override this method to alter the extra events received!
   */
  protected def initializeEvents(): Unit = {
    _virtualMachine.enableClassPrepareEvents()
    _virtualMachine.enableThreadStartEvents()
    _virtualMachine.enableThreadDeathEvents()
  }
  initializeEvents()

  /**
   * Creates a new instance of a manager container with newly-initialized
   * managers.
   *
   * @param loopingTaskRunner The looping task runner to provide to various
   *                          managers
   *
   * @return The new container of managers
   */
  protected def newManagerContainer(
    loopingTaskRunner: LoopingTaskRunner = new LoopingTaskRunner()
  ): ManagerContainer = ManagerContainer.fromVirtualMachine(
    virtualMachine = _virtualMachine,
    loopingTaskRunner = loopingTaskRunner
  )

  /** Represents the collection of low-level APIs for the virtual machine. */
  lazy val lowlevel = newManagerContainer()

  // Register our standard profiles
  profileManager.register(
    PureDebugProfile.Name,
    new PureDebugProfile(_virtualMachine, lowlevel)
  )

  // Mark our default profile
  this.use(PureDebugProfile.Name)

  /* Add custom event handlers */ {
    import EventType._

    logger.debug(vmString("Adding custom event handlers!"))

    // Mark start event to load all of our system classes
    this.withProfile(PureDebugProfile.Name).onVMStart().foreach(_ => {
      logger.trace(vmString("Refreshing all class references!"))
      lowlevel.classManager.refreshAllClasses()

      logger.trace(vmString("Applying any pending breakpoints for references!"))
      lowlevel.classManager.allFileNames
        .foreach(lowlevel.breakpointManager.processPendingBreakpoints)
    })

    // Mark class prepare events to signal refreshing our classes
    lowlevel.eventManager.addResumingEventHandler(ClassPrepareEventType, e => {
      val classPrepareEvent = e.asInstanceOf[ClassPrepareEvent]
      val referenceType = classPrepareEvent.referenceType()
      val referenceTypeName = referenceType.name()
      val fileName =
        lowlevel.classManager.fileNameForReferenceType(referenceType)

      logger.trace(vmString(s"Received new class: $referenceTypeName"))
      lowlevel.classManager.refreshClass(referenceType)

      logger.trace(vmString(
        s"Processing any pending breakpoints for $referenceTypeName!"))
      lowlevel.breakpointManager.processPendingBreakpoints(fileName)
    })
  }

  /**
   * Represents the underlying virtual machine represented by this Scala
   * virtual machine.
   *
   * @return The JDI VirtualMachine instance
   */
  val underlyingVirtualMachine: VirtualMachine = _virtualMachine
}

