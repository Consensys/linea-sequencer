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
package net.consensys.linea.bl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import net.consensys.linea.config.LineaProfitabilityConfiguration;
import net.consensys.linea.metrics.TransactionProfitabilityMetrics;
import net.consensys.linea.util.LineaPricingUtils.PricingData;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TransactionProfitabilityCalculator3DTest {

  private TransactionProfitabilityCalculator calculator;
  private TransactionProfitabilityMetrics profitabilityMetrics;
  private LineaProfitabilityConfiguration config;
  private MetricsSystem metricsSystem;
  private PricingData mockPricingData;

  @BeforeEach
  public void setUp() {
    // Mock the MetricsSystem and LabelledGauge
    metricsSystem = mock(MetricsSystem.class);
    LabelledGauge mockProfitableGauge = mock(LabelledGauge.class);
    LabelledGauge mockUnprofitableGauge = mock(LabelledGauge.class);

    // Set the behavior for createLabelledGauge
    when(metricsSystem.createLabelledGauge(any(), any(), any(), any()))
        .thenReturn(mockProfitableGauge)
        .thenReturn(mockUnprofitableGauge);

    // Initialize profitabilityMetrics with the mocked MetricsSystem
    this.profitabilityMetrics = mock(TransactionProfitabilityMetrics.class);

    // Define the configuration for the profitability calculator
    config =
        LineaProfitabilityConfiguration.builder()
            .fixedCostWei(1_000_000) // Example fixed cost
            .variableCostWei(1_000_000_000) // Example variable cost
            .minMargin(1.1) // 10% margin required
            .estimateGasMinMargin(1.1)
            .build();

    // Instantiate the TransactionProfitabilityCalculator
    calculator = new TransactionProfitabilityCalculator(config, profitabilityMetrics);

    // Mock PricingData
    mockPricingData = mock(PricingData.class);
    when(mockPricingData.getVariableCost())
        .thenReturn(Wei.of(1_000_000_000)); // Set reasonable default values
  }

  @Test
  public void test3DPricingProfitableTransaction() {
    // Arrange
    Transaction mockTransaction = mock(Transaction.class);
    when(mockTransaction.encoded()).thenReturn(org.apache.tuweni.bytes.Bytes.of(0x01, 0x02, 0x03));

    Wei baseFee = Wei.of(1_000_000_000); // 1 Gwei
    Wei priorityFee = Wei.of(2_000_000_000); // 2 Gwei (profitability determined by this)
    Wei maxGasPrice = baseFee.add(priorityFee); // Total gas price is base + priority

    long gasLimit = 21_000;
    Wei minGasPrice = Wei.of(1_000_000_000); // 1 Gwei (minimum allowed gas price)

    // Act
    boolean isProfitable =
        calculator.isProfitable(
            "TestContext",
            mockTransaction,
            config.minMargin(),
            baseFee,
            maxGasPrice,
            gasLimit,
            minGasPrice,
            mockPricingData);

    // Assert
    assertThat(isProfitable).isTrue();
    verify(profitabilityMetrics)
        .recordPreProcessingProfitability(mockTransaction, baseFee, maxGasPrice, gasLimit);
    verify(profitabilityMetrics).recordTxPoolProfitability(eq(mockTransaction), any(Wei.class));
  }

  @Test
  public void test3DPricingUnprofitableTransaction() {
    // Arrange
    Transaction mockTransaction = mock(Transaction.class);
    when(mockTransaction.encoded()).thenReturn(org.apache.tuweni.bytes.Bytes.of(0x01, 0x02, 0x03));

    // Set up the transaction with a low priority fee that makes it unprofitable
    Wei baseFee = Wei.of(1_000_000_000); // 1 Gwei
    Wei priorityFee = Wei.of(100_000); // 0.0001 Gwei (too low)
    Wei maxGasPrice = baseFee.add(priorityFee); // Total gas price

    // Set a large enough gas limit for the transaction
    long gasLimit = 21_000;

    // Mock the min gas price set by the miner
    Wei minGasPrice = Wei.of(1_000_000_000); // 1 Gwei

    // Act - call isProfitable to trigger the logic
    boolean isProfitable =
        calculator.isProfitable(
            "TestTx",
            mockTransaction,
            config.minMargin(),
            baseFee,
            maxGasPrice,
            gasLimit,
            minGasPrice,
            mockPricingData);

    // Assert
    assertThat(isProfitable).isFalse(); // The transaction should be unprofitable

    // Verify interactions with the mocked metrics
    verify(profitabilityMetrics)
        .recordPreProcessingProfitability(mockTransaction, baseFee, maxGasPrice, gasLimit);
    verify(profitabilityMetrics).recordTxPoolProfitability(eq(mockTransaction), any(Wei.class));
  }
}
