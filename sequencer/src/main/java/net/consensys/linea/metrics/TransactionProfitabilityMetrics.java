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

package net.consensys.linea.metrics;

import java.math.BigInteger;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import net.consensys.linea.util.LineaPricingUtils;
import org.hyperledger.besu.datatypes.Quantity;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.plugin.services.metrics.OperationTimer;

@Slf4j
public class TransactionProfitabilityMetrics {

  private final LabelledMetric<OperationTimer> extraDataVariableCostTimer;
  private final LabelledMetric<OperationTimer> extraDataFixedCostTimer;
  private final LabelledMetric<OperationTimer> extraDataEthGasPriceTimer;

  private final LabelledMetric<OperationTimer> txPoolProfitabilityRatioTimer;
  private final LabelledMetric<OperationTimer> sealedBlockProfitabilityRatioTimer;

  private final LabelledMetric<OperationTimer> evaluatedTxProfitabilityRatioTimer;
  private final LabelledMetric<OperationTimer> calculatedPriorityFeeTimer;
  private final LabelledMetric<OperationTimer> compressedTxSizeTimer;

  public TransactionProfitabilityMetrics(final MetricsSystem metricsSystem) {
    extraDataVariableCostTimer =
        metricsSystem.createLabelledTimer(
            BesuMetricCategory.TRANSACTION_POOL,
            "extra_data_variable_cost",
            "Timer of variable cost extracted from extraData",
            "type");

    extraDataFixedCostTimer =
        metricsSystem.createLabelledTimer(
            BesuMetricCategory.TRANSACTION_POOL,
            "extra_data_fixed_cost",
            "Timer of fixed cost extracted from extraData",
            "type");

    extraDataEthGasPriceTimer =
        metricsSystem.createLabelledTimer(
            BesuMetricCategory.TRANSACTION_POOL,
            "extra_data_eth_gas_price",
            "Timer of ETH gas price extracted from extraData",
            "type");

    txPoolProfitabilityRatioTimer =
        metricsSystem.createLabelledTimer(
            BesuMetricCategory.TRANSACTION_POOL,
            "tx_pool_profitability_ratio",
            "Timer of profitability ratios in the transaction pool",
            "type");

    sealedBlockProfitabilityRatioTimer =
        metricsSystem.createLabelledTimer(
            BesuMetricCategory.BLOCKCHAIN,
            "sealed_block_profitability_ratio",
            "Timer of profitability ratios in the last sealed block",
            "type");

    evaluatedTxProfitabilityRatioTimer =
        metricsSystem.createLabelledTimer(
            BesuMetricCategory.TRANSACTION_POOL,
            "evaluated_tx_profitability_ratio",
            "Timer of profitability ratios for transactions evaluated by TransactionProfitabilityCalculator",
            "type");

    calculatedPriorityFeeTimer =
        metricsSystem.createLabelledTimer(
            BesuMetricCategory.TRANSACTION_POOL,
            "calculated_priority_fee",
            "Timer of priority fees calculated by TransactionProfitabilityCalculator",
            "type");

    compressedTxSizeTimer =
        metricsSystem.createLabelledTimer(
            BesuMetricCategory.TRANSACTION_POOL,
            "compressed_tx_size",
            "Timer of compressed transaction sizes",
            "type");
  }

  public void updateExtraDataMetrics(LineaPricingUtils.PricingData pricingData) {
    recordMetric(extraDataVariableCostTimer, "current", pricingData.getVariableCost().toLong());
    recordMetric(extraDataFixedCostTimer, "current", pricingData.getFixedCost().toLong());
    recordMetric(extraDataEthGasPriceTimer, "current", pricingData.getEthGasPrice().toLong());
  }

  public void recordCompressedTxSize(int compressedSize) {
    recordMetric(compressedTxSizeTimer, "current", compressedSize);
  }

  public void recordPreProcessingProfitability(
      final Transaction transaction,
      final Wei baseFee,
      final Wei transactionGasPrice,
      final long gasLimit) {
    boolean isProfitable = transactionGasPrice.greaterThan(baseFee);
    recordMetric(txPoolProfitabilityRatioTimer, isProfitable ? "profitable" : "unprofitable", 1);
  }

  public void recordTxPoolProfitabilityRatio(double ratio) {
    recordMetric(txPoolProfitabilityRatioTimer, "current", (long) (ratio * 1_000_000));
  }

  public void recordSealedBlockProfitabilityRatio(double ratio) {
    recordMetric(sealedBlockProfitabilityRatioTimer, "current", (long) (ratio * 1_000_000));
  }

  public void recordEvaluatedTxProfitabilityRatio(double ratio) {
    recordMetric(evaluatedTxProfitabilityRatioTimer, "current", (long) (ratio * 1_000_000));
  }

  public void recordCalculatedPriorityFee(Quantity priorityFee) {
    recordMetric(calculatedPriorityFeeTimer, "current", priorityFee.getAsBigInteger().longValue());
  }

  public void recordCalculatedPriorityFee(Wei priorityFee) {
    recordCalculatedPriorityFee((Quantity) priorityFee);
  }

  public void recordTxPoolProfitability(Transaction transaction, Quantity calculatedPriorityFee) {
    recordProfitabilityRatio(
        transaction, calculatedPriorityFee, this::recordTxPoolProfitabilityRatio);
  }

  public void recordTxPoolProfitability(Transaction transaction, Wei calculatedPriorityFee) {
    recordTxPoolProfitability(transaction, (Quantity) calculatedPriorityFee);
  }

  public void recordSealedBlockProfitability(
      Transaction transaction, Quantity calculatedPriorityFee) {
    recordProfitabilityRatio(
        transaction, calculatedPriorityFee, this::recordSealedBlockProfitabilityRatio);
  }

  public void recordSealedBlockProfitability(Transaction transaction, Wei calculatedPriorityFee) {
    recordSealedBlockProfitability(transaction, (Quantity) calculatedPriorityFee);
  }

  private void recordProfitabilityRatio(
      Transaction transaction, Quantity calculatedPriorityFee, RatioRecorder ratioRecorder) {
    Optional<? extends Quantity> maxPriorityFeePerGas = transaction.getMaxPriorityFeePerGas();
    if (maxPriorityFeePerGas.isPresent()) {
      Quantity actualPriorityFee = maxPriorityFeePerGas.get();
      if (!actualPriorityFee.getAsBigInteger().equals(BigInteger.ZERO)) {
        double ratio =
            calculatedPriorityFee.getAsBigInteger().doubleValue()
                / actualPriorityFee.getAsBigInteger().doubleValue();
        ratioRecorder.record(ratio);
      }
    }
  }

  private void recordMetric(LabelledMetric<OperationTimer> timer, String label, long value) {
    OperationTimer.TimingContext context = timer.labels(label).startTimer();
    try {
      // Simulate an operation that takes 'value' nanoseconds
      Thread.sleep(0, (int) (value % 1_000_000));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      context.stopTimer();
    }
  }

  @FunctionalInterface
  private interface RatioRecorder {
    void record(double ratio);
  }
}
