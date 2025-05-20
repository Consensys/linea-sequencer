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

package org.hyperledger.besu.tests.acceptance.dsl;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.eth.EthTransactions;
import org.web3j.protocol.core.methods.response.EthBlock;

/*
 * Inspired by PragueAcceptanceTestHelper class in Besu codebase. We use this class to
 * emulate Engine API calls to the Besu Node, so that we can run tests for post-merge EVM forks.
 */
public class EngineAPIService {
  private long blockTimestamp;
  private final OkHttpClient httpClient;
  private final ObjectMapper mapper;
  private final BesuNode node;
  private final EthTransactions ethTransactions;

  private static String JSONRPC_VERSION = "2.0";
  private static long JSONRPC_REQUEST_ID = 67;
  private static String SUGGESTED_BLOCK_FEE_RECIPIENT =
      "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b";

  public EngineAPIService(
      BesuNode node, EthTransactions ethTransactions, long startingBlocktimestamp) {
    httpClient = new OkHttpClient();
    mapper = new ObjectMapper();
    this.node = node;
    this.ethTransactions = ethTransactions;
    this.blockTimestamp = startingBlocktimestamp;
  }

  /*
   * See https://hackmd.io/@danielrachi/engine_api
   *
   * The flow to build a block with the Engine API is as follows:
   * 1. Send engine_forkchoiceUpdated(EngineForkchoiceUpdatedParameter, EnginePayloadAttributesParameter) request to Besu node
   * 2. Besu node responds with payloadId
   * The Besu Node will start building a proposed block
   *
   * 3. Send engine_getPayload(payloadId) request to Besu node
   * 4. Besu node responds with executionPayload
   * Get the proposed block from the Besu node
   *
   * 5. Send engine_newPayload request to Besu node
   * Validate the proposed block. Then store the validated block for future reference.
   * Unsure why the proposed block is not stored in the previous steps where it was built.
   *
   * 6. Send engine_forkchoiceUpdated(EngineForkchoiceUpdatedParameter) request to Besu node
   * Add new block to blockchain head.
   */
  public void buildNewBlock() throws IOException {
    final EthBlock.Block block = node.execute(ethTransactions.block());

    this.blockTimestamp += 1;
    final Call buildBlockRequest = createForkChoiceRequest(block.getHash(), this.blockTimestamp);

    final String payloadId;
    try (final Response buildBlockResponse = buildBlockRequest.execute()) {
      payloadId =
          mapper
              .readTree(buildBlockResponse.body().string())
              .get("result")
              .get("payloadId")
              .asText();

      assertThat(payloadId).isNotEmpty();
    }

    WaitUtils.sleep(500);

    final Call getPayloadRequest = createGetPayloadRequest(payloadId);

    final ObjectNode executionPayload;
    final ArrayNode executionRequests;
    final String newBlockHash;
    final String parentBeaconBlockRoot;
    try (final Response getPayloadResponse = getPayloadRequest.execute()) {
      assertThat(getPayloadResponse.code()).isEqualTo(200);

      JsonNode result = mapper.readTree(getPayloadResponse.body().string()).get("result");
      executionPayload = (ObjectNode) result.get("executionPayload");
      executionRequests = (ArrayNode) result.get("executionRequests");

      newBlockHash = executionPayload.get("blockHash").asText();
      parentBeaconBlockRoot = executionPayload.remove("parentBeaconBlockRoot").asText();

      assertThat(newBlockHash).isNotEmpty();
    }

    final Call newPayloadRequest =
        createNewPayloadRequest(
            executionPayload.toString(), parentBeaconBlockRoot, executionRequests.toString());
    try (final Response newPayloadResponse = newPayloadRequest.execute()) {
      assertThat(newPayloadResponse.code()).isEqualTo(200);

      final String responseStatus =
          mapper.readTree(newPayloadResponse.body().string()).get("result").get("status").asText();
      assertThat(responseStatus).isEqualTo("VALID");
    }

    final Call moveChainAheadRequest = createForkChoiceRequest(newBlockHash);

    try (final Response moveChainAheadResponse = moveChainAheadRequest.execute()) {
      assertThat(moveChainAheadResponse.code()).isEqualTo(200);
    }
  }

