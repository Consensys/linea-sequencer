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

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.io.Resources;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.consensys.linea.config.LineaTracerConfiguration;
import org.apache.tuweni.toml.Toml;
import org.apache.tuweni.toml.TomlParseResult;
import org.apache.tuweni.toml.TomlTable;
import org.hyperledger.besu.datatypes.Hash;

/**
 * Accumulates and verifies line counts for modules based on provided limits. It supports verifying
 * if current transaction exceed these limits and updates the accumulated counts. Supports atomic
 * groups of txs, keeping an internal pending list of accumulated line counts referred by tx hashes,
 * that could be reverted to the latest confirmed checkpoint in case the atomic group if not
 * selected.
 */
@Slf4j
public class ModuleLineCountValidator {
  private final Map<String, Integer> moduleLineCountLimits;
  private final SequencedMap<Hash, Map<String, Integer>> pendingAccumulatedLineCountsPerModule =
      new LinkedHashMap<>();
  @Getter private Map<String, Integer> confirmedAccumulatedLineCountsPerModule;

  /**
   * Constructs a new accumulator with specified module line count limits.
   *
   * @param moduleLineCountLimits A map of module names to their respective line count limits.
   */
  public ModuleLineCountValidator(Map<String, Integer> moduleLineCountLimits) {
    this.moduleLineCountLimits = Map.copyOf(moduleLineCountLimits);
    this.confirmedAccumulatedLineCountsPerModule =
        moduleLineCountLimits.keySet().stream()
            .collect(Collectors.toMap(Function.identity(), unused -> 0));
  }

  /**
   * Verifies if the current accumulated line counts for modules exceed the predefined limits.
   *
   * @param currentAccumulatedLineCounts A map of module names to their current accumulated line
   *     counts.
   * @return A {@link ModuleLimitsValidationResult} indicating the outcome of the verification.
   */
  public ModuleLimitsValidationResult validate(Map<String, Integer> currentAccumulatedLineCounts) {
    for (Map.Entry<String, Integer> moduleEntry : currentAccumulatedLineCounts.entrySet()) {
      String moduleName = moduleEntry.getKey();
      Integer currentTotalLineCountForModule = moduleEntry.getValue();
      Integer lineCountLimitForModule = moduleLineCountLimits.get(moduleName);

      if (lineCountLimitForModule == null) {
        log.error("Module '{}' is not defined in the line count limits.", moduleName);
        return ModuleLimitsValidationResult.moduleNotDefined(moduleName);
      }

      final int lineCountAddedByCurrentTx =
          currentTotalLineCountForModule - getLastAccumulatedLineCountsPerModule().get(moduleName);

      if (lineCountAddedByCurrentTx > lineCountLimitForModule) {
        return ModuleLimitsValidationResult.txModuleLineCountOverflow(
            moduleName,
            lineCountAddedByCurrentTx,
            lineCountLimitForModule,
            currentTotalLineCountForModule,
            lineCountLimitForModule);
      }

      if (currentTotalLineCountForModule > lineCountLimitForModule) {
        return ModuleLimitsValidationResult.blockModuleLineCountFull(
            moduleName,
            lineCountAddedByCurrentTx,
            lineCountLimitForModule,
            currentTotalLineCountForModule,
            lineCountLimitForModule);
      }
    }
    return ModuleLimitsValidationResult.VALID;
  }

  /**
   * Append the accumulated line counts per module related to the passed tx hash. The appended value
   * remains pending, meaning it could be discarded, until {@link
   * ModuleLineCountResult#confirm(Hash)} is called for the same tx hash or a following one.
   *
   * @param txHash Hash of the transaction
   * @param accumulatedLineCounts A map of module names to their new accumulated line counts.
   */
  public void appendAccumulatedLineCounts(
      final Hash txHash, final Map<String, Integer> accumulatedLineCounts) {
    pendingAccumulatedLineCountsPerModule.putLast(txHash, accumulatedLineCounts);
  }

  /**
   * Discards the pending accumulated line counts starting from the specified tx hash.
   *
   * @param txHash the tx hash, could not be present, in which case there is no change to the
   *     pending state
   */
  public void discard(final Hash txHash) {
    boolean afterRemoved = false;
    final var it = pendingAccumulatedLineCountsPerModule.entrySet().iterator();
    while (it.hasNext()) {
      final var entry = it.next();
      if (afterRemoved || entry.getKey().equals(txHash)) {
        it.remove();
        afterRemoved = true;
      }
    }
  }

  /**
   * Sets the accumulated line counts referred by the specified tx hash has the confirmed one,
   * allowing to forget all the preceding entries.
   *
   * @param txHash the tx hash, it must exist in the pending list, otherwise an exception is thrown
   */
  public void confirm(final Hash txHash) {
    checkArgument(
        pendingAccumulatedLineCountsPerModule.containsKey(txHash),
        "The specified tx hash has no pending accumulated line counts.");

    final var it = pendingAccumulatedLineCountsPerModule.entrySet().iterator();
    while (it.hasNext()) {
      final var entry = it.next();
      it.remove();
      if (entry.getKey().equals(txHash)) {
        confirmedAccumulatedLineCountsPerModule = Collections.unmodifiableMap(entry.getValue());
        break;
      }
    }
  }

  /**
   * Gets the latest, including pending, accumulated line counts. Note that the returned values
   * could not yet be confirmed and could be discarded in the future.
   *
   * @return a map with the line count per module
   */
  public Map<String, Integer> getLastAccumulatedLineCountsPerModule() {
    if (pendingAccumulatedLineCountsPerModule.isEmpty()) {
      return confirmedAccumulatedLineCountsPerModule;
    }
    return pendingAccumulatedLineCountsPerModule.lastEntry().getValue();
  }

  /** Enumerates possible outcomes of verifying module line counts against their limits. */
  public enum ModuleLineCountResult {
    VALID,
    TX_MODULE_LINE_COUNT_OVERFLOW,
    BLOCK_MODULE_LINE_COUNT_FULL,
    MODULE_NOT_DEFINED
  }

  public static Map<String, Integer> createLimitModules(
      LineaTracerConfiguration lineaTracerConfiguration) {
    try {
      URL url = new File(lineaTracerConfiguration.moduleLimitsFilePath()).toURI().toURL();
      final String tomlString = Resources.toString(url, StandardCharsets.UTF_8);
      TomlParseResult result = Toml.parse(tomlString);
      final TomlTable table = result.getTable("traces-limits");
      final Map<String, Integer> limitsMap =
          table.toMap().entrySet().stream()
              .collect(
                  Collectors.toUnmodifiableMap(
                      Map.Entry::getKey, e -> Math.toIntExact((Long) e.getValue())));

      return limitsMap;
    } catch (final Exception e) {
      final String errorMsg =
          "Problem reading the toml file containing the limits for the modules: "
              + lineaTracerConfiguration.moduleLimitsFilePath();
      log.error(errorMsg);
      throw new RuntimeException(errorMsg, e);
    }
  }
}
