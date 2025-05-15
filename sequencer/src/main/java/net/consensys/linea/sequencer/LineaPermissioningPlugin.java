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

package net.consensys.linea.sequencer;

import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;
import net.consensys.linea.AbstractLineaRequiredPlugin;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.PermissioningService;

/**
 * This plugin uses the {@link PermissioningService} to filter transactions at multiple critical
 * lifecycle stages: i.) Block import - when a Besu validator receives a block via P2P gossip ii.)
 * Transaction pool - when a Besu node adds to its local transaction pool iii.) Block production -
 * when a Besu node builds a block
 *
 * <p>As PermissioningService executes rules over a broad scope, we may in the future consolidate
 * logic from the {@code LineaTransactionSelectorPlugin} and {@code
 * LineaTransactionPoolValidatorPlugin} to unify transaction filtering logic.
 *
 * <p>In addition to transaction permissioning, {@link PermissioningService} also supports node- and
 * message-level permissioning, which can be implemented to control peer connections and devp2p
 * message exchanges.
 */
@Slf4j
@AutoService(BesuPlugin.class)
public class LineaPermissioningPlugin extends AbstractLineaRequiredPlugin {
  private PermissioningService permissioningService;

  @Override
  public void doRegister(final ServiceManager serviceManager) {
    permissioningService =
        serviceManager
            .getService(PermissioningService.class)
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Failed to obtain PermissioningService from the ServiceManager."));
  }

  @Override
  public void doStart() {
    permissioningService.registerTransactionPermissioningProvider(
        (tx) -> {
          if (tx.getType() == TransactionType.FRONTIER
              || tx.getType() == TransactionType.ACCESS_LIST
              || tx.getType() == TransactionType.EIP1559) {
            return true;
          }
          // TODO: Enable configurable rather than hardcoded behaviour for tx filtering, e.g. flag
          // for enable blob tx
          return false;
        });
  }

  @Override
  public void stop() {
    super.stop();
  }
}
