package com.senkbeil.debugger

import com.senkbeil.utils.LogLike
import com.sun.jdi._
import com.sun.jdi.connect.LaunchingConnector

import scala.collection.JavaConverters._

/**
 * Represents a debugger that starts a new process on the same machine.
 *
 * @param className The name of the class to use as the entrypoint for the new
 *                  process
 * @param commandLineArguments The command line arguments to provide to the new
 *                             process
 * @param jvmOptions The options to provide to the new process' JVM
 * @param suspend If true, suspends the JVM until it connects to the debugger
 */
class LaunchingDebugger(
  private val className: String,
  private val commandLineArguments: Seq[String] = Nil,
  private val jvmOptions: Seq[String] = Nil,
  private val suspend: Boolean = true
) extends Debugger with LogLike {
  private val ConnectorClassString = "com.sun.jdi.CommandLineLaunch"
  private val virtualMachineManager = Bootstrap.virtualMachineManager()
  @volatile private var virtualMachine: Option[VirtualMachine] = None

  /**
   * Starts the debugger, resulting in launching a new process to connect to.
   *
   * @param newVirtualMachineFunc The function to be invoked once the process
   *                              has been launched
   * @tparam T The return type of the callback function
   */
  def start[T](newVirtualMachineFunc: VirtualMachine => T): Unit = {
    require(virtualMachine.isEmpty, "Debugger already started!")
    assertJdiLoaded()

    // Retrieve the launching connector, or throw an exception if failed
    val connector = findLaunchingConnector.getOrElse(
      throw new AssertionError("Unable to retrieve connector!"))

    val arguments = connector.defaultArguments()

    arguments.get("main")
      .setValue((className +: commandLineArguments).mkString(" "))
    arguments.get("options").setValue(jvmOptions.mkString(" "))
    arguments.get("suspend").setValue(suspend.toString)

    logger.info("Launching process: " +
      (className :+ commandLineArguments).mkString(" "))
    virtualMachine = Some(connector.launch(arguments))
    newVirtualMachineFunc(virtualMachine.get)
  }

  /**
   * Stops the process launched by the debugger.
   */
  def stop(): Unit = {
    require(virtualMachine.nonEmpty, "Debugger has not been started!")

    // Kill the process associated with the local virtual machine
    logger.info("Shutting down process: " +
      (className :+ commandLineArguments).mkString(" "))
    virtualMachine.get.process().destroy()

    // Wipe our reference to the old virtual machine
    virtualMachine = None
  }

  /**
   * Retrieves the connector to be used to launch a new process and connect
   * to it.
   *
   * @return Some connector if available, otherwise None
   */
  private def findLaunchingConnector: Option[LaunchingConnector] = {
    virtualMachineManager.launchingConnectors().asScala
      .find(_.name() == ConnectorClassString)
  }
}