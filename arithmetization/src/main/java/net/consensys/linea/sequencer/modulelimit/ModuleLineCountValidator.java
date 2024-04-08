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

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Accumulates and verifies line counts for modules based on provided limits. It supports verifying
 * if current transactions exceed these limits and updates the accumulated counts.
 */
@Slf4j
public class ModuleLineCountValidator {
  private final Map<String, Integer> moduleLineCountLimits;

  @Getter private final Map<String, Integer> accumulatedLineCountsPerModule = new HashMap<>();

  /**
   * Constructs a new accumulator with specified module line count limits.
   *
   * @param moduleLineCountLimits A map of module names to their respective line count limits.
   */
  public ModuleLineCountValidator(Map<String, Integer> moduleLineCountLimits) {
    this.moduleLineCountLimits = new HashMap<>(moduleLineCountLimits);
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

      int previouslyAccumulatedLineCount =
          accumulatedLineCountsPerModule.getOrDefault(moduleName, 0);
      int lineCountAddedByCurrentTx =
          currentTotalLineCountForModule - previouslyAccumulatedLineCount;

      if (lineCountAddedByCurrentTx > lineCountLimitForModule) {
        return ModuleLimitsValidationResult.txModuleLineCountOverflow(moduleName);
      }

      if (currentTotalLineCountForModule > lineCountLimitForModule) {
        return ModuleLimitsValidationResult.blockModuleLineCountFull(moduleName);
      }
    }
    return ModuleLimitsValidationResult.ok();
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

  /** Enumerates possible outcomes of verifying module line counts against their limits. */
  public enum ModuleLineCountResult {
    VALID,
    TX_MODULE_LINE_COUNT_OVERFLOW,
    BLOCK_MODULE_LINE_COUNT_FULL,
    MODULE_NOT_DEFINED
  }
}
