package org.apache.spark.internal

import java.lang.System

trait Logging
{
  // Log methods that take only a String
  protected def logInfo(msg: => String): Unit = {
    System.err.println(msg)
  }

  protected def logDebug(msg: => String): Unit = {
  }

  protected def logTrace(msg: => String): Unit = {
  }

  protected def logWarning(msg: => String): Unit = {
    System.err.println(msg)
  }

  protected def logError(msg: => String): Unit = {
    System.err.println(msg)
  }

  // Log methods that take Throwables (Exceptions/Errors) too
  protected def logInfo(msg: => String, throwable: Throwable): Unit = {
    logInfo { msg + "\n" + throwable.toString }
  }

  protected def logDebug(msg: => String, throwable: Throwable): Unit = {
    logDebug { msg + "\n" + throwable.toString }
  }

  protected def logTrace(msg: => String, throwable: Throwable): Unit = {
    logTrace { msg + "\n" + throwable.toString }
  }

  protected def logWarning(msg: => String, throwable: Throwable): Unit = {
    logWarning { msg + "\n" + throwable.toString }
  }

  protected def logError(msg: => String, throwable: Throwable): Unit = {
    logError { msg + "\n" + throwable.toString }
  }

  protected def isTraceEnabled(): Boolean = {
    false
  }
}