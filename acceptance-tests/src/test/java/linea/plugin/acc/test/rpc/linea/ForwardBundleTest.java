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
package linea.plugin.acc.test.rpc.linea;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import linea.plugin.acc.test.TestCommandLineOptionsBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.account.TransferTransaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@WireMockTest
public class ForwardBundleTest extends AbstractSendBundleTest {
  private static WireMockRuntimeInfo wireMockRuntimeInfo;

  @BeforeAll
  public static void beforeAll(final WireMockRuntimeInfo wireMockRuntimeInfo) {
    ForwardBundleTest.wireMockRuntimeInfo = wireMockRuntimeInfo;
  }

  @Override
  public List<String> getTestCliOptions() {
    return new TestCommandLineOptionsBuilder()
        .set("--plugin-linea-bundles-forward-urls=", wireMockRuntimeInfo.getHttpBaseUrl())
        .build();
  }

  @Test
  public void bundleIsForwarded() {
    final var bundleParams = sendBundle(1, 1);
    stubSuccessResponseFor(1);
    verifyRequestForwarded(bundleParams);
  }

  private static void stubSuccessResponseFor(final int blockNumber) {
    stubFor(
        post(urlEqualTo("/"))
            .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
            .withRequestBody(matchingBlockNumber(blockNumber))
            .willReturn(
                aResponse()
                    .withTransformers("response-template")
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                            {
                              "jsonrpc": "2.0",
                              "result": {
                                "bundleHash": "<bundleHash>"
                              },
                              "id": {{jsonPath request.body '$.id'}}
                            }"""
                            .replace("<bundleHash>", "0xb" + Integer.toHexString(blockNumber)))));
  }

  private static StringValuePattern matchingBlockNumber(final long blockNumber) {
    return matchingJsonPath("$.params[?(@.blockNumber == %d)]".formatted(blockNumber));
  }

  private static void verifyRequestForwarded(final BundleParams bundleParams) {
    await()
        .atMost(2, SECONDS)
        .untilAsserted(
            () ->
                verify(
                    exactly(1),
                    postRequestedFor(urlEqualTo("/"))
                        .withHeader("Content-Type", equalTo("application/json; charset=UTF-8"))
                        .withRequestBody(matchingBundleParams(bundleParams))));
  }

  private static StringValuePattern matchingBundleParams(final BundleParams bundleParams) {
    return matchingJsonPath(
            "$.params[?(@.blockNumber == %s)]".formatted(bundleParams.blockNumber()))
        .and(
            matchingJsonPath(
                "$.params[?(@.txs == [%s])]"
                    .formatted(
                        Arrays.stream(bundleParams.txs()).collect(Collectors.joining(",")))));
  }

  private BundleParams sendBundle(final int blockNumber, final int amount) {
    final Account sender = accounts.getSecondaryBenefactor();
    final Account recipient = accounts.getPrimaryBenefactor();

    final TransferTransaction tx = accountTransactions.createTransfer(sender, recipient, amount);

    final String bundleRawTx = tx.signedTransactionData();

    final var bundleParams =
        new BundleParams(new String[] {bundleRawTx}, Integer.toHexString(blockNumber));

    final var sendBundleRequest = new SendBundleRequest(bundleParams);
    final var sendBundleResponse = sendBundleRequest.execute(minerNode.nodeRequests());

    assertThat(sendBundleResponse.hasError()).isFalse();
    assertThat(sendBundleResponse.getResult().bundleHash()).isNotBlank();

    return bundleParams;
  }
}
