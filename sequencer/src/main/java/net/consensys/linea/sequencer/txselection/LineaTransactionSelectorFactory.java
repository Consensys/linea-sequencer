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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import net.consensys.linea.config.LineaProfitabilityConfiguration;
import net.consensys.linea.config.LineaTracerConfiguration;
import net.consensys.linea.config.LineaTransactionSelectorConfiguration;
import net.consensys.linea.jsonrpc.JsonRpcManager;
import net.consensys.linea.metrics.HistogramMetrics;
import net.consensys.linea.plugins.config.LineaL1L2BridgeSharedConfiguration;
import net.consensys.linea.rpc.services.BundlePoolService;
import net.consensys.linea.sequencer.txselection.selectors.LineaTransactionSelector;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.txselection.BlockTransactionSelectionService;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelectorFactory;
import org.hyperledger.besu.plugin.services.txselection.SelectorsStateManager;

/**
 * Represents a factory for creating transaction selectors. Note that a new instance of the
 * transaction selector is created everytime a new block creation time is started.
 *
 * <p>Also provides an entrypoint for bundle transactions
 */
public class LineaTransactionSelectorFactory implements PluginTransactionSelectorFactory {
  private final BlockchainService blockchainService;
  private final Optional<JsonRpcManager> rejectedTxJsonRpcManager;
  private final LineaTransactionSelectorConfiguration txSelectorConfiguration;
  private final LineaL1L2BridgeSharedConfiguration l1L2BridgeConfiguration;
  private final LineaProfitabilityConfiguration profitabilityConfiguration;
  private final LineaTracerConfiguration tracerConfiguration;
  private final Optional<HistogramMetrics> maybeProfitabilityMetrics;
  private final BundlePoolService bundlePoolService;
  private final long maxBundleGasPerBlock;
  private final Map<String, Integer> limitsMap;
  private final AtomicReference<LineaTransactionSelector> currSelector = new AtomicReference<>();

  public LineaTransactionSelectorFactory(
      final BlockchainService blockchainService,
      final LineaTransactionSelectorConfiguration txSelectorConfiguration,
      final LineaL1L2BridgeSharedConfiguration l1L2BridgeConfiguration,
      final LineaProfitabilityConfiguration profitabilityConfiguration,
      final LineaTracerConfiguration tracerConfiguration,
      final Map<String, Integer> limitsMap,
      final Optional<JsonRpcManager> rejectedTxJsonRpcManager,
      final Optional<HistogramMetrics> maybeProfitabilityMetrics,
      final BundlePoolService bundlePoolService,
      final long maxBundleGasPerBlock) {
    this.blockchainService = blockchainService;
    this.txSelectorConfiguration = txSelectorConfiguration;
    this.l1L2BridgeConfiguration = l1L2BridgeConfiguration;
    this.profitabilityConfiguration = profitabilityConfiguration;
    this.tracerConfiguration = tracerConfiguration;
    this.limitsMap = limitsMap;
    this.rejectedTxJsonRpcManager = rejectedTxJsonRpcManager;
    this.maybeProfitabilityMetrics = maybeProfitabilityMetrics;
    this.bundlePoolService = bundlePoolService;
    this.maxBundleGasPerBlock = maxBundleGasPerBlock;
  }

  @Override
  public PluginTransactionSelector create(final SelectorsStateManager selectorsStateManager) {
    final var selector =
        new LineaTransactionSelector(
            selectorsStateManager,
            blockchainService,
            txSelectorConfiguration,
            l1L2BridgeConfiguration,
            profitabilityConfiguration,
            tracerConfiguration,
            bundlePoolService,
            limitsMap,
            rejectedTxJsonRpcManager,
            maybeProfitabilityMetrics);
    currSelector.set(selector);
    return selector;
  }

  public void selectPendingTransactions(
      final BlockTransactionSelectionService bts, final ProcessableBlockHeader pendingBlockHeader) {
    final AtomicLong cumulativeBundleGasLimit = new AtomicLong(0L);

    bundlePoolService
        .getBundlesByBlockNumber(pendingBlockHeader.getNumber())
        .forEach(
            bundle -> {
              // mark as "to-evaluate" to prevent eviction during processing:
              bundlePoolService.markBundleForEval(bundle);
              var badBundleRes =
                  bundle.pendingTransactions().stream()
                      .map(
                          pt -> {
                            // restricting block bundles on the basis of gasLimit, not gas
                            // used. gasUsed isn't returned from evaluatePendingTransaction
                            // currently
                            final var pendingGasLimit = pt.getTransaction().getGasLimit();
                            if (pendingGasLimit + cumulativeBundleGasLimit.get()
                                < maxBundleGasPerBlock) {
                              var res = bts.evaluatePendingTransaction(pt);
                              if (res.selected()) {
                                cumulativeBundleGasLimit.addAndGet(pendingGasLimit);
                              }
                              return res;
                            } else {
                              return TransactionSelectionResult.BLOCK_OCCUPANCY_ABOVE_THRESHOLD;
                            }
                          })
                      .filter(evalRes -> !evalRes.selected())
                      .findFirst();
              if (badBundleRes.isPresent()) {
                rollback(bts);
              } else {
                commit(bts);
              }
            });
    currSelector.set(null);
  }

  private void commit(final BlockTransactionSelectionService bts) {
    currSelector.get().getOperationTracer().commitTransactions();
    bts.commit();
  }

  private void rollback(final BlockTransactionSelectionService bts) {
    currSelector.get().getOperationTracer().popTransactions();
    bts.rollback();
  }
}
