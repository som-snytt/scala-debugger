package org.scaladebugger.api.lowlevel.requests.filters

import org.scaladebugger.api.lowlevel.requests.JDIRequestProcessor
import org.scaladebugger.api.lowlevel.requests.filters.processors.ClassExclusionFilterProcessor

/**
 * Represents a filter used to limit requests to any class not specified
 * by this filter. Requests are checked by verifying the class containing the
 * current method being invoked.
 *
 * @note Only used by AccessWatchpointRequest, ClassPrepareRequest,
 *       ClassUnloadRequest, ExceptionRequest, MethodEntryRequest,
 *       MethodExitRequest, ModificationWatchpointRequest,
 *       MonitorContendedEnteredRequest, MonitorContendedEnterRequest,
 *       MonitorWaitedRequest, MonitorWaitRequest, and StepRequest.
 *
 * @param classPattern Classes whose names do not match this pattern will be
 *                     excluded, can only take normal characters and wildcard
 *                     "*", meaning "*.Foo" or "java.*"
 */
case class ClassExclusionFilter(classPattern: String) extends JDIRequestFilter {
  /**
   * Creates a new JDI request processor based on this filter.
   *
   * @return The new JDI request processor instance
   */
  override def toProcessor: JDIRequestFilterProcessor =
    new ClassExclusionFilterProcessor(this)
}
