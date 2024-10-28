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
package net.consensys.linea.sequencer.txpoolvalidation.metrics;

import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
import net.consensys.linea.bl.TransactionProfitabilityCalculator;
import net.consensys.linea.config.LineaProfitabilityConfiguration;
import org.apache.tuweni.units.bigints.UInt256s;
import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Histogram;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService;

/**
 * Tracks profitability metrics for transactions in the transaction pool. Specifically monitors the
 * ratio of profitable priority fee to actual priority fee:
 * profitablePriorityFeePerGas/transaction.priorityFeePerGas
 *
 * <p>Provides: - Lowest ratio seen (minimum profitability) - Highest ratio seen (maximum
 * profitability) - Distribution histogram of ratios
 */
@Slf4j
public class TransactionPoolProfitabilityMetrics {
  private static final double[] HISTOGRAM_BUCKETS = {0.1, 0.5, 0.8, 1.0, 1.2, 1.5, 2.0, 5.0, 10.0};

  private final TransactionProfitabilityCalculator profitabilityCalculator;
  private final LineaProfitabilityConfiguration profitabilityConf;
  private final BesuConfiguration besuConfiguration;
  private final TransactionPoolService transactionPoolService;
  private final BlockchainService blockchainService;

  private final LabelledMetric<Histogram> profitabilityHistogram;

  // Thread-safe references for gauge values
  private final AtomicReference<Double> currentLowest = new AtomicReference<>(0.0);
  private final AtomicReference<Double> currentHighest = new AtomicReference<>(0.0);

  public TransactionPoolProfitabilityMetrics(
      final BesuConfiguration besuConfiguration,
      final MetricsSystem metricsSystem,
      final LineaProfitabilityConfiguration profitabilityConf,
      final TransactionPoolService transactionPoolService,
      final BlockchainService blockchainService) {

    this.besuConfiguration = besuConfiguration;
    this.profitabilityConf = profitabilityConf;
    this.profitabilityCalculator = new TransactionProfitabilityCalculator(profitabilityConf);
    this.transactionPoolService = transactionPoolService;
    this.blockchainService = blockchainService;

    // Min/Max/Avg gauges with DoubleSupplier
    LabelledGauge lowestProfitabilityRatio =
        metricsSystem.createLabelledGauge(
            BesuMetricCategory.ETHEREUM,
            "txpool_profitability_ratio_min",
            "Lowest profitability ratio seen");
    lowestProfitabilityRatio.labels(currentLowest::get);

    LabelledGauge highestProfitabilityRatio =
        metricsSystem.createLabelledGauge(
            BesuMetricCategory.ETHEREUM,
            "txpool_profitability_ratio_max",
            "Highest profitability ratio seen");
    highestProfitabilityRatio.labels(currentHighest::get);

    // Running statistics
    this.profitabilityHistogram =
        metricsSystem.createLabelledHistogram(
            BesuMetricCategory.ETHEREUM,
            "txpool_profitability_ratio",
            "Histogram statistics of profitability ratios",
            HISTOGRAM_BUCKETS,
            "type");
  }

  public void handleTransaction(final Transaction transaction) {
    final Wei actualPriorityFeePerGas;
    if (transaction.getMaxPriorityFeePerGas().isEmpty()) {
      actualPriorityFeePerGas =
          Wei.fromQuantity(transaction.getGasPrice().orElseThrow())
              .subtract(blockchainService.getNextBlockBaseFee().orElseThrow());
    } else {
      final Wei maxPriorityFeePerGas =
          Wei.fromQuantity(transaction.getMaxPriorityFeePerGas().get());
      actualPriorityFeePerGas =
          UInt256s.min(
              maxPriorityFeePerGas.add(blockchainService.getNextBlockBaseFee().orElseThrow()),
              Wei.fromQuantity(transaction.getMaxFeePerGas().orElseThrow()));
    }

    Wei profitablePriorityFeePerGas =
        profitabilityCalculator.profitablePriorityFeePerGas(
            transaction,
            profitabilityConf.txPoolMinMargin(),
            transaction.getGasLimit(),
            besuConfiguration.getMinGasPrice());

    double ratio =
        actualPriorityFeePerGas.toBigInteger().doubleValue()
            / profitablePriorityFeePerGas.toBigInteger().doubleValue();

    updateRunningStats(ratio);

    log.trace("Recorded profitability ratio {} for tx {}", ratio, transaction.getHash());
  }

  private void updateRunningStats(double ratio) {
    // Update lowest seen
    currentLowest.updateAndGet(current -> Math.min(current, ratio));

    // Update highest seen
    currentHighest.updateAndGet(current -> Math.max(current, ratio));

    // Record the observation in summary
    profitabilityHistogram.labels("profitability").observe(ratio);
  }

  public void recalculate() {
    transactionPoolService.getPendingTransactions().stream()
        .map(PendingTransaction::getTransaction)
        .forEach(this::handleTransaction);
  }
}
