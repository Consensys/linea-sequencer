/*
 * Copyright Consensys Software Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package net.consensys.linea.config;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

/** The Linea profitability calculator configuration. */
@Builder(toBuilder = true)
@Accessors(fluent = true)
@Getter
@ToString
public class LineaProfitabilityConfiguration {
  private long fixedCostKWei;
  private long variableCostKWei;
  private double minMargin;
  private double estimateGasMinMargin;
  private double txPoolMinMargin;
  private boolean txPoolCheckApiEnabled;
  private boolean txPoolCheckP2pEnabled;

  /**
   * These 2 parameters must be atomically updated
   *
   * @param fixedCostKWei fixed cost in KWei
   * @param variableCostKWei variable cost in KWei
   */
  public synchronized void updateFixedAndVariableCost(
      final long fixedCostKWei, final long variableCostKWei) {
    this.fixedCostKWei = fixedCostKWei;
    this.variableCostKWei = variableCostKWei;
  }

  public synchronized long fixedCostKWei() {
    return fixedCostKWei;
  }

  public synchronized long variableCostKWei() {
    return variableCostKWei;
  }
}
