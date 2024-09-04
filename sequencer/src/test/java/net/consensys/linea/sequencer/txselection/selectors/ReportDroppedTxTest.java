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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@WireMockTest
@ExtendWith(MockitoExtension.class)
class ReportDroppedTxTest {
  @Mock PendingTransaction pendingTransaction;
  @Mock ProcessableBlockHeader pendingBlockHeader;
  @Mock Transaction transaction;

  @Test
  void droppedTxIsReported(final WireMockRuntimeInfo wmInfo) throws Exception {
    when(pendingBlockHeader.getNumber()).thenReturn(1L);
    when(pendingTransaction.getTransaction()).thenReturn(transaction);
    Bytes randomEncodedBytes = Bytes.random(32);
    when(transaction.encoded()).thenReturn(randomEncodedBytes);

    // json-rpc stubbing
    stubFor(
        post(urlEqualTo("/"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"jsonrpc\":\"2.0\",\"result\":true,\"id\":1}")));

    TestTransactionEvaluationContext context =
        new TestTransactionEvaluationContext(pendingTransaction)
            .setPendingBlockHeader(pendingBlockHeader);
    TransactionSelectionResult result = TransactionSelectionResult.invalid("test");

    ReportRejectedTransaction.notifyDiscardedTransaction(
        context, result, URI.create(wmInfo.getHttpBaseUrl()));

    // assert wiremock was called
    Thread.sleep(1000);

    verify(
        postRequestedFor(urlEqualTo("/"))
            .withRequestBody(
                equalToJson(
                    "{\"jsonrpc\":\"2.0\",\"method\":\"linea_saveRejectedTransaction\",\"params\":{\"blockNumber\":1,\"transactionRLP\":\""
                        + randomEncodedBytes.toHexString()
                        + "\",\"reasonMessage\":\"test\"},\"id\":1}")));
  }
}
