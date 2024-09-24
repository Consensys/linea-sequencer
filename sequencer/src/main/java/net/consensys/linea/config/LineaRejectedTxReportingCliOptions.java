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

import java.net.URI;

import com.google.common.base.MoreObjects;
import net.consensys.linea.plugins.LineaCliOptions;
import picocli.CommandLine;

/** The Linea Rejected Transaction Reporting CLI options. */
public class LineaRejectedTxReportingCliOptions implements LineaCliOptions {
  /**
   * The configuration key used in AbstractLineaPrivateOptionsPlugin to identify the cli options.
   */
  public static final String CONFIG_KEY = "rejected-tx-reporting-config";

  /** The rejected transaction endpoint. */
  public static final String REJECTED_TX_ENDPOINT = "--plugin-linea-rejected-tx-endpoint";

  /** The Linea node type. */
  public static final String LINEA_NODE_TYPE = "--plugin-linea-node-type";

  @CommandLine.Option(
      names = {REJECTED_TX_ENDPOINT},
      hidden = true,
      paramLabel = "<URI>",
      description =
          "Endpoint URI for reporting rejected transactions. Specify a valid URI to enable reporting.")
  private URI rejectedTxEndpoint = null;

  @CommandLine.Option(
      names = {LINEA_NODE_TYPE},
      hidden = true,
      paramLabel = "<NODE_TYPE>",
      description =
          "Linea Node type to use when reporting rejected transactions. (default: ${DEFAULT-VALUE}. Valid values: ${COMPLETION-CANDIDATES})")
  private LineaNodeType lineaNodeType = LineaNodeType.SEQUENCER;

  /** Default constructor. */
  private LineaRejectedTxReportingCliOptions() {}

  /**
   * Create Linea Rejected Transaction Reporting CLI options.
   *
   * @return the Linea Rejected Transaction Reporting CLI options
   */
  public static LineaRejectedTxReportingCliOptions create() {
    return new LineaRejectedTxReportingCliOptions();
  }

  /**
   * Instantiates a new Linea rejected tx reporting cli options from Configuration object
   *
   * @param config An instance of LineaRejectedTxReportingConfiguration
   */
  public static LineaRejectedTxReportingCliOptions fromConfig(
      final LineaRejectedTxReportingConfiguration config) {
    final LineaRejectedTxReportingCliOptions options = create();
    options.rejectedTxEndpoint = config.rejectedTxEndpoint();
    options.lineaNodeType = config.lineaNodeType();
    return options;
  }

  @Override
  public LineaRejectedTxReportingConfiguration toDomainObject() {
    return LineaRejectedTxReportingConfiguration.builder()
        .rejectedTxEndpoint(rejectedTxEndpoint)
        .lineaNodeType(lineaNodeType)
        .build();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add(REJECTED_TX_ENDPOINT, rejectedTxEndpoint)
        .add(LINEA_NODE_TYPE, lineaNodeType)
        .toString();
  }
}
