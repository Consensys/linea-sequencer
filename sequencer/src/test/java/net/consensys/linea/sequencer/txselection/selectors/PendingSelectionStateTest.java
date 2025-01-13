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
package net.consensys.linea.sequencer.txselection.selectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PendingSelectionStateTest {
  private static final Map<String, Integer> ALL_ZERO = Map.of("MOD1", 0, "MOD2", 0, "MOD3", 0);
  private PendingSelectionState<Map<String, Integer>> pendingModuleLineCount;

  @BeforeEach
  void setUp() {
    pendingModuleLineCount = new PendingSelectionState<>(ALL_ZERO);
  }

  @Test
  void allLineCountsAreZeroOnCreation() {
    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(ALL_ZERO);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(ALL_ZERO);
  }

  @Test
  void appendPendingLineCountsWithoutConfirm() {
    final var lineCountTx = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    pendingModuleLineCount.appendUnconfirmed(Hash.fromHexStringLenient("0x1"), lineCountTx);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(ALL_ZERO);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx);
  }

  @Test
  void appendMultiplePendingLineCountsWithoutConfirm() {
    final var lineCountTx1 = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    pendingModuleLineCount.appendUnconfirmed(Hash.fromHexStringLenient("0x1"), lineCountTx1);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(ALL_ZERO);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx1);

    final var lineCountTx2 = Map.of("MOD1", 1, "MOD2", 2, "MOD3", 2);

    pendingModuleLineCount.appendUnconfirmed(Hash.fromHexStringLenient("0x2"), lineCountTx2);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(ALL_ZERO);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx2);
  }

  @Test
  void appendMultiplePendingLineCountsWithConfirm() {
    final var txHash1 = Hash.fromHexStringLenient("0x1");
    final var lineCountTx1 = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    pendingModuleLineCount.appendUnconfirmed(txHash1, lineCountTx1);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(ALL_ZERO);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx1);

    final var txHash2 = Hash.fromHexStringLenient("0x2");
    final var lineCountTx2 = Map.of("MOD1", 1, "MOD2", 2, "MOD3", 2);

    pendingModuleLineCount.appendUnconfirmed(txHash2, lineCountTx2);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(ALL_ZERO);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx2);

    pendingModuleLineCount.confirm(txHash1);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(lineCountTx1);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx2);

    pendingModuleLineCount.confirm(txHash2);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(lineCountTx2);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx2);
  }

  @Test
  void appendPendingLineCountsWithConfirm() {
    final var txHash = Hash.fromHexStringLenient("0x1");
    final var lineCountTx = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    pendingModuleLineCount.appendUnconfirmed(txHash, lineCountTx);
    pendingModuleLineCount.confirm(txHash);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(lineCountTx);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx);
  }

  @Test
  void appendAndDiscardPendingLineCountsWithoutConfirm() {
    final var txHash = Hash.fromHexStringLenient("0x1");
    final var lineCountTx = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    pendingModuleLineCount.appendUnconfirmed(txHash, lineCountTx);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(ALL_ZERO);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx);

    pendingModuleLineCount.discard(txHash);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(ALL_ZERO);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(ALL_ZERO);
  }

  @Test
  void appendAndDiscardMultiplePendingLineCountsWithoutConfirm() {
    final var txHash1 = Hash.fromHexStringLenient("0x1");
    final var lineCountTx1 = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    pendingModuleLineCount.appendUnconfirmed(txHash1, lineCountTx1);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(ALL_ZERO);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx1);

    final var txHash2 = Hash.fromHexStringLenient("0x2");
    final var lineCountTx2 = Map.of("MOD1", 2, "MOD2", 2, "MOD3", 2);

    pendingModuleLineCount.appendUnconfirmed(txHash2, lineCountTx2);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(ALL_ZERO);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx2);

    pendingModuleLineCount.discard(txHash1);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(ALL_ZERO);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(ALL_ZERO);

    pendingModuleLineCount.discard(txHash2);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(ALL_ZERO);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(ALL_ZERO);
  }

  @Test
  void appendAndDiscardPendingLineCountsWithConfirm() {
    final var txHash1 = Hash.fromHexStringLenient("0x1");
    final var lineCountTx1 = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    pendingModuleLineCount.appendUnconfirmed(txHash1, lineCountTx1);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(ALL_ZERO);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx1);

    pendingModuleLineCount.confirm(txHash1);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(lineCountTx1);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx1);

    final var txHash2 = Hash.fromHexStringLenient("0x2");
    final var lineCountTx2 = Map.of("MOD1", 2, "MOD2", 2, "MOD3", 2);

    pendingModuleLineCount.appendUnconfirmed(txHash2, lineCountTx2);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(lineCountTx1);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx2);

    pendingModuleLineCount.discard(txHash2);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(lineCountTx1);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx1);
  }

  @Test
  void confirmDiscardConfirmSequence() {
    final var txHash1 = Hash.fromHexStringLenient("0x1");
    final var lineCountTx1 = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    pendingModuleLineCount.appendUnconfirmed(txHash1, lineCountTx1);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(ALL_ZERO);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx1);

    pendingModuleLineCount.confirm(txHash1);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(lineCountTx1);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx1);

    final var txHash2 = Hash.fromHexStringLenient("0x2");
    final var lineCountTx2 = Map.of("MOD1", 1, "MOD2", 2, "MOD3", 2);

    pendingModuleLineCount.appendUnconfirmed(txHash2, lineCountTx2);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(lineCountTx1);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx2);

    pendingModuleLineCount.discard(txHash2);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(lineCountTx1);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx1);

    final var txHash3 = Hash.fromHexStringLenient("0x3");
    final var lineCountTx3 = Map.of("MOD1", 1, "MOD2", 2, "MOD3", 3);

    pendingModuleLineCount.appendUnconfirmed(txHash3, lineCountTx3);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(lineCountTx1);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx3);

    pendingModuleLineCount.confirm(txHash3);

    assertThat(pendingModuleLineCount.getConfirmed()).isEqualTo(lineCountTx3);
    assertThat(pendingModuleLineCount.getLast()).isEqualTo(lineCountTx3);
  }
}
