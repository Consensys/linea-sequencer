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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.consensys.linea.config.LineaProfitabilityConfiguration;
import net.consensys.linea.metrics.TransactionProfitabilityMetrics;
import net.consensys.linea.util.LineaPricingUtils.PricingData;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TransactionProfitabilityCalculatorTest {

  private TransactionProfitabilityCalculator calculator;
  private TransactionProfitabilityMetrics profitabilityMetrics;
  private LineaProfitabilityConfiguration config;
  private PricingData mockPricingData; // Added mock for PricingData

  @BeforeEach
  public void setUp() {
    // Mock TransactionProfitabilityMetrics
    profitabilityMetrics = mock(TransactionProfitabilityMetrics.class);

    // Define the configuration for the profitability calculator
    config =
        LineaProfitabilityConfiguration.builder()
            .fixedCostWei(1_000_000)
            .variableCostWei(1_000_000_000)
            .minMargin(1.1) // 10% margin required
            .estimateGasMinMargin(1.1)
            .build();

    // Instantiate the TransactionProfitabilityCalculator with the config and mock MetricsSystem
    calculator = new TransactionProfitabilityCalculator(config, profitabilityMetrics);

    // Mock PricingData object
    mockPricingData = mock(PricingData.class);
    when(mockPricingData.getVariableCost())
        .thenReturn(Wei.of(1_000_000_000)); // Set some reasonable default values
  }

  @Test
  public void testTransactionIsProfitable() {
    // Arrange
    Transaction mockTransaction = mock(Transaction.class);

    // Simulate a transaction being encoded
    when(mockTransaction.encoded()).thenReturn(Bytes.of(0x01, 0x02));

    // Set up the transaction to return a reasonable gas price
    Wei baseFee = Wei.of(1_000_000_000); // 1 Gwei
    Wei priorityFee = Wei.of(2_000_000_000); // 2 Gwei (profitability determined by this)
    Wei maxGasPrice = baseFee.add(priorityFee); // Total gas price is base + priority

    // Set a large enough gas limit for the transaction
    long gasLimit = 21_000;

    // Mock the min gas price set by the miner
    Wei minGasPrice = Wei.of(1_000_000_000); // 1 Gwei

    // Act
    boolean isProfitable =
        calculator.isProfitable(
            "TestTx",
            mockTransaction,
            config.minMargin(),
            baseFee,
            maxGasPrice,
            gasLimit,
            minGasPrice,
            mockPricingData // Passing PricingData
            );

    // Assert
    assertThat(isProfitable).isTrue(); // The transaction should be profitable
    verify(profitabilityMetrics)
        .recordPreProcessingProfitability(
            mockTransaction,
            baseFee,
            maxGasPrice,
            gasLimit); // Verify that profitability metric was recorded
  }

  @Test
  public void testTransactionIsNotProfitable() {
    // Arrange
    Transaction mockTransaction = mock(Transaction.class);

    // Mock the behavior of encoded() to return a valid non-null value.
    // Create a mock byte array representing the encoded transaction.
    Bytes mockEncodedTransaction =
        Bytes.of(0x01, 0x02, 0x03); // Mock byte data for the transaction.

    // Make the mockTransaction return the mockEncodedTransaction when encoded() is called.
    when(mockTransaction.encoded()).thenReturn(mockEncodedTransaction);

    // Set up the transaction with a low priority fee that makes it unprofitable
    Wei baseFee = Wei.of(1_000_000_000); // 1 Gwei
    Wei priorityFee = Wei.of(100_000); // 0.0001 Gwei (too low)
    Wei maxGasPrice = baseFee.add(priorityFee); // Total gas price

    // Set a large enough gas limit for the transaction
    long gasLimit = 21_000;

    // Mock the min gas price set by the miner
    Wei minGasPrice = Wei.of(1_000_000_000); // 1 Gwei

    // Act
    boolean isNotProfitable =
        calculator.isProfitable(
            "TestTx",
            mockTransaction,
            config.minMargin(),
            baseFee,
            maxGasPrice,
            gasLimit,
            minGasPrice,
            mockPricingData // Passing PricingData
            );

    // Assert
    assertThat(isNotProfitable).isFalse(); // The transaction should not be profitable
    verify(profitabilityMetrics)
        .recordPreProcessingProfitability(
            mockTransaction,
            baseFee,
            maxGasPrice,
            gasLimit); // Verify that profitability metric was recorded
  }

  @Test
  public void testNodePerceptionOnExtraData() {
    // Arrange
    Transaction mockTransaction = mock(Transaction.class);

    // Simulate the encoded byte size of the transaction
    when(mockTransaction.encoded()).thenReturn(Bytes.of(0x01, 0x02, 0x03));

    Wei baseFee = Wei.of(1_000_000_000); // 1 Gwei
    Wei ethGasPrice = Wei.of(3_000_000_000L); // 3 Gwei (paying gas price)
    Wei priorityFee = Wei.of(100_000); // 0.0001 Gwei (too low)
    Wei maxGasPrice = baseFee.add(priorityFee); // Total gas price
    long gasLimit = 21_000;
    Wei minGasPrice = Wei.of(1_000_000_000); // 1 Gwei

    // Act
    Wei profitablePriorityFee =
        calculator.profitablePriorityFeePerGas(
            mockTransaction,
            config.minMargin(),
            gasLimit,
            ethGasPrice,
            mockPricingData); // Updated to pass PricingData

    // Assert the profitable priority fee is calculated properly
    assertThat(profitablePriorityFee).isGreaterThan(Wei.ZERO);

    boolean result =
        calculator.isProfitable(
            "TestTx",
            mockTransaction,
            config.minMargin(),
            baseFee,
            maxGasPrice,
            gasLimit,
            minGasPrice,
            mockPricingData // Updated to pass PricingData
            );

    // Verify that metrics are recorded
    verify(profitabilityMetrics)
        .recordPreProcessingProfitability(mockTransaction, baseFee, ethGasPrice, gasLimit);
  }
}
