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
package net.consensys.linea.sequencer.txselection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import net.consensys.linea.config.LineaProfitabilityConfiguration;
import net.consensys.linea.config.LineaTracerConfiguration;
import net.consensys.linea.config.LineaTransactionSelectorConfiguration;
import net.consensys.linea.plugins.config.LineaL1L2BridgeSharedConfiguration;
import net.consensys.linea.rpc.services.BundlePoolService;
import net.consensys.linea.rpc.services.LineaLimitedBundlePool;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.txselection.BlockTransactionSelectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

class LineaTransactionSelectorFactoryTest {

  private BlockchainService mockBlockchainService;
  private LineaTransactionSelectorConfiguration mockTxSelectorConfiguration;
  private LineaL1L2BridgeSharedConfiguration mockL1L2BridgeConfiguration;
  private LineaProfitabilityConfiguration mockProfitabilityConfiguration;
  private LineaTracerConfiguration mockTracerConfiguration;
  private Map<String, Integer> mockLimitsMap;
  private BesuEvents mockEvents;
  private LineaLimitedBundlePool bundlePool;
  private Optional<BundlePoolService> mockBundlePool;

  private LineaTransactionSelectorFactory factory;

  @BeforeEach
  void setUp() {
    mockBlockchainService = mock(BlockchainService.class);
    mockTxSelectorConfiguration = mock(LineaTransactionSelectorConfiguration.class);
    mockL1L2BridgeConfiguration = mock(LineaL1L2BridgeSharedConfiguration.class);
    mockProfitabilityConfiguration = mock(LineaProfitabilityConfiguration.class);
    mockTracerConfiguration = mock(LineaTracerConfiguration.class);
    mockLimitsMap = new HashMap<>();
    mockEvents = mock(BesuEvents.class);
    bundlePool = spy(new LineaLimitedBundlePool(4096, mockEvents));
    mockBundlePool = spy(Optional.of(bundlePool));

    factory =
        new LineaTransactionSelectorFactory(
            mockBlockchainService,
            mockTxSelectorConfiguration,
            mockL1L2BridgeConfiguration,
            mockProfitabilityConfiguration,
            mockTracerConfiguration,
            mockLimitsMap,
            Optional.empty(),
            Optional.empty(),
            mockBundlePool,
            15_000_000L);
  }

  @Test
  void testSelectPendingTransactions_WithBundles() {
    var mockBts = mock(BlockTransactionSelectionService.class);
    var mockPendingBlockHeader = mock(ProcessableBlockHeader.class);
    when(mockPendingBlockHeader.getNumber()).thenReturn(1L);

    var mockHash = Hash.wrap(Bytes32.random());
    var mockBundle = createBundle(mockHash, 1L, Optional.empty());
    bundlePool.putOrReplace(mockHash, mockBundle);

    when(mockBts.evaluatePendingTransaction(any())).thenReturn(TransactionSelectionResult.SELECTED);

    factory.selectPendingTransactions(mockBts, mockPendingBlockHeader);

    verify(mockBts).commit();
  }

  @ParameterizedTest()
  @ArgumentsSource(FailedTransactionSelectionResultProvider.class)
  void testSelectPendingTransactions_WithFailedBundle(TransactionSelectionResult failStatus) {
    var mockBts = mock(BlockTransactionSelectionService.class);
    var mockPendingBlockHeader = mock(ProcessableBlockHeader.class);
    when(mockPendingBlockHeader.getNumber()).thenReturn(1L);

    var mockHash = Hash.wrap(Bytes32.random());
    var mockBundle = createBundle(mockHash, 1L, Optional.empty());
    bundlePool.putOrReplace(mockHash, mockBundle);

    when(mockBts.evaluatePendingTransaction(any())).thenReturn(failStatus);

    factory.selectPendingTransactions(mockBts, mockPendingBlockHeader);

    verify(mockBts).rollback();
  }

  @Test
  void testSelectPendingTransactions_WithoutBundles() {
    var mockBts = mock(BlockTransactionSelectionService.class);
    var mockPendingBlockHeader = mock(ProcessableBlockHeader.class);
    when(mockPendingBlockHeader.getNumber()).thenReturn(1L);

    doNothing().when(mockBundlePool).ifPresent(any());
    doAnswer(__ -> Optional.empty()).when(mockBundlePool).map(any());
    factory.selectPendingTransactions(mockBts, mockPendingBlockHeader);

    verifyNoInteractions(mockBts);
  }

