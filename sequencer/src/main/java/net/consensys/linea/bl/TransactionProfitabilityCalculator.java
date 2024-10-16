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
package net.consensys.linea.bl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.consensys.linea.compress.LibCompress;
import net.consensys.linea.config.LineaProfitabilityConfiguration;
import net.consensys.linea.metrics.TransactionProfitabilityMetrics;
import net.consensys.linea.util.LineaPricingUtils;
import org.hyperledger.besu.datatypes.Quantity;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.Wei;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * This class implements the profitability formula, and it is used both to check if a tx is
 * profitable and to give an estimation of the profitable priorityFeePerGas for a given tx. The
 * profitability depends on the context, so it could mean that it is priced enough to have a chance:
 * to be accepted in the txpool and to be a candidate for new block creation, it is also used to
 * give an estimated priorityFeePerGas in response to a linea_estimateGas call. Each context has it
 * own minMargin configuration, so that is possible to accept in the txpool txs, that are not yet
 * profitable for block inclusion, but could be in future if the gas price decrease and likewise, it
 * is possible to return an estimated priorityFeePerGas that has a profitability buffer to address
 * small fluctuations in the gas market.
 */
@Slf4j
@Getter
public class TransactionProfitabilityCalculator {
  private final LineaProfitabilityConfiguration profitabilityConf;
  private final TransactionProfitabilityMetrics profitabilityMetrics;

  public TransactionProfitabilityCalculator(
      final LineaProfitabilityConfiguration profitabilityConf,
      final TransactionProfitabilityMetrics profitabilityMetrics) {
    this.profitabilityConf = profitabilityConf;
    this.profitabilityMetrics = profitabilityMetrics;
  }

  /**
   * Calculate the estimation of priorityFeePerGas that is considered profitable for the given tx,
   * according to the current pricing config and the minMargin.
   *
   * @param transaction the tx we want to get the estimated priorityFeePerGas for
   * @param minMargin the min margin to use for this calculation
   * @param gas the gas to use for this calculation, could be the gasUsed of the tx, if it has been
   *     processed/simulated, otherwise the gasLimit of the tx
   * @param minGasPriceWei the current minGasPrice, only used in place of the variable cost from the
   *     config, in case the extra data pricing is disabled
   * @param pricingData object containing fixedCost, variableCost, and ethGasPrice.
   * @return the estimation of priorityFeePerGas that is considered profitable for the given tx
   */
  public Wei profitablePriorityFeePerGas(
      final Transaction transaction,
      final double minMargin,
      final long gas,
      final Wei minGasPriceWei,
      final LineaPricingUtils.PricingData pricingData) {
    final int compressedTxSize = getCompressedTxSize(transaction);

    // If pricingData is present and 3D pricing is enabled, use its variable cost
    // Otherwise, fall back to minGasPriceWei
    final long variableCostWei =
        (pricingData != null && profitabilityConf.extraDataPricingEnabled())
            ? pricingData.getVariableCost().toLong() // Use variable cost from PricingData
            : minGasPriceWei
                .toLong(); // Fallback to min gas price if PricingData or 3D pricing is disabled

    final var profitAt =
        minMargin * (variableCostWei * compressedTxSize / gas + profitabilityConf.fixedCostWei());

    final var profitAtWei = Wei.ofNumber(BigDecimal.valueOf(profitAt).toBigInteger());

    // Record metrics for calculated priority fee and compressed transaction size
    profitabilityMetrics.recordCalculatedPriorityFee(profitAtWei);
    profitabilityMetrics.recordCompressedTxSize(compressedTxSize);

    // Record profitability ratio
    Optional<? extends Quantity> maxPriorityFeePerGas = transaction.getMaxPriorityFeePerGas();
    if (maxPriorityFeePerGas.isPresent()) {
      Quantity actualPriorityFee = maxPriorityFeePerGas.get();
      if (!actualPriorityFee.getAsBigInteger().equals(BigInteger.ZERO)) {
        double ratio =
            profitAtWei.getAsBigInteger().doubleValue()
                / actualPriorityFee.getAsBigInteger().doubleValue();
        profitabilityMetrics.recordEvaluatedTxProfitabilityRatio(ratio);
      }
    }

    log.atDebug()
        .setMessage(
            "Estimated profitable priorityFeePerGas: {}, minMargin={}, fixedCostWei={}, variableCostWei={}, gas={}, txSize={}, compressedTxSize={}")
        .addArgument(profitAtWei::toHumanReadableString)
        .addArgument(minMargin)
        .addArgument(profitabilityConf.fixedCostWei())
        .addArgument(variableCostWei)
        .addArgument(gas)
        .addArgument(transaction::getSize)
        .addArgument(compressedTxSize)
        .log();

    return profitAtWei;
  }

