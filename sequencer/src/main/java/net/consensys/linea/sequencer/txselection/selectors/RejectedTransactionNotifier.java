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
import java.time.Instant;

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
 * {@code linea_saveRejectedTransactionV1({
 *         "txRejectionStage": "SEQUENCER/RPC/P2P",
 *         "timestamp": "2024-08-22T09:18:51Z", # ISO8601 UTC+0 when tx was rejected by node, usefull if P2P edge node.
 *         "blockNumber": "base 10 number",
 *         "transactionRLP": "transaction as the user sent in eth_sendRawTransaction",
 *         "reason": "Transaction line count for module ADD=402 is above the limit 70"
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
public class RejectedTransactionNotifier {

  static void notifyDiscardedTransactionAsync(
      final TransactionEvaluationContext<? extends PendingTransaction> evaluationContext,
      final TransactionSelectionResult transactionSelectionResult,
      final Instant timestamp,
      final URI rejectedTxEndpoint) {
    if (!transactionSelectionResult.discard()) {
      return;
    }

    final PendingTransaction pendingTransaction = evaluationContext.getPendingTransaction();
    final ProcessableBlockHeader pendingBlockHeader = evaluationContext.getPendingBlockHeader();

    // Build JSON-RPC request
    final JsonObject params = new JsonObject();
    params.addProperty("txRejectionStage", "SEQUENCER");
    params.addProperty("timestamp", timestamp.toString());
    params.addProperty("blockNumber", pendingBlockHeader.getNumber());
    params.addProperty(
        "transactionRLP", pendingTransaction.getTransaction().encoded().toHexString());
    params.addProperty("reasonMessage", transactionSelectionResult.maybeInvalidReason().orElse(""));

    final String jsonRequest =
        JsonRpcRequestBuilder.buildRequest("linea_saveRejectedTransactionV1", params, 1);

    // Send JSON-RPC request with retries in a new thread
    JsonRpcClient.sendRequestWithRetries(rejectedTxEndpoint, jsonRequest);
  }
}
