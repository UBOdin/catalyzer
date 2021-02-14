/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.util

import java.{lang => jl}
import java.io.ObjectInputStream
import java.util.{ArrayList, Collections}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import org.apache.spark.internal.Logging

private[spark] case class AccumulatorMetadata(
    id: Long,
    name: Option[String],
    countFailedValues: Boolean) extends Serializable


/**
 * The base class for accumulators, that can accumulate inputs of type `IN`, and produce output of
 * type `OUT`.
 *
 * `OUT` should be a type that can be read atomically (e.g., Int, Long), or thread-safely
 * (e.g., synchronized collections) because it will be read from other threads.
 */
abstract class AccumulatorV2[IN, OUT] extends Serializable {
  private[spark] var metadata: AccumulatorMetadata = _
  private[this] var atDriverSide = true

  /**
   * Returns true if this accumulator has been registered.
   *
   * @note All accumulators must be registered before use, or it will throw exception.
   */
  final def isRegistered: Boolean =
    metadata != null && AccumulatorContext.get(metadata.id).isDefined

  private def assertMetadataNotNull(): Unit = {
    if (metadata == null) {
      throw new IllegalStateException("The metadata of this accumulator has not been assigned yet.")
    }
  }

  /**
   * Returns the id of this accumulator, can only be called after registration.
   */
  final def id: Long = {
    assertMetadataNotNull()
    metadata.id
  }

  /**
   * Returns the name of this accumulator, can only be called after registration.
   */
  final def name: Option[String] = {
    assertMetadataNotNull()

    if (atDriverSide) {
      metadata.name.orElse(AccumulatorContext.get(id).flatMap(_.metadata.name))
    } else {
      metadata.name
    }
  }

  /**
   * Whether to accumulate values from failed tasks. This is set to true for system and time
   * metrics like serialization time or bytes spilled, and false for things with absolute values
   * like number of input rows.  This should be used for internal metrics only.
   */
  private[spark] final def countFailedValues: Boolean = {
    assertMetadataNotNull()
    metadata.countFailedValues
  }

  final private[spark] def isAtDriverSide: Boolean = atDriverSide

  /**
   * Returns if this accumulator is zero value or not. e.g. for a counter accumulator, 0 is zero
   * value; for a list accumulator, Nil is zero value.
   */
  def isZero: Boolean

  /**
   * Creates a new copy of this accumulator, which is zero value. i.e. call `isZero` on the copy
   * must return true.
   */
  def copyAndReset(): AccumulatorV2[IN, OUT] = {
    val copyAcc = copy()
    copyAcc.reset()
    copyAcc
  }

  /**
   * Creates a new copy of this accumulator.
   */
  def copy(): AccumulatorV2[IN, OUT]

  /**
   * Resets this accumulator, which is zero value. i.e. call `isZero` must
   * return true.
   */
  def reset(): Unit

  /**
   * Takes the inputs and accumulates.
   */
  def add(v: IN): Unit

  /**
   * Merges another same-type accumulator into this one and update its state, i.e. this should be
   * merge-in-place.
   */
  def merge(other: AccumulatorV2[IN, OUT]): Unit

  /**
   * Defines the current value of this accumulator
   */
  def value: OUT

  override def toString: String = {
    // getClass.getSimpleName can cause Malformed class name error,
    // call safer `Utils.getSimpleName` instead
    if (metadata == null) {
      "Un-registered Accumulator: " + Utils.getSimpleName(getClass)
    } else {
      Utils.getSimpleName(getClass) + s"(id: $id, name: $name, value: $value)"
    }
  }
}


/**
 * An internal class used to track accumulators by Spark itself.
 */
private[spark] object AccumulatorContext extends Logging {

  /**
   * This global map holds the original accumulator objects that are created on the driver.
   * It keeps weak references to these objects so that accumulators can be garbage-collected
   * once the RDDs and user-code that reference them are cleaned up.
   * TODO: Don't use a global map; these should be tied to a SparkContext (SPARK-13051).
   */
  private val originals = new ConcurrentHashMap[Long, jl.ref.WeakReference[AccumulatorV2[_, _]]]

  private[this] val nextId = new AtomicLong(0L)

  /**
   * Returns a globally unique ID for a new [[AccumulatorV2]].
   * Note: Once you copy the [[AccumulatorV2]] the ID is no longer unique.
   */
  def newId(): Long = nextId.getAndIncrement

  /** Returns the number of accumulators registered. Used in testing. */
  def numAccums: Int = originals.size

  /**
   * Registers an [[AccumulatorV2]] created on the driver such that it can be used on the executors.
   *
   * All accumulators registered here can later be used as a container for accumulating partial
   * values across multiple tasks. This is what `org.apache.spark.scheduler.DAGScheduler` does.
   * Note: if an accumulator is registered here, it should also be registered with the active
   * context cleaner for cleanup so as to avoid memory leaks.
   *
   * If an [[AccumulatorV2]] with the same ID was already registered, this does nothing instead
   * of overwriting it. We will never register same accumulator twice, this is just a sanity check.
   */
  def register(a: AccumulatorV2[_, _]): Unit = {
    originals.putIfAbsent(a.id, new jl.ref.WeakReference[AccumulatorV2[_, _]](a))
  }

  /**
   * Unregisters the [[AccumulatorV2]] with the given ID, if any.
   */
  def remove(id: Long): Unit = {
    originals.remove(id)
  }

  /**
   * Returns the [[AccumulatorV2]] registered with the given ID, if any.
   */
  def get(id: Long): Option[AccumulatorV2[_, _]] = {
    val ref = originals.get(id)
    if (ref eq null) {
      None
    } else {
      // Since we are storing weak references, warn when the underlying data is not valid.
      val acc = ref.get
      if (acc eq null) {
        logWarning(s"Attempted to access garbage collected accumulator $id")
      }
      Option(acc)
    }
  }

  /**
   * Clears all registered [[AccumulatorV2]]s. For testing only.
   */
  def clear(): Unit = {
    originals.clear()
  }

  // Identifier for distinguishing SQL metrics from other accumulators
  private[spark] val SQL_ACCUM_IDENTIFIER = "sql"
}


