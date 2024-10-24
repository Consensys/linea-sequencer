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

package net.consensys.linea.sequencer.txselection.metrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.BlockHeader;

@Slf4j
public class SelectorProfitabilityMetrics {

  private final Map<Long, Map<Hash, TransactionProfitabilityData>> txProfitabilityDataCacheByBlock =
      new ConcurrentHashMap<>();

  /**
   * Handles the list of transactions by calculating their profitability based on the supplied cache
   * of profitable priority fees.
   *
   * @param blockHeader the block header
   * @param transactions The list of transactions to process
   */
  public void handleNewBlock(
      final BlockHeader blockHeader, List<? extends Transaction> transactions) {

    log.info(
        "New block number {}, txProfitabilityDataCache content: {}",
        blockHeader.getNumber(),
        txProfitabilityDataCacheByBlock);

    purgePreviousBlockFromCache(blockHeader.getNumber());

    // get and remove cached data for this block
    final var txProfitabilityDataCache =
        txProfitabilityDataCacheByBlock.remove(blockHeader.getNumber());

    // if the cache is empty we are not building blocks, so nothing to do
    if (txProfitabilityDataCache != null && !txProfitabilityDataCache.isEmpty()) {
      final Wei baseFee =
          blockHeader
              .getBaseFee()
              .map(Wei::fromQuantity)
              .orElseThrow(() -> new IllegalStateException("Base fee market expected"));
      transactions.forEach(tx -> process(txProfitabilityDataCache, baseFee, tx));
    }
  }

  private void purgePreviousBlockFromCache(final long newBlockNumber) {
    // remove all cached data of previous blocks
    for (final var cachedBlockNumber : txProfitabilityDataCacheByBlock.keySet()) {
      if (cachedBlockNumber < newBlockNumber) {
        txProfitabilityDataCacheByBlock.remove(cachedBlockNumber);
      }
    }
  }

  /**
   * Processes an individual transaction to determine its profitability level.
   *
   * @param txProfitabilityDataCache
   * @param baseFee
   * @param transaction The transaction being processed
   */
  private void process(
      final Map<Hash, TransactionProfitabilityData> txProfitabilityDataCache,
      final Wei baseFee,
      Transaction transaction) {
    final var selectorProfitabilityData = txProfitabilityDataCache.remove(transaction.getHash());
    if (selectorProfitabilityData != null) {
      final var effectivePriorityFee =
          selectorProfitabilityData.effectiveGasPrice.subtract(baseFee);
      final var ratio =
          selectorProfitabilityData.profitablePriorityFee.getValue().doubleValue()
              / effectivePriorityFee.getValue().doubleValue();
      log.info(
          "Tx {} profitability data found {}, baseFee {}, effectivePayingPriorityFee {}, ratio (calculatedProfitablePriorityFee/effectivePayingPriorityFee) {}",
          transaction.getHash(),
          selectorProfitabilityData,
          baseFee.toHumanReadableString(),
          effectivePriorityFee.toHumanReadableString(),
          ratio);
    } else {
      log.info("Cached profitability data not found for tx {}", transaction.getHash());
    }
  }

  public void remember(
      final long blockNumber,
      final Hash hash,
      final Wei transactionGasPrice,
      final Wei profitablePriorityFeePerGas) {
    txProfitabilityDataCacheByBlock
        .computeIfAbsent(blockNumber, unused -> new HashMap<>())
        .put(
            hash,
            new TransactionProfitabilityData(transactionGasPrice, profitablePriorityFeePerGas));
  }

  record TransactionProfitabilityData(Wei effectiveGasPrice, Wei profitablePriorityFee) {
    @Override
    public String toString() {
      return "{"
          + "effectivePaidGasPrice="
          + effectiveGasPrice.toHumanReadableString()
          + ", calculatedProfitablePriorityFee="
          + profitablePriorityFee.toHumanReadableString()
          + '}';
    }
  }
}
