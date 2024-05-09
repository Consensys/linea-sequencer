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

package net.consensys.linea.extradata;

import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.data.AddedBlockContext;
import org.hyperledger.besu.plugin.services.BesuEvents;

@Slf4j
public class LineaExtraDataHandler implements BesuEvents.BlockAddedListener {
  @Override
  public void onBlockAdded(final AddedBlockContext addedBlockContext) {
    final var blockHeader = addedBlockContext.getBlockHeader();
    final var extraData = blockHeader.getExtraData();

    if (!Bytes.EMPTY.equals(extraData)) {
      final byte version = extractVersion(extraData);
      switch (version) {
        case 1:
          parseVersion1(extraData);
          break;
        default:
          log.warn(
              "unsupported extra data version: {}, raw extra data field",
              version,
              extraData.toHexString());
      }
    }
  }

  private void parseVersion1(final Bytes extraData) {
    log.info("Found extra data version 1: {}", extraData.toHexString());
  }

  private byte extractVersion(final Bytes extraData) {
    return extraData.get(0);
  }
}
