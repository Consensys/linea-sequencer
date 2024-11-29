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

package net.consensys.linea.rpc.capture;

import java.util.Optional;

import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;
import net.consensys.linea.AbstractLineaRequiredPlugin;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.RpcEndpointService;

/**
 * Registers RPC endpoints .This class provides an RPC endpoint named
 * 'generateConflatedTracesToFileV0' under the 'rollup' namespace. It uses {@link CaptureToFile} to
 * generate conflated file traces. This class provides an RPC endpoint named
 * 'generateConflatedTracesToFileV0' under the 'rollup' namespace.
 */
@AutoService(BesuPlugin.class)
@Slf4j
public class CaptureEndpointServicePlugin extends AbstractLineaRequiredPlugin {

  /**
   * Register the RPC service.
   *
   * @param serviceManager the ServiceManager to be used.
   */
  @Override
  public void doRegister(final ServiceManager serviceManager) {
    CaptureToFile method = new CaptureToFile(serviceManager);

    Optional<RpcEndpointService> service = serviceManager.getService(RpcEndpointService.class);
    createAndRegister(
        method,
        service.orElseThrow(
            () ->
                new RuntimeException(
                    "Failed to obtain RpcEndpointService from the ServiceManager.")));
  }

  /**
   * Create and register the RPC service.
   *
   * @param method the RollupGenerateConflatedTracesToFileV0 method to be used.
   * @param rpcEndpointService the RpcEndpointService to be registered.
   */
  private void createAndRegister(
      final CaptureToFile method, final RpcEndpointService rpcEndpointService) {
    rpcEndpointService.registerRPCEndpoint(
        method.getNamespace(), method.getName(), method::execute);
  }
}
