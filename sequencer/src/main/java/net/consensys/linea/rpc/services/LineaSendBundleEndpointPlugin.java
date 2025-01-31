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

import com.google.auto.service.AutoService;
import net.consensys.linea.AbstractLineaRequiredPlugin;
import net.consensys.linea.rpc.methods.LineaCancelBundle;
import net.consensys.linea.rpc.methods.LineaSendBundle;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.RpcEndpointService;

@AutoService(BesuPlugin.class)
public class LineaSendBundleEndpointPlugin extends AbstractLineaRequiredPlugin {
  private RpcEndpointService rpcEndpointService;
  private LineaSendBundle lineaSendBundleMethod;
  private LineaCancelBundle lineaCancelBundleMethod;
  private ServiceManager serviceManager;

  /**
   * Register the bundle RPC service.
   *
   * @param serviceManager the ServiceManager to be used.
   */
  @Override
  public void doRegister(final ServiceManager serviceManager) {
    this.serviceManager = serviceManager;
    rpcEndpointService =
        serviceManager
            .getService(RpcEndpointService.class)
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Failed to obtain RpcEndpointService from the ServiceManager."));

    lineaSendBundleMethod = new LineaSendBundle(rpcEndpointService);

    rpcEndpointService.registerRPCEndpoint(
        lineaSendBundleMethod.getNamespace(),
        lineaSendBundleMethod.getName(),
        lineaSendBundleMethod::execute);

    lineaCancelBundleMethod = new LineaCancelBundle(rpcEndpointService);
    rpcEndpointService.registerRPCEndpoint(
        lineaCancelBundleMethod.getNamespace(),
        lineaCancelBundleMethod.getName(),
        lineaCancelBundleMethod::execute);
  }

  /**
   * Starts this plugin and in case the extra data pricing is enabled, as first thing it tries to
   * extract extra data pricing configuration from the chain head, then it starts listening for new
   * imported block, in order to update the extra data pricing on every incoming block.
   */
  @Override
  public void start() {
    super.start();
    final var bundlePool =
        serviceManager
            .getService(BundlePoolService.class)
            .orElseThrow(
                () ->
                    new RuntimeException("Failed to obtain Bundle pool from the ServiceManager."));

    // set the pool
    lineaSendBundleMethod.init(bundlePool);
    // set the pool
    lineaCancelBundleMethod.init(bundlePool);
  }
}
