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
  /** It is safe to keep this as long, since it will store value <= max_int * 1000 */
  private long fixedCostWei;

  /** It is safe to keep this as long, since it will store value <= max_int * 1000 */
  private long variableCostWei;

  private double minMargin;
  private double estimateGasMinMargin;
  private double txPoolMinMargin;
  private boolean txPoolCheckApiEnabled;
  private boolean txPoolCheckP2pEnabled;
  private boolean extraDataPricingEnabled;
  private boolean extraDataSetMinGasPriceEnabled;

  /**
   * These 2 parameters must be atomically updated
   *
   * @param fixedCostWei fixed cost in Wei
   * @param variableCostWei variable cost in Wei
   */
  public synchronized void updateFixedAndVariableCost(
      final long fixedCostWei, final long variableCostWei) {
    this.fixedCostWei = fixedCostWei;
    this.variableCostWei = variableCostWei;
  }

  public synchronized long fixedCostWei() {
    return fixedCostWei;
  }

  public synchronized long variableCostWei() {
    return variableCostWei;
  }
}
