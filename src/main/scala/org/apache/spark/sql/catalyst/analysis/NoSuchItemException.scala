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

package org.apache.spark.sql.catalyst.analysis

import org.apache.spark.sql.AnalysisException
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._
import org.apache.spark.sql.connector.catalog.Identifier
import org.apache.spark.sql.types.StructType


/**
 * Thrown by a catalog when an item cannot be found. The analyzer will rethrow the exception
 * as an [[org.apache.spark.sql.AnalysisException]] with the correct position information.
 */
class NoSuchDatabaseException(
    val db: String) extends NoSuchNamespaceException(s"Database '$db' not found")

class NoSuchNamespaceException(message: String, cause: Option[Throwable] = None)
  extends AnalysisException(message, cause = cause) {
  def this(namespace: Array[String]) = {
    this(s"Namespace '${namespace.quoted}' not found")
  }
}

class NoSuchTableException(message: String, cause: Option[Throwable] = None)
  extends AnalysisException(message, cause = cause) {
  def this(db: String, table: String) = {
    this(s"Table or view '$table' not found in database '$db'")
  }

  def this(tableIdent: Identifier) = {
    this(s"Table ${tableIdent.quoted} not found")
  }
}

class NoSuchPartitionException(message: String) extends AnalysisException(message) {
  def this(db: String, table: String, spec: Object) = {
    this(s"Partition not found in table '$table' database '$db'")
  }

  def this(tableName: String, partitionIdent: InternalRow, partitionSchema: StructType) = {
    this(s"Partition not found in table $tableName: "
      + partitionIdent.toSeq(partitionSchema).zip(partitionSchema.map(_.name))
        .map( kv => s"${kv._1} -> ${kv._2}").mkString(","))
  }
}

class NoSuchPermanentFunctionException(db: String, func: String)
  extends AnalysisException(s"Function '$func' not found in database '$db'")

class NoSuchFunctionException(db: String, func: String, cause: Option[Throwable] = None)
  extends AnalysisException(
    s"Undefined function: '$func'. This function is neither a registered temporary function nor " +
    s"a permanent function registered in the database '$db'.", cause = cause)

class NoSuchPartitionsException(message: String) extends AnalysisException(message) {
  def this(db: String, table: String, specs: Seq[Object]) = {
    this(s"The following partitions not found in table '$table' database '$db':\n"
      + specs.mkString("\n===\n"))
  }

  def this(tableName: String, partitionIdents: Seq[InternalRow], partitionSchema: StructType) = {
    this(s"The following partitions not found in table $tableName: "
      + partitionIdents.map(_.toSeq(partitionSchema).zip(partitionSchema.map(_.name))
        .map( kv => s"${kv._1} -> ${kv._2}").mkString(",")).mkString("\n===\n"))
  }
}

class NoSuchTempFunctionException(func: String)
  extends AnalysisException(s"Temporary function '$func' not found")
