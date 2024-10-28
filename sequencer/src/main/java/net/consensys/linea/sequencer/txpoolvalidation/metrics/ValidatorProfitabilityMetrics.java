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

public class ValidatorProfitabilityMetrics {

  private final Counter lowProfitabilityCounter;
  private final Counter highProfitabilityCounter;
  private final Counter avgProfitabilityCounter;
  private final TransactionProfitabilityCalculator profitabilityCalculator;
  private final LineaProfitabilityConfiguration profitabilityConf;
  private final BesuConfiguration besuConfiguration;

  public ValidatorProfitabilityMetrics(
      final BesuConfiguration besuConfiguration,
      final MetricsSystem metricsSystem,
      final LineaProfitabilityConfiguration profitabilityConf) {

    this.besuConfiguration = besuConfiguration;

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

    this.profitabilityConf = profitabilityConf;

    this.profitabilityCalculator = new TransactionProfitabilityCalculator(profitabilityConf);
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
    blockContext.getBlockBody().getTransactions().forEach(this::calculateTransactionProfitability);
  }

  private void calculateTransactionProfitability(Transaction transaction) {
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
      recordProfitabilityLevel(profitabilityRatio);
    }
  }
}