  /**
   * Checks if then given gas price is considered profitable for the given tx, according to the
   * current pricing config, the minMargin and gas used, or gasLimit of the tx.
   *
   * @param context a string to name the context in which it is called, used for logs
   * @param transaction the tx we want to check if profitable
   * @param minMargin the min margin to use for this check
   * @param payingGasPrice the gas price the tx is willing to pay
   * @param gas the gas to use for this check, could be the gasUsed of the tx, if it has been
   *     processed/simulated, otherwise the gasLimit of the tx
   * @param minGasPriceWei the current minGasPrice, only used in place of the variable cost from the
   *     config, in case the extra data pricing is disabled
   * @param pricingData object containing fixedCost, variableCost, and ethGasPrice.
   * @return true if the tx is priced enough to be profitable, false otherwise
   */
  public boolean isProfitable(
      final String context,
      final Transaction transaction,
      final double minMargin,
      final Wei baseFee,
      final Wei payingGasPrice,
      final long gas,
      final Wei minGasPriceWei,
      final LineaPricingUtils.PricingData pricingData) {

    // Record pre-processing profitability
    profitabilityMetrics.recordPreProcessingProfitability(
        transaction, baseFee, payingGasPrice, gas);

    // Calculate the profitable priority fee and gas price
    final Wei profitablePriorityFee =
        profitablePriorityFeePerGas(transaction, minMargin, gas, minGasPriceWei, pricingData);
    final Wei profitableGasPrice = baseFee.add(profitablePriorityFee);

    // Calculate the profitability ratio
    double profitabilityRatio =
        payingGasPrice.getAsBigInteger().doubleValue()
            / profitableGasPrice.getAsBigInteger().doubleValue();
    profitabilityMetrics.recordEvaluatedTxProfitabilityRatio(profitabilityRatio);

    log.debug(
        "Calculated profitablePriorityFee: {}, profitableGasPrice: {}, payingGasPrice: {}",
        profitablePriorityFee.toHumanReadableString(),
        profitableGasPrice.toHumanReadableString(),
        payingGasPrice.toHumanReadableString());

    // Record TxPool profitability
    profitabilityMetrics.recordTxPoolProfitability(transaction, profitablePriorityFee);

    // If the gas price is less than the profitable gas price, mark as unprofitable
    if (payingGasPrice.lessThan(profitableGasPrice)) {
      logTransactionProfitability(
          log.atDebug(),
          context,
          transaction,
          minMargin,
          payingGasPrice,
          baseFee,
          profitablePriorityFee,
          profitableGasPrice,
          gas,
          minGasPriceWei);
      return false;
    }

    // Otherwise, mark as profitable
    logTransactionProfitability(
        log.atTrace(),
        context,
        transaction,
        minMargin,
        payingGasPrice,
        baseFee,
        profitablePriorityFee,
        profitableGasPrice,
        gas,
        minGasPriceWei);
    return true;
  }

  /**
   * This method calculates the compressed size of a tx using the native lib
   *
   * @param transaction the tx
   * @return the compressed size
   */
  private int getCompressedTxSize(final Transaction transaction) {
    final byte[] bytes = transaction.encoded().toArrayUnsafe();
    return LibCompress.CompressedSize(bytes, bytes.length);
  }

  /** Log transaction profitability details. */
  private void logTransactionProfitability(
      final LoggingEventBuilder leb,
      final String context,
      final Transaction transaction,
      final double minMargin,
      final Wei payingGasPrice,
      final Wei baseFee,
      final Wei profitablePriorityFee,
      final Wei profitableGasPrice,
      final long gasUsed,
      final Wei minGasPriceWei) {

    leb.setMessage(
            "Context {}. Transaction {} has a margin of {}, minMargin={}, payingGasPrice={}, profitableGasPrice={}, baseFee={}, profitablePriorityFee={}, fixedCostWei={}, variableCostWei={}, gasUsed={}")
        .addArgument(context)
        .addArgument(transaction::getHash)
        .addArgument(
            () ->
                payingGasPrice.toBigInteger().doubleValue()
                    / profitablePriorityFee.toBigInteger().doubleValue())
        .addArgument(minMargin)
        .addArgument(payingGasPrice::toHumanReadableString)
        .addArgument(profitableGasPrice::toHumanReadableString)
        .addArgument(baseFee::toHumanReadableString)
        .addArgument(profitablePriorityFee::toHumanReadableString)
        .addArgument(profitabilityConf::fixedCostWei)
        .addArgument(
            () ->
                profitabilityConf.extraDataPricingEnabled()
                    ? profitabilityConf.variableCostWei()
                    : minGasPriceWei.toLong())
        .addArgument(gasUsed)
        .log();
  }

  public void updateExtraDataMetrics(LineaPricingUtils.PricingData pricingData) {
    profitabilityMetrics.updateExtraDataMetrics(pricingData);
  }

  public void recordSealedBlockProfitability(Transaction transaction, Wei calculatedPriorityFee) {
    profitabilityMetrics.recordSealedBlockProfitability(transaction, calculatedPriorityFee);
  }
}
