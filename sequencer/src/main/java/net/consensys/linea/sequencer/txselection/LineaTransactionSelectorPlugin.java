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

import static net.consensys.linea.metrics.LineaMetricCategory.SEQUENCER_PROFITABILITY;
import static net.consensys.linea.sequencer.modulelimit.ModuleLineCountValidator.createLimitModules;

import java.util.Optional;

import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;
import net.consensys.linea.AbstractLineaRequiredPlugin;
import net.consensys.linea.config.LineaRejectedTxReportingConfiguration;
import net.consensys.linea.config.LineaTransactionSelectorConfiguration;
import net.consensys.linea.jsonrpc.JsonRpcManager;
import net.consensys.linea.metrics.HistogramMetrics;
import net.consensys.linea.sequencer.txselection.selectors.ProfitableTransactionSelector;
import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.BesuService;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelectorFactory;

/**
 * This class extends the default transaction selection rules used by Besu. It leverages the
 * TransactionSelectionService to manage and customize the process of transaction selection. This
 * includes setting limits such as 'TraceLineLimit', 'maxBlockGas', and 'maxCallData'.
 */
@Slf4j
@AutoService(BesuPlugin.class)
public class LineaTransactionSelectorPlugin extends AbstractLineaRequiredPlugin {
  public static final String NAME = "linea";
  private ServiceManager serviceManager;
  private TransactionSelectionService transactionSelectionService;
  private Optional<JsonRpcManager> rejectedTxJsonRpcManager = Optional.empty();
  private BesuConfiguration besuConfiguration;

  @Override
  public void doRegister(final ServiceManager serviceManager) {
    this.serviceManager = serviceManager;
    transactionSelectionService =
        serviceManager
            .getService(TransactionSelectionService.class)
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Failed to obtain TransactionSelectionService from the ServiceManager."));

    besuConfiguration =
        serviceManager
            .getService(BesuConfiguration.class)
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Failed to obtain BesuConfiguration from the ServiceManager."));
    metricCategoryRegistry.addMetricCategory(SEQUENCER_PROFITABILITY);
  }

  @Override
  public void start() {
    super.start();

    final LineaTransactionSelectorConfiguration txSelectorConfiguration =
        transactionSelectorConfiguration();
    final LineaRejectedTxReportingConfiguration lineaRejectedTxReportingConfiguration =
        rejectedTxReportingConfiguration();
    rejectedTxJsonRpcManager =
        Optional.ofNullable(lineaRejectedTxReportingConfiguration.rejectedTxEndpoint())
            .map(
                endpoint ->
                    new JsonRpcManager(
                            "linea-tx-selector-plugin",
                            besuConfiguration.getDataPath(),
                            lineaRejectedTxReportingConfiguration)
                        .start());

    final Optional<HistogramMetrics> maybeProfitabilityMetrics =
        metricCategoryRegistry.isMetricCategoryEnabled(SEQUENCER_PROFITABILITY)
            ? Optional.of(
                new HistogramMetrics(
                    metricsSystem,
                    SEQUENCER_PROFITABILITY,
                    "ratio",
                    "sequencer profitability ratio",
                    profitabilityConfiguration().profitabilityMetricsBuckets(),
                    ProfitableTransactionSelector.Phase.class))
            : Optional.empty();

    transactionSelectionService.registerPluginTransactionSelectorFactory(
        new LineaTransactionSelectorFactory(
            blockchainService,
            txSelectorConfiguration,
            l1L2BridgeSharedConfiguration(),
            profitabilityConfiguration(),
            tracerConfiguration(),
            createLimitModules(tracerConfiguration()),
            rejectedTxJsonRpcManager,
            maybeProfitabilityMetrics,
            // TODO: plumb LineaLimitedBundlePool
            Optional.empty()));
  }

  @Override
  public void stop() {
    super.stop();
    rejectedTxJsonRpcManager.ifPresent(JsonRpcManager::shutdown);
  }

  /**
   * I am confusingly named. For the uninitiated, how would one differentiate the purpose of
   * TransactionSelectionService versus BlockTransactionSelectionService? Perhaps it was the
   * original creation/naming of TransactionSelectionService that confused things.
   *
   * <p>This is a bit circuitous. BTS has a transactionSelectorService, which creates a
   * PluginTransactionSelector via a factory. The factory in this case is _this_ outer class, loaded
   * via the plugin system. BTS passes a reference to itself into selectPendingTransactions, so we
   * can call evaluatePendingTransaction for each tx in a bundle, and finally commit/rollback
   *
   * <p>this interface will be in upstream besu eventually.
   *
   * <p>TODO: remove me.
   */
  public interface BlockTransactionSelectionService extends BesuService {

    TransactionSelectionResult evaluatePendingTransaction(PendingTransaction pendingTransaction);

    boolean commit();

    void rollback();
  }

  public interface SelectorsStateManager {
    /**
     * Will be added in the upstream plugin services api.
     *
     * <p>TODO: remove me.
     */
  }

  /**
   * updated upstream interface, normally found in
   * org.hyperledger.besu.plugin.services.TransactionSelectionService
   *
   * <p>I am extended beyond being a provider/factory for PluginTransactionSelector, to also provide
   * an entry point for adding pending transactions to the block
   *
   * <p>TODO: remove me
   */
  public interface TransactionSelectionService extends BesuService {
    PluginTransactionSelector createPluginTransactionSelector(
        SelectorsStateManager selectorsStateManager);

    void selectPendingTransactions(
        BlockTransactionSelectionService blockTransactionSelectionService,
        final ProcessableBlockHeader pendingBlockHeader);

    void registerPluginTransactionSelectorFactory(
        PluginTransactionSelectorFactory transactionSelectorFactory);
  }

  /**
   * updated in upstream besu.
   *
   * <p>TODO: remove me.
   */
  public interface PluginTransactionSelectorFactory {

    default PluginTransactionSelector create(final SelectorsStateManager selectorsStateManager) {
      return PluginTransactionSelector.ACCEPT_ALL;
    }

    default void selectPendingTransactions(
        final BlockTransactionSelectionService blockTransactionSelectionService,
        final ProcessableBlockHeader pendingBlockHeader) {}
  }
}