  private Call createForkChoiceRequest(final String blockHash) {
    return createForkChoiceRequest(blockHash, null);
  }

  private Call createForkChoiceRequest(final String parentBlockHash, final Long timeStamp) {
    final Optional<Long> maybeTimeStamp = Optional.ofNullable(timeStamp);

    ObjectNode request = mapper.createObjectNode();
    request.put("jsonrpc", JSONRPC_VERSION);
    request.put("method", "engine_forkchoiceUpdatedV3");

    // Construct the first param - EngineForkchoiceUpdatedParameter
    ArrayNode params = mapper.createArrayNode();
    ObjectNode forkchoiceState = mapper.createObjectNode();
    forkchoiceState.put("headBlockHash", parentBlockHash);
    forkchoiceState.put("safeBlockHash", parentBlockHash);
    forkchoiceState.put("finalizedBlockHash", parentBlockHash);
    params.add(forkchoiceState);

    // Optionally construct the second param - EnginePayloadAttributesParameter
    if (maybeTimeStamp.isPresent()) {
      ObjectNode payloadAttributes = mapper.createObjectNode();
      payloadAttributes.put("timestamp", timeStamp);
      payloadAttributes.put("prevRandao", Hash.ZERO.toString());
      payloadAttributes.put("suggestedFeeRecipient", SUGGESTED_BLOCK_FEE_RECIPIENT);
      payloadAttributes.set("withdrawals", mapper.createArrayNode());
      payloadAttributes.put("parentBeaconBlockRoot", Hash.ZERO.toString());
      params.add(payloadAttributes);
    }

    request.set("params", params);
    request.put("id", JSONRPC_REQUEST_ID);

    String requestString;
    try {
      requestString = mapper.writeValueAsString(request);
    } catch (Exception e) {
      throw new RuntimeException("Failed to build engine_forkchoiceUpdatedV3 request", e);
    }
    return createEngineCall(requestString);
  }

  private Call createGetPayloadRequest(final String payloadId) {
    ObjectNode request = mapper.createObjectNode();
    request.put("jsonrpc", JSONRPC_VERSION);
    request.put("method", "engine_getPayloadV4");
    ArrayNode params = mapper.createArrayNode();
    params.add(payloadId);
    request.set("params", params);
    request.put("id", JSONRPC_REQUEST_ID);

    String requestString;
    try {
      requestString = mapper.writeValueAsString(request);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize engine_getPayloadV4 request", e);
    }
    return createEngineCall(requestString);
  }

  private Call createNewPayloadRequest(
      final String executionPayload,
      final String parentBeaconBlockRoot,
      final String executionRequests) {
    // Parse executionPayload and executionRequests as JSON nodes
    ObjectNode executionPayloadNode;
    ArrayNode executionRequestsNode;
    try {
      executionPayloadNode = (ObjectNode) mapper.readTree(executionPayload);
      executionRequestsNode = (ArrayNode) mapper.readTree(executionRequests);
    } catch (Exception e) {
      throw new RuntimeException("Invalid JSON input", e);
    }

    ObjectNode request = mapper.createObjectNode();
    request.put("jsonrpc", JSONRPC_VERSION);
    request.put("method", "engine_newPayloadV4");

    ArrayNode params = mapper.createArrayNode();
    params.add(executionPayloadNode);
    params.add(mapper.createArrayNode()); // empty withdrawals
    params.add(parentBeaconBlockRoot);
    params.add(executionRequestsNode);

    request.set("params", params);
    request.put("id", JSONRPC_REQUEST_ID);

    String requestString;
    try {
      requestString = mapper.writeValueAsString(request);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize engine_newPayloadV4 request", e);
    }
    return createEngineCall(requestString);
  }

  private Call createEngineCall(final String request) {
    return httpClient.newCall(
        new Request.Builder()
            .url(node.engineRpcUrl().get())
            .post(RequestBody.create(request, MediaType.parse("application/json; charset=utf-8")))
            .build());
  }
}
