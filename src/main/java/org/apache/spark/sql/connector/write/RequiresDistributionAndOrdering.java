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

package org.apache.spark.sql.connector.write;

import org.apache.spark.annotation.Experimental;
import org.apache.spark.sql.connector.distributions.Distribution;
import org.apache.spark.sql.connector.distributions.UnspecifiedDistribution;
import org.apache.spark.sql.connector.expressions.SortOrder;

/**
 * A write that requires a specific distribution and ordering of data.
 *
 * @since 3.2.0
 */
@Experimental
public interface RequiresDistributionAndOrdering extends Write {
  /**
   * Returns the distribution required by this write.
   * <p>
   * Spark will distribute incoming records across partitions to satisfy the required distribution
   * before passing the records to the data source table on write.
   * <p>
   * Implementations may return {@link UnspecifiedDistribution} if they don't require any specific
   * distribution of data on write.
   *
   * @return the required distribution
   */
  Distribution requiredDistribution();

  /**
   * Returns the ordering required by this write.
   * <p>
   * Spark will order incoming records within partitions to satisfy the required ordering
   * before passing those records to the data source table on write.
   * <p>
   * Implementations may return an empty array if they don't require any specific ordering of data
   * on write.
   *
   * @return the required ordering
   */
  SortOrder[] requiredOrdering();
}
