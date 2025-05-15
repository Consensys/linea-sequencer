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
package net.consensys.linea.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import net.consensys.linea.config.LineaTransactionPoolValidatorCliOptions;
import net.consensys.linea.plugins.LineaOptionsPluginConfiguration;
import org.hyperledger.besu.plugin.services.BesuConfiguration;

/** CLI options specific to the Linea Permissioning Plugin. */
public class LineaPermissioningCliOptions implements LineaOptionsPluginConfiguration {
  public static final String CONFIG_KEY = "permissioning-config";

  public static final String BLOB_TX_ENABLED = "--plugin-linea-blob-tx-enabled";
  public static final boolean DEFAULT_BLOB_TX_ENABLED = false;

  @CommandLine.Option(
      names = {BLOB_TX_ENABLED},
      arity = "0..1",
      hidden = true,
      paramLabel = "<BOOLEAN>",
      description =
          "Enable blob transactions? (default: ${DEFAULT-VALUE})")
  private boolean blobTxEnabled = DEFAULT_BLOB_TX_ENABLED;

  public LineaPermissioningCliOptions() {}

  /**
   * Create Linea cli options.
   *
   * @return the Linea cli options
   */
  public static LineaPermissioningCliOptions create() {
    return new LineaPermissioningCliOptions();
  }

  /**
   * Cli options from config.
   *
   * @param config the config
   * @return the cli options
   */
  public static LineaPermissioningCliOptions fromConfig(
      final LineaPermissioningConfiguration config) {
    final LineaTransactionPoolValidatorCliOptions options = create();
    options.blobTxEnabled = config.blobTxEnabled();
    return options;
  }

  /**
   * To domain object Linea factory configuration.
   *
   * @return the Linea factory configuration
   */
  @Override
  public LineaPermissioningConfiguration toDomainObject() {
    return new LineaPermissioningConfiguration(
            blobTxEnabled
        );
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add(BLOB_TX_ENABLED, blobTxEnabled)
        .toString();
  }
}
