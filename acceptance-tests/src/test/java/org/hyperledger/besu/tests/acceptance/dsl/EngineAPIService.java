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
import java.util.Collections;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EngineForkchoiceUpdatedParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadAttributesParameter;
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
    this.node = node;
    this.ethTransactions = ethTransactions;
    this.blockTimestamp = startingBlocktimestamp;

    mapper = new ObjectMapper();
    // Ensure correct serialization of custom types used in Besu
    SimpleModule customTypesModule = new SimpleModule();
    registerToHexStringSerializer(customTypesModule, Hash.class);
    registerToHexStringSerializer(customTypesModule, Bytes32.class);
    registerToHexStringSerializer(customTypesModule, Address.class);
    mapper.registerModule(customTypesModule);
  }

  private static <T> void registerToHexStringSerializer(SimpleModule module, Class<T> type) {
    module.addSerializer(
        type,
        new JsonSerializer<T>() {
          @Override
          public void serialize(T value, JsonGenerator gen, SerializerProvider serializers)
              throws IOException {
            try {
              String hex = (String) value.getClass().getMethod("toHexString").invoke(value);
              gen.writeString(hex);
            } catch (Exception e) {
              throw new RuntimeException("Failed to call toHexString() on " + value.getClass(), e);
            }
          }
        });
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
   * Add validated block to blockchain head.
   */
  public void buildNewBlock() throws IOException {
    final EthBlock.Block block = node.execute(ethTransactions.block());
    this.blockTimestamp += 1;

    final Call buildBlockRequest = createForkChoiceRequest(block.getHash(), this.blockTimestamp);

    final String payloadId;
    try (final Response buildBlockResponse = buildBlockRequest.execute()) {
      // We would like to deserialize directly into Besu native types. However neither
      // EngineUpdateForkchoiceResult and JsonRpcSuccessResponse classes have a constructor tagged
      // with @JsonCreator nor a default constructor. We will need to write DTO classes to avoid
      // manual JSON parsing.
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
        createNewPayloadRequest(executionPayload, parentBeaconBlockRoot, executionRequests);

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

    // Construct the first param - EngineForkchoiceUpdatedParameter
    ArrayNode params = mapper.createArrayNode();
    EngineForkchoiceUpdatedParameter engineForkchoiceUpdatedParameter =
        new EngineForkchoiceUpdatedParameter(
            Hash.fromHexString(parentBlockHash),
            Hash.fromHexString(parentBlockHash),
            Hash.fromHexString(parentBlockHash));
    params.add(mapper.valueToTree(engineForkchoiceUpdatedParameter));

    // Optionally construct the second param - EnginePayloadAttributesParameter
    if (maybeTimeStamp.isPresent()) {
      EnginePayloadAttributesParameter payloadAttributes =
          new EnginePayloadAttributesParameter(
              String.valueOf(timeStamp),
              Hash.ZERO.toHexString(),
              SUGGESTED_BLOCK_FEE_RECIPIENT,
              Collections.emptyList(),
              Hash.ZERO.toHexString());
      params.add(mapper.valueToTree(payloadAttributes));
    }
    return createEngineCall("engine_forkchoiceUpdatedV3", params);
  }

  private Call createGetPayloadRequest(final String payloadId) {
    ArrayNode params = mapper.createArrayNode();
    params.add(payloadId);
    return createEngineCall("engine_getPayloadV4", params);
  }

  private Call createNewPayloadRequest(
      final ObjectNode executionPayload,
      final String parentBeaconBlockRoot,
      final ArrayNode executionRequests) {
    ArrayNode params = mapper.createArrayNode();
    params.add(executionPayload);
    params.add(mapper.createArrayNode()); // empty withdrawals
    params.add(parentBeaconBlockRoot);
    params.add(executionRequests);

    return createEngineCall("engine_newPayloadV4", params);
  }

  private Call createEngineCall(final String rpcMethod, ArrayNode params) {
    ObjectNode request = mapper.createObjectNode();
    request.put("jsonrpc", JSONRPC_VERSION);
    request.put("method", rpcMethod);
    request.set("params", params);
    request.put("id", JSONRPC_REQUEST_ID);

    String requestString;
    try {
      requestString = mapper.writeValueAsString(request);
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to serialize JSON-RPC request for method " + rpcMethod + ":", e);
    }

    return httpClient.newCall(
        new Request.Builder()
            .url(node.engineRpcUrl().get())
            .post(
                RequestBody.create(
                    requestString, MediaType.parse("application/json; charset=utf-8")))
            .build());
  }
}