/**
 * An [[AccumulatorV2 accumulator]] for computing sum, count, and average of 64-bit integers.
 *
 * @since 2.0.0
 */
class LongAccumulator extends AccumulatorV2[jl.Long, jl.Long] {
  private var _sum = 0L
  private var _count = 0L

  /**
   * Returns false if this accumulator has had any values added to it or the sum is non-zero.
   *
   * @since 2.0.0
   */
  override def isZero: Boolean = _sum == 0L && _count == 0

  override def copy(): LongAccumulator = {
    val newAcc = new LongAccumulator
    newAcc._count = this._count
    newAcc._sum = this._sum
    newAcc
  }

  override def reset(): Unit = {
    _sum = 0L
    _count = 0L
  }

  /**
   * Adds v to the accumulator, i.e. increment sum by v and count by 1.
   * @since 2.0.0
   */
  override def add(v: jl.Long): Unit = {
    _sum += v
    _count += 1
  }

  /**
   * Adds v to the accumulator, i.e. increment sum by v and count by 1.
   * @since 2.0.0
   */
  def add(v: Long): Unit = {
    _sum += v
    _count += 1
  }

  /**
   * Returns the number of elements added to the accumulator.
   * @since 2.0.0
   */
  def count: Long = _count

  /**
   * Returns the sum of elements added to the accumulator.
   * @since 2.0.0
   */
  def sum: Long = _sum

  /**
   * Returns the average of elements added to the accumulator.
   * @since 2.0.0
   */
  def avg: Double = _sum.toDouble / _count

  override def merge(other: AccumulatorV2[jl.Long, jl.Long]): Unit = other match {
    case o: LongAccumulator =>
      _sum += o.sum
      _count += o.count
    case _ =>
      throw new UnsupportedOperationException(
        s"Cannot merge ${this.getClass.getName} with ${other.getClass.getName}")
  }

  private[spark] def setValue(newValue: Long): Unit = _sum = newValue

  override def value: jl.Long = _sum
}


/**
 * An [[AccumulatorV2 accumulator]] for computing sum, count, and averages for double precision
 * floating numbers.
 *
 * @since 2.0.0
 */
class DoubleAccumulator extends AccumulatorV2[jl.Double, jl.Double] {
  private var _sum = 0.0
  private var _count = 0L

  /**
   * Returns false if this accumulator has had any values added to it or the sum is non-zero.
   */
  override def isZero: Boolean = _sum == 0.0 && _count == 0

  override def copy(): DoubleAccumulator = {
    val newAcc = new DoubleAccumulator
    newAcc._count = this._count
    newAcc._sum = this._sum
    newAcc
  }

  override def reset(): Unit = {
    _sum = 0.0
    _count = 0L
  }

  /**
   * Adds v to the accumulator, i.e. increment sum by v and count by 1.
   * @since 2.0.0
   */
  override def add(v: jl.Double): Unit = {
    _sum += v
    _count += 1
  }

  /**
   * Adds v to the accumulator, i.e. increment sum by v and count by 1.
   * @since 2.0.0
   */
  def add(v: Double): Unit = {
    _sum += v
    _count += 1
  }

  /**
   * Returns the number of elements added to the accumulator.
   * @since 2.0.0
   */
  def count: Long = _count

  /**
   * Returns the sum of elements added to the accumulator.
   * @since 2.0.0
   */
  def sum: Double = _sum

  /**
   * Returns the average of elements added to the accumulator.
   * @since 2.0.0
   */
  def avg: Double = _sum / _count

  override def merge(other: AccumulatorV2[jl.Double, jl.Double]): Unit = other match {
    case o: DoubleAccumulator =>
      _sum += o.sum
      _count += o.count
    case _ =>
      throw new UnsupportedOperationException(
        s"Cannot merge ${this.getClass.getName} with ${other.getClass.getName}")
  }

  private[spark] def setValue(newValue: Double): Unit = _sum = newValue

  override def value: jl.Double = _sum
}


/**
 * An [[AccumulatorV2 accumulator]] for collecting a list of elements.
 *
 * @since 2.0.0
 */
class CollectionAccumulator[T] extends AccumulatorV2[T, java.util.List[T]] {
  private val _list: java.util.List[T] = Collections.synchronizedList(new ArrayList[T]())

  /**
   * Returns false if this accumulator instance has any values in it.
   */
  override def isZero: Boolean = _list.isEmpty

  override def copyAndReset(): CollectionAccumulator[T] = new CollectionAccumulator

  override def copy(): CollectionAccumulator[T] = {
    val newAcc = new CollectionAccumulator[T]
    _list.synchronized {
      newAcc._list.addAll(_list)
    }
    newAcc
  }

  override def reset(): Unit = _list.clear()

  override def add(v: T): Unit = _list.add(v)

  override def merge(other: AccumulatorV2[T, java.util.List[T]]): Unit = other match {
    case o: CollectionAccumulator[T] => _list.addAll(o.value)
    case _ => throw new UnsupportedOperationException(
      s"Cannot merge ${this.getClass.getName} with ${other.getClass.getName}")
  }

  override def value: java.util.List[T] = _list.synchronized {
    java.util.Collections.unmodifiableList(new ArrayList[T](_list))
  }

  private[spark] def setValue(newValue: java.util.List[T]): Unit = {
    _list.clear()
    _list.addAll(newValue)
  }
}
