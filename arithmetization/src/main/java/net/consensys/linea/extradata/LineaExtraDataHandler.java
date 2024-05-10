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

package net.consensys.linea.extradata;

import java.util.function.Consumer;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import net.consensys.linea.config.LineaProfitabilityConfiguration;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt32;
import org.hyperledger.besu.plugin.data.AddedBlockContext;
import org.hyperledger.besu.plugin.services.BesuEvents;

@Slf4j
public class LineaExtraDataHandler implements BesuEvents.BlockAddedListener {
  private final ExtraDataParser[] extraDataParsers;

  public LineaExtraDataHandler(final LineaProfitabilityConfiguration profitabilityConf) {
    extraDataParsers = new ExtraDataParser[] {new Version1Parser(profitabilityConf)};
  }

  @Override
  public void onBlockAdded(final AddedBlockContext addedBlockContext) {
    final var blockHeader = addedBlockContext.getBlockHeader();
    final var rawExtraData = blockHeader.getExtraData();

    if (!Bytes.EMPTY.equals(rawExtraData)) {
      for (final ExtraDataParser extraDataParser : extraDataParsers) {
        if (extraDataParser.canParse(rawExtraData)) {
          final var extraData = rawExtraData.slice(1);
          extraDataParser.parse(extraData);
          return;
        }
      }
      log.warn("unsupported extra data field {}", rawExtraData.toHexString());
    }
  }

  private interface ExtraDataParser {
    boolean canParse(Bytes extraData);

    void parse(Bytes extraData);

    static Long toLong(final Bytes fieldBytes) {
      return UInt32.fromBytes(fieldBytes).toLong();
    }
  }

  @SuppressWarnings("rawtypes")
  private static class Version1Parser implements ExtraDataParser {

    private final FieldConsumer[] fieldsSequence;

    public Version1Parser(final LineaProfitabilityConfiguration profitabilityConf) {
      final FieldConsumer fixedGasCostField =
          new FieldConsumer<>(
              "fixedGasCost", 4, ExtraDataParser::toLong, profitabilityConf::fixedCostKWei);
      final FieldConsumer variableGasCostField =
          new FieldConsumer<>(
              "variableGasCost", 4, ExtraDataParser::toLong, profitabilityConf::variableCostKWei);
      final FieldConsumer minGasPriceField =
          new FieldConsumer<>("minGasPrice", 4, ExtraDataParser::toLong, this::toDo);

      this.fieldsSequence =
          new FieldConsumer[] {fixedGasCostField, variableGasCostField, minGasPriceField};
    }

    public boolean canParse(final Bytes rawExtraData) {
      return rawExtraData.get(0) == (byte) 1;
    }

    public void parse(final Bytes extraData) {
      log.info("Parsing extra data version 1: {}", extraData.toHexString());
      int startIndex = 0;
      for (final FieldConsumer fieldConsumer : fieldsSequence) {
        fieldConsumer.accept(extraData.slice(startIndex, fieldConsumer.length));
        startIndex += fieldConsumer.length;
      }
    }

    void toDo(final Long minGasPriceKWei) {
      log.info("ToDo: call setMinGasPrice to {} kwei", minGasPriceKWei);
    }
  }

  private record FieldConsumer<T>(
      String name, int length, Function<Bytes, T> converter, Consumer<T> consumer)
      implements Consumer<Bytes> {

    @Override
    public void accept(final Bytes fieldBytes) {
      final var converted = converter.apply(fieldBytes);
      log.debug("Field {}={} (raw bytes: {})", name, converted, fieldBytes.toHexString());
      consumer.accept(converted);
    }
  }
}
