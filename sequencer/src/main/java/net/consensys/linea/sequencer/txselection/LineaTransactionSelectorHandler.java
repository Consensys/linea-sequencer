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

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;

public class LineaTransactionSelectorHandler {
  /**
   * Handles the list of transactions by calculating their profitability
   * based on the supplied cache of profitable priority fees.
   *
   * @param profitablePriorityFeeCache Maps transaction hash to profitable priority fee
   * @param transactions The list of transactions to process
   */
  public void handle(Map<Hash, Wei> profitablePriorityFeeCache, List<? extends Transaction> transactions) {
    transactions.forEach(transaction ->
      process(transaction, profitablePriorityFeeCache)
    );
  }

  /**
   * Processes an individual transaction to determine its profitability level.
   *
   * @param transaction The transaction being processed
   * @param profitablePriorityFeeCache Maps transaction hash to its corresponding profitability priority fee
   */
  private void process(Transaction transaction, Map<Hash, Wei> profitablePriorityFeeCache) {
    var defaultPriorityFee = Wei.of(BigInteger.ONE);
    var priorityFee = transaction.getMaxPriorityFeePerGas()
      .map(quantity -> Wei.of(quantity.getAsBigInteger()))
      .orElse(defaultPriorityFee);
    var profitabilityLevel = profitablePriorityFeeCache.get(transaction.getHash()).divide(priorityFee);
  }
}
