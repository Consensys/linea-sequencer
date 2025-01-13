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
package net.consensys.linea.sequencer.modulelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.Test;

class ModuleLineCountValidatorTest {

  @Test
  void allLineCountsAreZeroOnCreation() {
    final var moduleLineCountValidator =
        new ModuleLineCountValidator(Map.of("MOD1", 1, "MOD2", 2, "MOD3", 3));
    final var allZeroMap = Map.of("MOD1", 0, "MOD2", 0, "MOD3", 0);
    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
  }

  @Test
  void appendPendingLineCountsWithoutConfirm() {
    final var moduleLineCountValidator =
        new ModuleLineCountValidator(Map.of("MOD1", 1, "MOD2", 2, "MOD3", 3));
    final var allZeroMap = Map.of("MOD1", 0, "MOD2", 0, "MOD3", 0);
    final var lineCountTx = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    moduleLineCountValidator.appendAccumulatedLineCounts(
        Hash.fromHexStringLenient("0x1"), lineCountTx);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx);
  }

  @Test
  void appendMultiplePendingLineCountsWithoutConfirm() {
    final var moduleLineCountValidator =
        new ModuleLineCountValidator(Map.of("MOD1", 1, "MOD2", 2, "MOD3", 3));
    final var allZeroMap = Map.of("MOD1", 0, "MOD2", 0, "MOD3", 0);
    final var lineCountTx1 = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    assertThat(moduleLineCountValidator.validate(lineCountTx1))
        .isEqualTo(ModuleLimitsValidationResult.VALID);

    moduleLineCountValidator.appendAccumulatedLineCounts(
        Hash.fromHexStringLenient("0x1"), lineCountTx1);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);

    final var lineCountTx2 = Map.of("MOD1", 1, "MOD2", 2, "MOD3", 2);

    assertThat(moduleLineCountValidator.validate(lineCountTx2))
        .isEqualTo(ModuleLimitsValidationResult.VALID);

    moduleLineCountValidator.appendAccumulatedLineCounts(
        Hash.fromHexStringLenient("0x2"), lineCountTx2);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx2);
  }

  @Test
  void appendMultiplePendingLineCountsWithConfirm() {
    final var moduleLineCountValidator =
        new ModuleLineCountValidator(Map.of("MOD1", 1, "MOD2", 2, "MOD3", 3));
    final var allZeroMap = Map.of("MOD1", 0, "MOD2", 0, "MOD3", 0);
    final var txHash1 = Hash.fromHexStringLenient("0x1");
    final var lineCountTx1 = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    assertThat(moduleLineCountValidator.validate(lineCountTx1))
        .isEqualTo(ModuleLimitsValidationResult.VALID);

    moduleLineCountValidator.appendAccumulatedLineCounts(txHash1, lineCountTx1);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);

    final var txHash2 = Hash.fromHexStringLenient("0x2");
    final var lineCountTx2 = Map.of("MOD1", 1, "MOD2", 2, "MOD3", 2);

    assertThat(moduleLineCountValidator.validate(lineCountTx2))
        .isEqualTo(ModuleLimitsValidationResult.VALID);

    moduleLineCountValidator.appendAccumulatedLineCounts(txHash2, lineCountTx2);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx2);

    moduleLineCountValidator.confirm(txHash1);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx2);

    moduleLineCountValidator.confirm(txHash2);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx2);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx2);
  }

  @Test
  void appendPendingLineCountsWithConfirm() {
    final var moduleLineCountValidator =
        new ModuleLineCountValidator(Map.of("MOD1", 1, "MOD2", 2, "MOD3", 3));
    final var txHash = Hash.fromHexStringLenient("0x1");
    final var lineCountTx = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    moduleLineCountValidator.appendAccumulatedLineCounts(txHash, lineCountTx);
    moduleLineCountValidator.confirm(txHash);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx);
  }

  @Test
  void appendAndDiscardPendingLineCountsWithoutConfirm() {
    final var moduleLineCountValidator =
        new ModuleLineCountValidator(Map.of("MOD1", 1, "MOD2", 2, "MOD3", 3));
    final var allZeroMap = Map.of("MOD1", 0, "MOD2", 0, "MOD3", 0);
    final var txHash = Hash.fromHexStringLenient("0x1");
    final var lineCountTx = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    moduleLineCountValidator.appendAccumulatedLineCounts(txHash, lineCountTx);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx);

    moduleLineCountValidator.discard(txHash);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
  }

  @Test
  void appendAndDiscardMultiplePendingLineCountsWithoutConfirm() {
    final var moduleLineCountValidator =
        new ModuleLineCountValidator(Map.of("MOD1", 1, "MOD2", 2, "MOD3", 3));
    final var allZeroMap = Map.of("MOD1", 0, "MOD2", 0, "MOD3", 0);
    final var txHash1 = Hash.fromHexStringLenient("0x1");
    final var lineCountTx1 = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    moduleLineCountValidator.appendAccumulatedLineCounts(txHash1, lineCountTx1);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);

    final var txHash2 = Hash.fromHexStringLenient("0x2");
    final var lineCountTx2 = Map.of("MOD1", 2, "MOD2", 2, "MOD3", 2);

    moduleLineCountValidator.appendAccumulatedLineCounts(txHash2, lineCountTx2);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx2);

    moduleLineCountValidator.discard(txHash1);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);

    moduleLineCountValidator.discard(txHash2);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
  }

  @Test
  void appendAndDiscardPendingLineCountsWithConfirm() {
    final var moduleLineCountValidator =
        new ModuleLineCountValidator(Map.of("MOD1", 1, "MOD2", 2, "MOD3", 3));
    final var allZeroMap = Map.of("MOD1", 0, "MOD2", 0, "MOD3", 0);
    final var txHash1 = Hash.fromHexStringLenient("0x1");
    final var lineCountTx1 = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    moduleLineCountValidator.appendAccumulatedLineCounts(txHash1, lineCountTx1);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);

    moduleLineCountValidator.confirm(txHash1);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);

    final var txHash2 = Hash.fromHexStringLenient("0x2");
    final var lineCountTx2 = Map.of("MOD1", 2, "MOD2", 2, "MOD3", 2);

    moduleLineCountValidator.appendAccumulatedLineCounts(txHash2, lineCountTx2);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx2);

    moduleLineCountValidator.discard(txHash2);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);
  }

  @Test
  void confirmDiscardConfirmSequenceWithSuccessfulValidation() {
    final var moduleLineCountValidator =
        new ModuleLineCountValidator(Map.of("MOD1", 1, "MOD2", 2, "MOD3", 3));
    final var allZeroMap = Map.of("MOD1", 0, "MOD2", 0, "MOD3", 0);
    final var txHash1 = Hash.fromHexStringLenient("0x1");
    final var lineCountTx1 = Map.of("MOD1", 1, "MOD2", 1, "MOD3", 1);

    assertThat(moduleLineCountValidator.validate(lineCountTx1))
        .isEqualTo(ModuleLimitsValidationResult.VALID);

    moduleLineCountValidator.appendAccumulatedLineCounts(txHash1, lineCountTx1);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(allZeroMap);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);

    moduleLineCountValidator.confirm(txHash1);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);

    final var txHash2 = Hash.fromHexStringLenient("0x2");
    final var lineCountTx2 = Map.of("MOD1", 1, "MOD2", 2, "MOD3", 2);

    assertThat(moduleLineCountValidator.validate(lineCountTx2))
        .isEqualTo(ModuleLimitsValidationResult.VALID);

    moduleLineCountValidator.appendAccumulatedLineCounts(txHash2, lineCountTx2);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx2);

    moduleLineCountValidator.discard(txHash2);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);

    final var txHash3 = Hash.fromHexStringLenient("0x3");
    final var lineCountTx3 = Map.of("MOD1", 1, "MOD2", 2, "MOD3", 3);

    assertThat(moduleLineCountValidator.validate(lineCountTx3))
        .isEqualTo(ModuleLimitsValidationResult.VALID);

    moduleLineCountValidator.appendAccumulatedLineCounts(txHash3, lineCountTx3);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx1);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx3);

    moduleLineCountValidator.confirm(txHash3);

    assertThat(moduleLineCountValidator.getConfirmedAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx3);
    assertThat(moduleLineCountValidator.getLastAccumulatedLineCountsPerModule())
        .isEqualTo(lineCountTx3);
  }

  @Test
  void failedValidationTransactionOverLimit() {
    final var moduleLineCounts = LinkedHashMap.<String, Integer>newLinkedHashMap(3);
    moduleLineCounts.put("MOD1", 1);
    moduleLineCounts.put("MOD2", 2);
    moduleLineCounts.put("MOD3", 3);
    final var moduleLineCountValidator = new ModuleLineCountValidator(moduleLineCounts);
    final var lineCountTx = Map.of("MOD1", 3, "MOD2", 3, "MOD3", 3);

    assertThat(moduleLineCountValidator.validate(lineCountTx))
        .isEqualTo(ModuleLimitsValidationResult.txModuleLineCountOverflow("MOD1", 3, 1, 3, 1));
  }
}
