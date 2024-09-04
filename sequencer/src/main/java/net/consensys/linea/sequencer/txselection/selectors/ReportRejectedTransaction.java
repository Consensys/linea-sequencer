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
package net.consensys.linea.sequencer.txselection.selectors;

import java.net.URI;

import com.google.gson.JsonObject;
import net.consensys.linea.jsonrpc.JsonRpcClient;
import net.consensys.linea.jsonrpc.JsonRpcRequestBuilder;
import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;

/**
 *
 *
 * <pre>
 * {@code linea_saveRejectedTransaction({
 *         "blockNumber": "base 10 number",
 *         "transactionRLP": "transaction as the user sent in eth_sendRawTransaction",
 *         "reasonMessage": "Transaction line count for module ADD=402 is above the limit 70"
 *         "overflows": [{
 *           "module": "ADD",
 *           "count": 402,
 *           "limit": 70
 *         }, {
 *           "module": "MUL",
 *           "count": 587,
 *           "limit": 400
 *         }]
 *     })
 * }
 * </pre>
 */
public class ReportRejectedTransaction {
  static void notifyDiscardedTransaction(
      final TransactionEvaluationContext<? extends PendingTransaction> evaluationContext,
      final TransactionSelectionResult transactionSelectionResult,
      final URI rejectedTxEndpoint) {
    if (!transactionSelectionResult.discard()) {
      return;
    }

    final PendingTransaction pendingTransaction = evaluationContext.getPendingTransaction();
    final ProcessableBlockHeader pendingBlockHeader = evaluationContext.getPendingBlockHeader();

    // Build JSON-RPC request
    final JsonObject params = new JsonObject();
    params.addProperty("blockNumber", pendingBlockHeader.getNumber());
    params.addProperty(
        "transactionRLP", pendingTransaction.getTransaction().encoded().toHexString());
    params.addProperty("reasonMessage", transactionSelectionResult.maybeInvalidReason().orElse(""));

    final String jsonRequest =
        JsonRpcRequestBuilder.buildRequest("linea_saveRejectedTransaction", params, 1);

    // Send JSON-RPC request with retries in a new thread
    JsonRpcClient.sendRequestWithRetries(rejectedTxEndpoint, jsonRequest);
  }
}
