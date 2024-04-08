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

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Accumulates and verifies line counts for modules based on provided limits. It supports verifying
 * if current transactions exceed these limits and updates the accumulated counts.
 */
@Slf4j
public class ModuleLineCountAccumulator {
  private final Map<String, Integer> moduleLineCountLimits;

  @Getter private final Map<String, Integer> accumulatedLineCountsPerModule = new HashMap<>();

  /**
   * Constructs a new accumulator with specified module line count limits.
   *
   * @param moduleLineCountLimits A map of module names to their respective line count limits.
   */
  public ModuleLineCountAccumulator(Map<String, Integer> moduleLineCountLimits) {
    this.moduleLineCountLimits = new HashMap<>(moduleLineCountLimits);
  }

  /**
   * Verifies if the current accumulated line counts for modules exceed the predefined limits.
   *
   * @param currentAccumulatedLineCounts A map of module names to their current accumulated line
   *     counts.
   * @return A {@link VerificationResult} indicating the outcome of the verification.
   */
  public VerificationResult verify(Map<String, Integer> currentAccumulatedLineCounts) {
    for (Map.Entry<String, Integer> moduleEntry : currentAccumulatedLineCounts.entrySet()) {
      String moduleName = moduleEntry.getKey();
      Integer currentTotalLineCountForModule = moduleEntry.getValue();
      Integer lineCountLimitForModule = moduleLineCountLimits.get(moduleName);

      if (lineCountLimitForModule == null) {
        log.error("Module '{}' is not defined in the line count limits.", moduleName);
        return VerificationResult.moduleNotDefined(moduleName);
      }

      int previouslyAccumulatedLineCount =
          accumulatedLineCountsPerModule.getOrDefault(moduleName, 0);
      int lineCountAddedByCurrentTx =
          currentTotalLineCountForModule - previouslyAccumulatedLineCount;

      if (lineCountAddedByCurrentTx > lineCountLimitForModule) {
        return VerificationResult.txModuleLineCountOverflow(moduleName);
      }

      if (currentTotalLineCountForModule > lineCountLimitForModule) {
        return VerificationResult.blockModuleLineCountFull(moduleName);
      }
    }
    return VerificationResult.ok();
  }

  /**
   * Updates the internal map of accumulated line counts per module.
   *
   * @param newAccumulatedLineCounts A map of module names to their new accumulated line counts.
   */
  public void updateAccumulatedLineCounts(Map<String, Integer> newAccumulatedLineCounts) {
    accumulatedLineCountsPerModule.clear();
    accumulatedLineCountsPerModule.putAll(newAccumulatedLineCounts);
  }

  /** Represents the result of verifying module line counts against their limits. */
  @Getter
  public static class VerificationResult {
    // Getters for verificationOutcome and affectedModuleName
    private final ModuleLineCountResult result;
    private final String moduleName;

    private VerificationResult(ModuleLineCountResult result, String moduleName) {
      this.result = result;
      this.moduleName = moduleName;
    }

    public static VerificationResult ok() {
      return new VerificationResult(ModuleLineCountResult.VALID, null);
    }

    public static VerificationResult moduleNotDefined(String moduleName) {
      return new VerificationResult(ModuleLineCountResult.MODULE_NOT_DEFINED, moduleName);
    }

    public static VerificationResult txModuleLineCountOverflow(String moduleName) {
      return new VerificationResult(
          ModuleLineCountResult.TX_MODULE_LINE_COUNT_OVERFLOW, moduleName);
    }

    public static VerificationResult blockModuleLineCountFull(String moduleName) {
      return new VerificationResult(ModuleLineCountResult.BLOCK_MODULE_LINE_COUNT_FULL, moduleName);
    }
  }

  /** Enumerates possible outcomes of verifying module line counts against their limits. */
  public enum ModuleLineCountResult {
    VALID,
    TX_MODULE_LINE_COUNT_OVERFLOW,
    BLOCK_MODULE_LINE_COUNT_FULL,
    MODULE_NOT_DEFINED
  }
}
