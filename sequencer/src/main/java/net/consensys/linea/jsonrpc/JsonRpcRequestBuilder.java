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

package net.consensys.linea.jsonrpc;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonObject;
import net.consensys.linea.config.LineaNodeType;
import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.txselection.TransactionEvaluationContext;

/**
 * Helper class to build JSON-RPC requests for rejected transactions.
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
public class JsonRpcRequestBuilder {
  private static final AtomicLong idCounter = new AtomicLong(1);

  public static String buildRejectedTxRequest(
      final TransactionEvaluationContext<? extends PendingTransaction> evaluationContext,
      final TransactionSelectionResult transactionSelectionResult,
      final Instant timestamp) {
    final PendingTransaction pendingTransaction = evaluationContext.getPendingTransaction();
    final ProcessableBlockHeader pendingBlockHeader = evaluationContext.getPendingBlockHeader();
    return buildRejectedTxRequest(
        LineaNodeType.SEQUENCER,
        pendingTransaction.getTransaction(),
        timestamp,
        Optional.of(pendingBlockHeader.getNumber()),
        transactionSelectionResult.maybeInvalidReason().orElse(""));
  }

  public static String buildRejectedTxRequest(
      final LineaNodeType lineaNodeType,
      final Transaction transaction,
      final Instant timestamp,
      final Optional<Long> blockNumber,
      final String reasonMessage) {
    final JsonObject params = new JsonObject();
    params.addProperty("txRejectionStage", lineaNodeType.name());
    params.addProperty("timestamp", timestamp.toString());
    blockNumber.ifPresent(number -> params.addProperty("blockNumber", number));
    params.addProperty("transactionRLP", transaction.encoded().toHexString());
    params.addProperty("reasonMessage", reasonMessage);
    // TODO: overflows

    final JsonObject request = new JsonObject();
    request.addProperty("jsonrpc", "2.0");
    request.addProperty("method", "linea_saveRejectedTransactionV1");
    request.add("params", params);
    request.addProperty("id", idCounter.getAndIncrement());
    return request.toString();
  }
}
