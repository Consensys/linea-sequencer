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

package net.consensys.linea.sequencer.txselection.metrics;

import lombok.extern.slf4j.Slf4j;
import net.consensys.linea.metrics.LineaMetricCategory;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.Summary;

@Slf4j
public class SelectorProfitabilityMetrics {
  private final LabelledMetric<Summary> summaries;

  public SelectorProfitabilityMetrics(final MetricsSystem metricsSystem) {
    this.summaries =
        metricsSystem.createLabelledSummary(
            BesuMetricCategory.ETHEREUM,
            "selection_priority_fee_ratio",
            "The ratio between the effective priority fee and the calculated one",
            "phase");
  }

  public enum Phase {
    PRE_PROCESSING,
    POST_PROCESSING
  }

  public void track(
      final Phase phase,
      final long blockNumber,
      final Transaction tx,
      final Wei baseFee,
      final Wei effectiveGasPrice,
      final Wei profitablePriorityFee) {
    final var effectivePriorityFee = effectiveGasPrice.subtract(baseFee);
    final var ratio =
        effectivePriorityFee.getValue().doubleValue()
            / profitablePriorityFee.getValue().doubleValue();

    summaries.labels(phase.name()).observe(ratio);

    log.atTrace()
        .setMessage(
            "{}: block[{}] tx {} , baseFee {}, effectiveGasPrice {}, ratio (effectivePayingPriorityFee {} / calculatedProfitablePriorityFee {}) {}")
        .addArgument(phase)
        .addArgument(blockNumber)
        .addArgument(tx.getHash())
        .addArgument(baseFee::toHumanReadableString)
        .addArgument(effectiveGasPrice::toHumanReadableString)
        .addArgument(effectivePriorityFee::toHumanReadableString)
        .addArgument(profitablePriorityFee::toHumanReadableString)
        .addArgument(ratio)
        .log();
  }
}
