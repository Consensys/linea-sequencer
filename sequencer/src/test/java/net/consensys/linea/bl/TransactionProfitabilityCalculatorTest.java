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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigInteger;

import net.consensys.linea.config.LineaProfitabilityCliOptions;
import net.consensys.linea.config.LineaProfitabilityConfiguration;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionProfitabilityCalculatorTest {
  private static final SECPSignature FAKE_SIGNATURE;

  static {
    final X9ECParameters params = SECNamedCurves.getByName("secp256k1");
    final ECDomainParameters curve =
        new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
    FAKE_SIGNATURE =
        SECPSignature.create(
            new BigInteger(
                "66397251408932042429874251838229702988618145381408295790259650671563847073199"),
            new BigInteger(
                "24729624138373455972486746091821238755870276413282629437244319694880507882088"),
            (byte) 0,
            curve.getN());
  }

  public static final Wei ETH_GAS_PRICE = Wei.of(52025000);
  public static final Address SENDER =
      Address.fromHexString("0x0000000000000000000000000000000000001000");
  public static final Address RECIPIENT =
      Address.fromHexString("0x0000000000000000000000000000000000001001");
  public static final Long VARIABlE_COST = (long) (3287000L / 0.0006);
  public static final LineaProfitabilityConfiguration profitabilityConfiguration =
      LineaProfitabilityCliOptions.create().toDomainObject().toBuilder()
          .minMargin(1.0)
          .extraDataPricingEnabled(true)
          .build();
  public static final int FIXED_COST_WEI = 30000000; // 0.03 Gwei
  public static final TransactionProfitabilityCalculator calculator =
      new TransactionProfitabilityCalculator(profitabilityConfiguration);

  @BeforeEach
  void beforeEach() {
    profitabilityConfiguration.updateFixedAndVariableCost(FIXED_COST_WEI, VARIABlE_COST);
  }

  @Test
  void plainTransfer() {
    final Transaction plainTransfer =
        Transaction.builder()
            .sender(SENDER)
            .to(RECIPIENT)
            .gasLimit(21000)
            .gasPrice(ETH_GAS_PRICE)
            .payload(Bytes.EMPTY)
            .value(Wei.ONE)
            .signature(FAKE_SIGNATURE)
            .build();

    performNegativeTest(plainTransfer, 1);

    setVariableCostTo(VARIABlE_COST * 5);
    performPositiveTest(plainTransfer, 3);
  }

  @Test
  void contractDeployment() throws IOException {
    final String contractDeploymentHex =
        new String(getClass().getResourceAsStream("/contract-bytecode").readAllBytes());
    final Bytes payload = Bytes.fromHexString(contractDeploymentHex);
    final Transaction contractDeployment =
        Transaction.builder()
            .sender(SENDER)
            .gasLimit(468428)
            .gasPrice(ETH_GAS_PRICE)
            .payload(payload)
            .signature(FAKE_SIGNATURE)
            .value(Wei.ZERO)
            .build();
    performPositiveTest(contractDeployment, 1);

    setVariableCostTo(VARIABlE_COST * 5);
    performPositiveTest(contractDeployment, 3);
  }

  @Test
  void inscriptionWith250Bytes() {
    final Bytes payload = Bytes.random(250);
    final Transaction inscription =
        Transaction.builder()
            .sender(SENDER)
            .to(RECIPIENT)
            .gasLimit(24988)
            .gasPrice(ETH_GAS_PRICE)
            .payload(payload)
            .signature(FAKE_SIGNATURE)
            .value(Wei.ZERO)
            .build();

    performNegativeTest(inscription, 1);
    performPositiveTest(inscription, 3);

    setVariableCostTo(VARIABlE_COST * 5);
    performNegativeTest(inscription, 5);
    performPositiveTest(inscription, 9);
  }

  @Test
  void inscriptionWith500Bytes() {
    final Bytes payload = Bytes.random(500);
    final Transaction inscription =
        Transaction.builder()
            .sender(SENDER)
            .to(RECIPIENT)
            .gasLimit(28964)
            .gasPrice(ETH_GAS_PRICE)
            .payload(payload)
            .signature(FAKE_SIGNATURE)
            .value(Wei.ZERO)
            .build();

    performNegativeTest(inscription, 1);
    performPositiveTest(inscription, 4);

    setVariableCostTo(VARIABlE_COST * 5);
    performPositiveTest(inscription, 14);
  }

  @Test
  void inscriptionWith1000Bytes() {
    final Bytes payload = Bytes.random(1000);
    final Transaction inscription =
        Transaction.builder()
            .sender(SENDER)
            .to(RECIPIENT)
            .gasLimit(36964)
            .gasPrice(ETH_GAS_PRICE)
            .payload(payload)
            .signature(FAKE_SIGNATURE)
            .value(Wei.ZERO)
            .build();
    performNegativeTest(inscription, 1);
    performPositiveTest(inscription, 5);

    setVariableCostTo(VARIABlE_COST * 5);
    performPositiveTest(inscription, 22);
  }

  @Test
  void smallExecutionGasTransaction() {
    final String contractCallArguments =
        "0x3e8b68c100000000000000000000000000000000000000000000000000000000000005dc000000000000000000000000000000000000000000000000000000000000001f";
    final Bytes payload = Bytes.fromHexString(contractCallArguments);
    final Transaction smallComputation =
        Transaction.builder()
            .sender(SENDER)
            .to(RECIPIENT)
            .gasLimit(80695)
            .gasPrice(ETH_GAS_PRICE)
            .payload(payload)
            .signature(FAKE_SIGNATURE)
            .value(Wei.ZERO)
            .build();
    performPositiveTest(smallComputation, 1);

    setVariableCostTo(VARIABlE_COST * 5);
    performPositiveTest(smallComputation, 2);
  }

  @Test
  void oracleTransaction() throws IOException {
    final String contractCallArguments =
        new String(getClass().getResourceAsStream("/oracle-transaction").readAllBytes());
    ;
    final Bytes payload = Bytes.fromHexString(contractCallArguments);
    final Wei gasPrice = Wei.of(5400000000L); // 5.4 Gwei
    final Transaction oracleTransaction =
        Transaction.builder()
            .sender(SENDER)
            .to(RECIPIENT)
            .gasLimit(150468)
            .gasPrice(gasPrice)
            .payload(payload)
            .signature(FAKE_SIGNATURE)
            .value(Wei.ZERO)
            .build();
    performNegativeTest(oracleTransaction, 1);

    setVariableCostTo(VARIABlE_COST * 5);
    performPositiveTest(oracleTransaction, 6);
  }

  private void setVariableCostTo(long newVariableCost) {
    profitabilityConfiguration.updateFixedAndVariableCost(FIXED_COST_WEI, newVariableCost);
  }

  String getErrorMessage(Wei actualProfitability, Wei setGasPrice) {
    final float profitabilityRatio =
        (float) setGasPrice.getAsBigInteger().longValue()
            / actualProfitability.getAsBigInteger().longValue();
    return "Profitability is %.2f, expected at least 1.0. Gas price is %d, profitability level is %d"
        .formatted(profitabilityRatio, ETH_GAS_PRICE.toLong(), actualProfitability.toLong());
  }

  void performPositiveTest(Transaction transaction, int expectedGasPriceMultipler) {
    final Wei expectedProfitabilityLevel = ETH_GAS_PRICE.multiply(expectedGasPriceMultipler);
    final Wei profitability =
        calculator.profitablePriorityFeePerGas(
            transaction,
            profitabilityConfiguration.minMargin(),
            transaction.getGasLimit(),
            Wei.ZERO);

    assertThat(profitability.getAsBigInteger())
        .withFailMessage(getErrorMessage(profitability, expectedProfitabilityLevel))
        .isLessThan(expectedProfitabilityLevel.getAsBigInteger());
  }

  void performNegativeTest(Transaction transaction, int expectedGasPriceMultipler) {
    final Wei expectedProfitabilityLevel = ETH_GAS_PRICE.multiply(expectedGasPriceMultipler);
    final Wei profitability =
        calculator.profitablePriorityFeePerGas(
            transaction,
            profitabilityConfiguration.minMargin(),
            transaction.getGasLimit(),
            Wei.ZERO);

    assertThat(profitability.getAsBigInteger())
        .withFailMessage(getErrorMessage(profitability, expectedProfitabilityLevel))
        .isGreaterThanOrEqualTo(expectedProfitabilityLevel.getAsBigInteger());
  }
}
