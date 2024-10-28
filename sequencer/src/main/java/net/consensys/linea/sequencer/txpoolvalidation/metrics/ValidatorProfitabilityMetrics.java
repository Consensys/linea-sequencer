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

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReference;

import net.consensys.linea.bl.TransactionProfitabilityCalculator;
import net.consensys.linea.config.LineaProfitabilityConfiguration;
import net.consensys.linea.metrics.LineaMetricCategory;
import org.hyperledger.besu.datatypes.Quantity;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.AddedBlockContext;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;

public class ValidatorProfitabilityMetrics {

  // TODO: Add check if this node is a Sequencer

  private static final double[] HISTOGRAM_BUCKETS = {0.1, 0.5, 0.8, 1.0, 1.2, 1.5, 2.0, 5.0, 10.0};

  // Existing counters
  private final Counter lowProfitabilityCounter;
  private final Counter highProfitabilityCounter;
  private final Counter avgProfitabilityCounter;

  // New metrics for sealed block profitability tracking
  private final LabelledMetric<Counter> sealedBlockProfitabilityHistogram;
  private final LabelledGauge sealedBlockLowestRatio;
  private final LabelledGauge sealedBlockHighestRatio;
  private final LabelledGauge sealedBlockAverageRatio;

  private final TransactionProfitabilityCalculator profitabilityCalculator;
  private final LineaProfitabilityConfiguration profitabilityConf;
  private final BesuConfiguration besuConfiguration;

  // Track current block stats
  private final AtomicReference<Double> currentLowest = new AtomicReference<>(Double.MAX_VALUE);
  private final AtomicReference<Double> currentHighest = new AtomicReference<>(0.0);
  private final AtomicReference<Double> currentAverage = new AtomicReference<>(0.0);

  public ValidatorProfitabilityMetrics(
    final BesuConfiguration besuConfiguration,
    final MetricsSystem metricsSystem,
    final LineaProfitabilityConfiguration profitabilityConf) {

    this.besuConfiguration = besuConfiguration;
    this.profitabilityConf = profitabilityConf;
    this.profitabilityCalculator = new TransactionProfitabilityCalculator(profitabilityConf);

    this.lowProfitabilityCounter =
      metricsSystem.createCounter(
        LineaMetricCategory.PROFITABILITY,
        "tx_pool_profitability_low",
        "Number of low profitability transactions");

    this.avgProfitabilityCounter =
      metricsSystem.createCounter(
        LineaMetricCategory.PROFITABILITY,
        "tx_pool_profitability_avg",
        "Number of average profitability transactions");

    this.highProfitabilityCounter =
      metricsSystem.createCounter(
        LineaMetricCategory.PROFITABILITY,
        "tx_pool_profitability_high",
        "Number of high profitability transactions");

    // Add new sealed block metrics
    this.sealedBlockProfitabilityHistogram = metricsSystem.createLabelledCounter(
      LineaMetricCategory.PROFITABILITY,
      "sealed_block_profitability_ratio",
      "Distribution of transaction profitability ratios in last sealed block",
      "bucket"
    );

    // Initialize histogram buckets
    for (double bucket : HISTOGRAM_BUCKETS) {
      sealedBlockProfitabilityHistogram.labels(String.format("le_%.1f", bucket));
    }

    this.sealedBlockLowestRatio = metricsSystem.createLabelledGauge(
      LineaMetricCategory.PROFITABILITY,
      "sealed_block_profitability_min",
      "Lowest profitability ratio in last sealed block"
    );
    this.sealedBlockLowestRatio.labels(currentLowest::get);

    this.sealedBlockHighestRatio = metricsSystem.createLabelledGauge(
      LineaMetricCategory.PROFITABILITY,
      "sealed_block_profitability_max",
      "Highest profitability ratio in last sealed block"
    );
    this.sealedBlockHighestRatio.labels(currentHighest::get);

    this.sealedBlockAverageRatio = metricsSystem.createLabelledGauge(
      LineaMetricCategory.PROFITABILITY,
      "sealed_block_profitability_avg",
      "Average profitability ratio in last sealed block"
    );
    this.sealedBlockAverageRatio.labels(currentAverage::get);
  }

  public void recordProfitabilityLevel(double profitabilityRatio) {
    if (profitabilityRatio < 1.0) {
      lowProfitabilityCounter.inc();
    } else if (profitabilityRatio >= 1.0 && profitabilityRatio <= 2.0) {
      avgProfitabilityCounter.inc();
    } else {
      highProfitabilityCounter.inc();
    }
  }

  public void handleBlockAdded(AddedBlockContext blockContext) {

    // TODO: Process only sealed blocks

    // Reset stats for new block
    currentLowest.set(Double.MAX_VALUE);
    currentHighest.set(0.0);
    double sumRatios = 0.0;
    int count = 0;

    // Process each transaction
    for (Transaction transaction : blockContext.getBlockBody().getTransactions()) {
      Wei profitablePriorityFeePerGas =
        profitabilityCalculator.profitablePriorityFeePerGas(
          transaction,
          profitabilityConf.txPoolMinMargin(),
          transaction.getGasLimit(),
          besuConfiguration.getMinGasPrice());

      Quantity priorityFeePerGas =
        transaction.getMaxPriorityFeePerGas().map(Quantity.class::cast).orElse(Wei.ZERO);

      if (!priorityFeePerGas.getAsBigInteger().equals(BigInteger.ZERO)) {
        double profitabilityRatio =
          profitablePriorityFeePerGas.getAsBigInteger().doubleValue()
            / priorityFeePerGas.getAsBigInteger().doubleValue();

        // Update legacy metrics
        recordProfitabilityLevel(profitabilityRatio);

        // Update block stats
        currentLowest.updateAndGet(current -> Math.min(current, profitabilityRatio));
        currentHighest.updateAndGet(current -> Math.max(current, profitabilityRatio));
        sumRatios += profitabilityRatio;
        count++;

        // Update histogram
        for (double bucket : HISTOGRAM_BUCKETS) {
          if (profitabilityRatio <= bucket) {
            sealedBlockProfitabilityHistogram.labels(String.format("le_%.1f", bucket)).inc();
          }
        }
      }
    }

    // Update average if we have transactions
    if (count > 0) {
      currentAverage.set(sumRatios / count);
    }
  }
}