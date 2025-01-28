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
package net.consensys.linea.rpc.methods;

import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;
import net.consensys.linea.rpc.methods.parameters.BundleParameter;
import net.consensys.linea.rpc.services.LineaLimitedBundlePool;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.plugin.services.RpcEndpointService;
import org.hyperledger.besu.plugin.services.exception.PluginRpcEndpointException;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;
import org.hyperledger.besu.plugin.services.rpc.RpcMethodError;

@Slf4j
public class LineaSendBundle {
  private static final AtomicInteger LOG_SEQUENCE = new AtomicInteger();
  private final JsonRpcParameter parameterParser = new JsonRpcParameter();
  private final RpcEndpointService rpcEndpointService;
  private final LineaLimitedBundlePool bundlePool;

  public LineaSendBundle(
      final RpcEndpointService rpcEndpointService, LineaLimitedBundlePool bundlePool) {
    this.rpcEndpointService = rpcEndpointService;
    this.bundlePool = bundlePool;
  }

  public String getNamespace() {
    return "linea";
  }

  public String getName() {
    return "sendBundle";
  }

  public BundleResponse execute(final PluginRpcRequest request) {
    // sequence id for correlating error messages in logs:
    final int logId = log.isDebugEnabled() ? LOG_SEQUENCE.incrementAndGet() : -1;

    try {
      final var bundleParams = parseRequest(logId, request.getParams());

      // TODO: pre-validate the bundle and add to the bundle pool.

      return new BundleResponse(Bytes32.random());
    } catch (final Exception e) {
      throw new PluginRpcEndpointException(new LineaSendBundleError(e.getMessage()));
    }
  }

  private BundleParameter parseRequest(final int logId, final Object[] params) {
    try {
      BundleParameter param = parameterParser.required(params, 0, BundleParameter.class);
      return param;
    } catch (Exception e) {
      log.atError()
          .setMessage("[{}] failed to parse linea_sendBundle request")
          .addArgument(logId)
          .setCause(e)
          .log();
      throw new RuntimeException(e);
    }
  }

  public record BundleResponse(Bytes32 bundleHash) {}

  class LineaSendBundleError implements RpcMethodError {

    final String errMessage;

    LineaSendBundleError(String errMessage) {
      this.errMessage = errMessage;
    }

    @Override
    public int getCode() {
      return INVALID_PARAMS_ERROR_CODE;
    }

    @Override
    public String getMessage() {
      return errMessage;
    }
  }
}
