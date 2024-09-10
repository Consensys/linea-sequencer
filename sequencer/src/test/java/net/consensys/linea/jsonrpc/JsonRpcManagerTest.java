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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import net.consensys.linea.sequencer.txselection.selectors.TestTransactionEvaluationContext;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@WireMockTest
@ExtendWith(MockitoExtension.class)
class JsonRpcManagerTest {
  @TempDir private Path tempDataDir;
  private JsonRpcManager jsonRpcManager;

  @Mock private PendingTransaction pendingTransaction;
  @Mock private ProcessableBlockHeader pendingBlockHeader;
  @Mock private Transaction transaction;

  @BeforeEach
  void init(final WireMockRuntimeInfo wmInfo) {
    jsonRpcManager = new JsonRpcManager(tempDataDir, URI.create(wmInfo.getHttpBaseUrl()));
    jsonRpcManager.start();
  }

  @AfterEach
  void cleanup() {
    jsonRpcManager.shutdown();
  }

  @Test
  void rejectedTxIsReported() throws InterruptedException {
    // mock stubbing
    when(pendingBlockHeader.getNumber()).thenReturn(1L);
    when(pendingTransaction.getTransaction()).thenReturn(transaction);
    final Bytes randomEncodedBytes = Bytes.random(32);
    when(transaction.encoded()).thenReturn(randomEncodedBytes);

    // json-rpc stubbing
    stubFor(
        post(urlEqualTo("/"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"jsonrpc\":\"2.0\",\"result\":{ \"status\": \"SAVED\"},\"id\":1}")));

    final TestTransactionEvaluationContext context =
        new TestTransactionEvaluationContext(pendingTransaction)
            .setPendingBlockHeader(pendingBlockHeader);
    final TransactionSelectionResult result = TransactionSelectionResult.invalid("test");
    final Instant timestamp = Instant.now();

    // method under test
    final String jsonRpcCall =
        JsonRpcRequestBuilder.buildRejectedTxRequest(context, result, timestamp);
    jsonRpcManager.submitNewJsonRpcCall(jsonRpcCall);

    // sleep a bit to allow async processing
    Thread.sleep(1000);

    // assert that the expected json-rpc request was sent to WireMock
    verify(postRequestedFor(urlEqualTo("/")).withRequestBody(equalToJson(jsonRpcCall)));
  }
}
