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

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.io.Resources;
import lombok.extern.slf4j.Slf4j;
import net.consensys.linea.config.LineaTracerConfiguration;
import org.apache.tuweni.toml.Toml;
import org.apache.tuweni.toml.TomlParseResult;
import org.apache.tuweni.toml.TomlTable;

@Slf4j
public class LimitModuleHelper {
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
