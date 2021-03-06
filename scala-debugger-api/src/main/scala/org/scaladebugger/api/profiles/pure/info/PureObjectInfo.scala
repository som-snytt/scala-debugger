package org.scaladebugger.api.profiles.pure.info

import com.sun.jdi._
import org.scaladebugger.api.lowlevel.{InvokeNonVirtualArgument, InvokeSingleThreadedArgument, JDIArgument}
import org.scaladebugger.api.profiles.traits.info._
import org.scaladebugger.api.virtualmachines.ScalaVirtualMachine

import scala.util.Try
import scala.collection.JavaConverters._

/**
 * Represents a pure implementation of an object profile that adds no custom
 * logic on top of the standard JDI.
 *
 * @param scalaVirtualMachine The high-level virtual machine containing the
 *                            object
 * @param infoProducer The producer of info-based profile instances
 * @param _objectReference The reference to the underlying JDI object
 * @param _virtualMachine The virtual machine associated with the object
 * @param _referenceType The reference type for this object
 */
class PureObjectInfo(
  override val scalaVirtualMachine: ScalaVirtualMachine,
  override protected val infoProducer: InfoProducer,
  private val _objectReference: ObjectReference
)(
  protected val _virtualMachine: VirtualMachine = _objectReference.virtualMachine(),
  private val _referenceType: ReferenceType = _objectReference.referenceType()
) extends PureValueInfo(
  scalaVirtualMachine = scalaVirtualMachine,
  infoProducer = infoProducer,
  _value = _objectReference
) with ObjectInfo {
  private lazy val typeChecker = newTypeCheckerProfile()

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
  override def toJavaInfo: ObjectInfo = {
    infoProducer.toJavaInfo.newObjectInfo(
      scalaVirtualMachine = scalaVirtualMachine,
      objectReference = _objectReference
    )(
      virtualMachine = _virtualMachine,
      referenceType = _referenceType
    )
  }

  /**
   * Returns the JDI representation this profile instance wraps.
   *
   * @return The JDI instance
   */
  override def toJdiInstance: ObjectReference = _objectReference

  /**
   * Returns the type information for the object.
   *
   * @return The profile containing type information
   */
  override def `type`: ReferenceTypeInfo =
    super.`type`.toReferenceType

  /**
   * Represents the unique id of this object.
   *
   * @return The unique id as a long
   */
  override def uniqueId: Long = _objectReference.uniqueID()

  /**
   * Returns the reference type information for this object.
   *
   * @note Returns the specific type of this object, not any interface or
   *       superclass that it inherits. So, val x: AnyRef = "a string" would
   *       yield the reference type for String, not AnyRef.
   * @return The reference type information
   */
  override def referenceType: ReferenceTypeInfo =
    newTypeProfile(_objectReference.referenceType()).toReferenceType

  /**
   * Invokes the object's method.
   *
   * @param thread The thread within which to invoke the method
   * @param method The method of the object to invoke
   * @param arguments         The arguments to provide to the method
   * @param jdiArguments      Optional arguments to provide custom settings to the
   *                          method invocation
   * @return The resulting value of the invocation
   */
  override def invoke(
    thread: ThreadInfo,
    method: MethodInfo,
    arguments: Seq[Any],
    jdiArguments: JDIArgument*
  ): ValueInfo = {
    val t = thread.toJdiInstance
    val m = method.toJdiInstance

    import org.scaladebugger.api.lowlevel.wrappers.Implicits._
    val v = arguments.map(_virtualMachine.mirrorOf(_: Any))

    val o = jdiArguments.map {
      case InvokeSingleThreadedArgument => ObjectReference.INVOKE_SINGLE_THREADED
      case InvokeNonVirtualArgument     => ObjectReference.INVOKE_NONVIRTUAL
    }.fold(0)(_ | _)

    val r = _objectReference.invokeMethod(t, m, v.asJava, o)
    newValueProfile(r)
  }

  /**
   * Returns all visible methods contained in this object.
   *
   * @return The profiles wrapping the visible methods in this object
   */
  override def methods: Seq[MethodInfo] = {
    _referenceType.visibleMethods().asScala.map(newMethodProfile)
  }

  /**
   * Returns the object's method with the specified name.
   *
   * @param name               The name of the method
   * @param parameterTypeNames The fully-qualified type names of the parameters
   *                           of the method to find
   * @return Some profile wrapping the method, otherwise None if doesn't exist
   */
  override def methodOption(
    name: String,
    parameterTypeNames: String*
  ): Option[MethodInfo] = {
    _referenceType.methodsByName(name).asScala.find(
      _.argumentTypeNames().asScala.zip(parameterTypeNames)
        .forall(s => typeChecker.equalTypeNames(s._1, s._2))
    ).map(newMethodProfile)
  }

  /**
   * Returns all visible fields contained in this object.
   *
   * @note Provides no offset index information!
   * @return The profiles wrapping the visible fields in this object
   */
  override def fields: Seq[FieldVariableInfo] = {
    _referenceType.visibleFields().asScala.map(newFieldProfile)
  }

  /**
   * Returns all visible fields contained in this object with offset index.
   *
   * @return The profiles wrapping the visible fields in this object
   */
  override def indexedFields: Seq[FieldVariableInfo] = {
    _referenceType.visibleFields().asScala.zipWithIndex.map { case (f, i) =>
      newFieldProfile(f, i)
    }
  }

  /**
   * Returns the object's field with the specified name.
   *
   * @param name The name of the field
   * @return Some profile wrapping the field, or None if doesn't exist
   */
  override def fieldOption(name: String): Option[FieldVariableInfo] = {
    Option(_referenceType.fieldByName(name)).map(newFieldProfile)
  }

  /**
   * Returns the object's field with the specified name with offset index
   * information.
   *
   * @param name The name of the field
   * @return Some profile wrapping the field, or None if doesn't exist
   */
  override def indexedFieldOption(
    name: String
  ): Option[FieldVariableInfo] = {
    indexedFields.reverse.find(_.name == name)
  }

  protected def newFieldProfile(field: Field): FieldVariableInfo =
    newFieldProfile(field, -1)

  protected def newFieldProfile(
    field: Field,
    offsetIndex: Int
  ): FieldVariableInfo = infoProducer.newFieldInfo(
    scalaVirtualMachine,
    Left(_objectReference),
    field,
    offsetIndex
  )()

  protected def newMethodProfile(method: Method): MethodInfo =
    infoProducer.newMethodInfo(scalaVirtualMachine, method)

  protected def newValueProfile(value: Value): ValueInfo =
    infoProducer.newValueInfo(scalaVirtualMachine, value)

  protected def newTypeCheckerProfile(): TypeChecker =
    infoProducer.newTypeChecker()
}
