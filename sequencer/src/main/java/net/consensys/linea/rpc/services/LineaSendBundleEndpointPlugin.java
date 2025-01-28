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

package net.consensys.linea.rpc.services;

import net.consensys.linea.AbstractLineaRequiredPlugin;
import net.consensys.linea.rpc.methods.LineaSendBundle;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.RpcEndpointService;

public class LineaSendBundleEndpointPlugin extends AbstractLineaRequiredPlugin {
  private RpcEndpointService rpcEndpointService;
  private LineaSendBundle lineaSendBundleMethod;

  // TODO: rational default?
  private final long DEFAULT_MAX_POOL_SIZE_IN_MB = 4L;

  /**
   * Register the bundle RPC service.
   *
   * @param serviceManager the ServiceManager to be used.
   */
  @Override
  public void doRegister(final ServiceManager serviceManager) {

    rpcEndpointService =
        serviceManager
            .getService(RpcEndpointService.class)
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Failed to obtain RpcEndpointService from the ServiceManager."));

    var lineaBundlePool = new LineaLimitedBundlePool(DEFAULT_MAX_POOL_SIZE_IN_MB * 1024 * 1024);

    lineaSendBundleMethod = new LineaSendBundle(rpcEndpointService, lineaBundlePool);

    rpcEndpointService.registerRPCEndpoint(
        lineaSendBundleMethod.getNamespace(),
        lineaSendBundleMethod.getName(),
        lineaSendBundleMethod::execute);
  }
}
