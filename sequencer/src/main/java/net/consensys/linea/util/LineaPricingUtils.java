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
package net.consensys.linea.util;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.consensys.linea.extradata.LineaExtraDataException;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;

/** Utility class for handling Linea pricing data extraction from the extraData field. */
@Slf4j
public class LineaPricingUtils {

  private static final int FIXED_COST_OFFSET = 1;
  private static final int VARIABLE_COST_OFFSET = 5;
  private static final int GAS_PRICE_OFFSET = 9;
  private static final int COST_FIELD_LENGTH = 4;

  /**
   * Extracts the pricing data from the provided extraData bytes.
   *
   * @param extraDataBytes The extraData bytes to extract the pricing from.
   * @return PricingData object containing fixedCost, variableCost, and ethGasPrice.
   * @throws LineaExtraDataException if the data format is invalid.
   */
  public static PricingData extractPricingFromExtraData(Bytes extraDataBytes) {
    if (extraDataBytes == null || extraDataBytes.isEmpty()) {
      log.warn("ExtraData is null or empty. Using default values.");
      return new PricingData(Wei.ZERO, Wei.ZERO, Wei.ZERO);
    }

    byte version = extraDataBytes.get(0);
    if (version != 1) {
      log.warn("Unsupported extraData version: {}. Using default values.", version);
      return new PricingData(Wei.ZERO, Wei.ZERO, Wei.ZERO);
    }

    Wei fixedCost = extractCost(extraDataBytes, FIXED_COST_OFFSET);
    Wei variableCost = extractCost(extraDataBytes, VARIABLE_COST_OFFSET);
    Wei ethGasPrice = extractCost(extraDataBytes, GAS_PRICE_OFFSET);

    return new PricingData(fixedCost, variableCost, ethGasPrice);
  }

  private static Wei extractCost(Bytes extraDataBytes, int offset) {
    if (extraDataBytes.size() <= offset) {
      return Wei.ZERO;
    }
    int availableBytes = extraDataBytes.size() - offset;
    int bytesToExtract = Math.min(availableBytes, COST_FIELD_LENGTH);
    return Wei.of(extraDataBytes.slice(offset, bytesToExtract).toUnsignedBigInteger());
  }

  @Getter
  @Setter
  public static class PricingData {
    private final Wei fixedCost;
    private final Wei variableCost;
    private final Wei ethGasPrice;

    public PricingData(Wei fixedCost, Wei variableCost, Wei ethGasPrice) {
      this.fixedCost = fixedCost;
      this.variableCost = variableCost;
      this.ethGasPrice = ethGasPrice;
    }
  }
}
