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

import lombok.Getter;

/** Represents the result of verifying module line counts against their limits. */
@Getter
public class ModuleLimitsValidationResult {
  // Getters for verificationOutcome and affectedModuleName
  private final ModuleLineCountValidator.ModuleLineCountResult result;
  private final String moduleName;

  public static ModuleLimitsValidationResult VALID =
      new ModuleLimitsValidationResult(ModuleLineCountValidator.ModuleLineCountResult.VALID, null);

  private ModuleLimitsValidationResult(
      ModuleLineCountValidator.ModuleLineCountResult result, String moduleName) {
    this.result = result;
    this.moduleName = moduleName;
  }

  public static ModuleLimitsValidationResult moduleNotDefined(String moduleName) {
    return new ModuleLimitsValidationResult(
        ModuleLineCountValidator.ModuleLineCountResult.MODULE_NOT_DEFINED, moduleName);
  }

  public static ModuleLimitsValidationResult txModuleLineCountOverflow(String moduleName) {
    return new ModuleLimitsValidationResult(
        ModuleLineCountValidator.ModuleLineCountResult.TX_MODULE_LINE_COUNT_OVERFLOW, moduleName);
  }

  public static ModuleLimitsValidationResult blockModuleLineCountFull(String moduleName) {
    return new ModuleLimitsValidationResult(
        ModuleLineCountValidator.ModuleLineCountResult.BLOCK_MODULE_LINE_COUNT_FULL, moduleName);
  }
}