  @Test
  void testSelectPendingTransactions_GasLimitCheck() {
    ProcessableBlockHeader pendingBlockHeader = mock(ProcessableBlockHeader.class);
    BlockTransactionSelectionService bts = mock(BlockTransactionSelectionService.class);
    long mockBlockHeader = 15L;

    when(pendingBlockHeader.getNumber()).thenReturn(mockBlockHeader);

    PendingTransaction tx1 = mock(PendingTransaction.class);
    PendingTransaction tx2 = mock(PendingTransaction.class);
    PendingTransaction tx3 = mock(PendingTransaction.class);
    PendingTransaction tx4 = mock(PendingTransaction.class);

    Transaction transaction1 = mock(Transaction.class);
    Transaction transaction2 = mock(Transaction.class);
    Transaction transaction3 = mock(Transaction.class);
    Transaction transaction4 = mock(Transaction.class);

    when(tx1.getTransaction()).thenReturn(transaction1);
    when(tx2.getTransaction()).thenReturn(transaction2);
    when(tx3.getTransaction()).thenReturn(transaction3);
    when(tx4.getTransaction()).thenReturn(transaction4);

    when(transaction1.getGasLimit()).thenReturn(5_000_000L);
    when(transaction2.getGasLimit()).thenReturn(7_000_000L);
    // exceeds limit, will not be included
    when(transaction3.getGasLimit()).thenReturn(5_000_000L);
    // within limit but not selected:
    when(transaction4.getGasLimit()).thenReturn(2_000_000L);

    when(bts.evaluatePendingTransaction(tx1)).thenReturn(TransactionSelectionResult.SELECTED);
    when(bts.evaluatePendingTransaction(tx2)).thenReturn(TransactionSelectionResult.SELECTED);
    when(bts.evaluatePendingTransaction(tx3)).thenReturn(TransactionSelectionResult.SELECTED);
    when(bts.evaluatePendingTransaction(tx4))
        .thenReturn(TransactionSelectionResult.BLOCK_OCCUPANCY_ABOVE_THRESHOLD);

    var bundle1 = createBundle(Hash.wrap(Bytes32.random()), mockBlockHeader, Optional.of(tx1));
    var bundle2 = createBundle(Hash.wrap(Bytes32.random()), mockBlockHeader, Optional.of(tx2));
    var bundle3 = createBundle(Hash.wrap(Bytes32.random()), mockBlockHeader, Optional.of(tx3));
    var bundle4 = createBundle(Hash.wrap(Bytes32.random()), mockBlockHeader, Optional.of(tx4));

    bundlePool.putOrReplace(bundle1.bundleIdentifier(), bundle1);
    bundlePool.putOrReplace(bundle2.bundleIdentifier(), bundle2);
    bundlePool.putOrReplace(bundle3.bundleIdentifier(), bundle3);
    bundlePool.putOrReplace(bundle3.bundleIdentifier(), bundle4);

    factory.selectPendingTransactions(bts, pendingBlockHeader);

    // twice for not exceeding and selected
    verify(bts, times(2)).commit();
    // once for not selected
    verify(bts, times(1)).rollback();
    // never evaluated one that exceeds:
    verify(bts, never()).evaluatePendingTransaction(tx3);
  }

  private LineaLimitedBundlePool.TransactionBundle createBundle(
      Hash hash, long blockNumber, Optional<PendingTransaction> optPendingTx) {
    return new LineaLimitedBundlePool.TransactionBundle(
        hash,
        List.of(
            optPendingTx.isPresent()
                ? optPendingTx.get()
                : mock(PendingTransaction.class, RETURNS_DEEP_STUBS)),
        blockNumber,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  static class FailedTransactionSelectionResultProvider implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(
        org.junit.jupiter.api.extension.ExtensionContext context) {
      return Stream.of(
          Arguments.of(TransactionSelectionResult.BLOCK_FULL),
          Arguments.of(TransactionSelectionResult.BLOBS_FULL),
          Arguments.of(TransactionSelectionResult.BLOCK_SELECTION_TIMEOUT),
          Arguments.of(TransactionSelectionResult.BLOCK_SELECTION_TIMEOUT_INVALID_TX),
          Arguments.of(TransactionSelectionResult.TX_EVALUATION_TOO_LONG),
          Arguments.of(TransactionSelectionResult.INVALID_TX_EVALUATION_TOO_LONG),
          Arguments.of(TransactionSelectionResult.BLOCK_OCCUPANCY_ABOVE_THRESHOLD),
          Arguments.of(TransactionSelectionResult.TX_TOO_LARGE_FOR_REMAINING_GAS),
          Arguments.of(TransactionSelectionResult.TX_TOO_LARGE_FOR_REMAINING_BLOB_GAS));
    }
  }
}
