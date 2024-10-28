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
import net.consensys.linea.metrics.LineaMetricCategory;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import org.hyperledger.besu.plugin.services.metrics.Summary;

/**
 * Tracks profitability metrics for transactions in the transaction pool.
 * Specifically monitors the ratio of profitable priority fee to actual priority fee:
 * profitablePriorityFeePerGas/transaction.priorityFeePerGas
 * <p>
 * Provides:
 * - Lowest ratio seen (minimum profitability)
 * - Highest ratio seen (maximum profitability)
 * - Running average ratio (average profitability)
 * - Distribution histogram of ratios
 */
@Slf4j
public class TransactionPoolProfitabilityMetrics {
  private static final double[] HISTOGRAM_BUCKETS = {0.1, 0.5, 0.8, 1.0, 1.2, 1.5, 2.0, 5.0, 10.0};

  private final TransactionProfitabilityCalculator profitabilityCalculator;
  private final LineaProfitabilityConfiguration profitabilityConf;
  private final BesuConfiguration besuConfiguration;

  private final LabelledMetric<Counter> profitabilityHistogram;

  private final LabelledMetric<Summary> profitabilityRatioSummary;
  private final Counter invalidTransactionCount;

  // Thread-safe references for gauge values
  private final AtomicReference<Double> currentLowest = new AtomicReference<>(Double.MAX_VALUE);
  private final AtomicReference<Double> currentHighest = new AtomicReference<>(0.0);

  public TransactionPoolProfitabilityMetrics(
    final BesuConfiguration besuConfiguration,
    final MetricsSystem metricsSystem,
    final LineaProfitabilityConfiguration profitabilityConf) {

    this.besuConfiguration = besuConfiguration;
    this.profitabilityConf = profitabilityConf;
    this.profitabilityCalculator = new TransactionProfitabilityCalculator(profitabilityConf);

    // Distribution histogram
    this.profitabilityHistogram = metricsSystem.createLabelledCounter(
      LineaMetricCategory.PROFITABILITY,
      "txpool_profitability_ratio",
      "Distribution of transaction profitability ratios in TxPool",
      "bucket"
    );

    // Min/Max/Avg gauges with DoubleSupplier
    LabelledGauge lowestProfitabilityRatio = metricsSystem.createLabelledGauge(
      LineaMetricCategory.PROFITABILITY,
      "txpool_profitability_ratio_min",
      "Lowest profitability ratio seen"
    );
    lowestProfitabilityRatio.labels(currentLowest::get);

    LabelledGauge highestProfitabilityRatio = metricsSystem.createLabelledGauge(
      LineaMetricCategory.PROFITABILITY,
      "txpool_profitability_ratio_max",
      "Highest profitability ratio seen"
    );
    highestProfitabilityRatio.labels(currentHighest::get);

    LabelledGauge averageProfitabilityRatio = metricsSystem.createLabelledGauge(
      LineaMetricCategory.PROFITABILITY,
      "txpool_profitability_ratio_avg",
      "Average profitability ratio"
    );
    AtomicReference<Double> currentAverage = new AtomicReference<>(0.0);
    averageProfitabilityRatio.labels(currentAverage::get);

    // Running statistics
    this.profitabilityRatioSummary = metricsSystem.createLabelledSummary(
      LineaMetricCategory.PROFITABILITY,
      "txpool_profitability_ratio_summary",
      "Summary statistics of profitability ratios",
      "type"
    );

    this.invalidTransactionCount = metricsSystem.createCounter(
      LineaMetricCategory.PROFITABILITY,
      "txpool_invalid_transaction_count",
      "Number of transactions that couldn't be processed for profitability"
    );

    // Pre-create histogram buckets
    for (double bucket : HISTOGRAM_BUCKETS) {
      profitabilityHistogram.labels(String.format("le_%.1f", bucket));
    }
  }

  public void handleTransactionAdded(Transaction transaction) {
    try {
      if (transaction.getMaxPriorityFeePerGas().isEmpty()) {
        invalidTransactionCount.inc();
        log.trace("Skipping transaction {} - no priority fee", transaction.getHash());
        return;
      }

      Wei profitablePriorityFeePerGas = profitabilityCalculator.profitablePriorityFeePerGas(
        transaction,
        profitabilityConf.txPoolMinMargin(),
        transaction.getGasLimit(),
        besuConfiguration.getMinGasPrice()
      );

      Wei actualPriorityFeePerGas = Wei.fromQuantity(transaction.getMaxPriorityFeePerGas().get());

      if (actualPriorityFeePerGas.toLong() > 0) {
        double ratio = profitablePriorityFeePerGas.toBigInteger().doubleValue() /
          actualPriorityFeePerGas.toBigInteger().doubleValue();

        updateRunningStats(ratio);
        updateHistogramBuckets(ratio);

        log.trace(
          "Recorded profitability ratio {} for tx {}",
          ratio,
          transaction.getHash()
        );
      } else {
        invalidTransactionCount.inc();
        log.trace("Skipping transaction {} - zero priority fee", transaction.getHash());
      }
    } catch (Exception e) {
      invalidTransactionCount.inc();
      log.warn(
        "Failed to record profitability metrics for tx {}: {}",
        transaction.getHash(),
        e.getMessage()
      );
    }
  }

  private void updateRunningStats(double ratio) {
    // Update lowest seen
    currentLowest.updateAndGet(current -> Math.min(current, ratio));

    // Update highest seen
    currentHighest.updateAndGet(current -> Math.max(current, ratio));

    // Record the observation in summary
    profitabilityRatioSummary.labels("profitability").observe(ratio);
  }

  private void updateHistogramBuckets(double ratio) {
    for (double bucket : HISTOGRAM_BUCKETS) {
      if (ratio <= bucket) {
        profitabilityHistogram.labels(String.format("le_%.1f", bucket)).inc();
      }
    }
  }
}