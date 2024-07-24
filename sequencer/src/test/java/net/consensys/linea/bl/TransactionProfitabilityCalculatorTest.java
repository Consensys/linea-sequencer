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
  public static final Long VARIABlE_COST =
      (long)
          (3287000L
              / 0.0006); // 3287000 Wei is average from Mainnet over 16 days with 0.0006 margin
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
  void plainTransferIsProfitable() {
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
  void contractDeploymentIsProfitable() {
    final String contractDeploymentHex =
        "608060405234801561001057600080fd5b50610786806100206000396000f3fe608060405234801561001057600080fd5b50600436106100ea5760003560e01c806381cec0fe1161008c578063c7dd97f611610066578063c7dd97f6146101c0578063d75522da146101d3578063de36d657146101f4578063eb6c7bc01461022b57600080fd5b806381cec0fe146101905780639b0e317c146101a3578063c2d7ff5b146101ad57600080fd5b8063445a31dc116100c8578063445a31dc1461012a57806348286a551461014a57806365e884381461016a5780636bd969ad1461017d57600080fd5b8063101eb93a146100ef57806314b05a3814610104578063293fd05d146100ef575b600080fd5b6101026100fd3660046104eb565b610260565b005b6101176101123660046105a9565b610291565b6040519081526020015b60405180910390f35b610117610138366004610623565b60016020526000908152604090205481565b61015d6101583660046105a9565b6102c1565b6040516101219190610695565b6101026101783660046104eb565b6102ea565b61015d61018b3660046105a9565b610322565b61010261019e3660046106a8565b610335565b6000546101179081565b6101026101bb3660046104eb565b61049c565b6101176101ce3660046105a9565b6104d8565b6101026101e13660046104eb565b6040805160208101909152819052600055565b61010261020236600461070a565b73ffffffffffffffffffffffffffffffffffffffff909116600090815260016020526040902055565b610102610239366004610623565b73ffffffffffffffffffffffffffffffffffffffff16600090815260016020526040812055565b60005b8181101561028d5760408051602081018390520160408051601f1981840301905252600101610263565b5050565b6000816040516020016102a49190610734565b604051602081830303815290604052805190602001209050919050565b6060816040516020016102d49190610695565b6040516020818303038152906040529050919050565b60405181815233907f2a1343a7ef16865394327596242ebb1d13cafbd9dbb29027e89cbc0212cfa7379060200160405180910390a250565b6060816040516020016102d49190610734565b60008273ffffffffffffffffffffffffffffffffffffffff168260405161035c9190610734565b6000604051808303816000865af19150503d8060008114610399576040519150601f19603f3d011682016040523d82523d6000602084013e61039e565b606091505b50509050806103f45760405162461bcd60e51b815260206004820152600b60248201527f43616c6c206661696c656400000000000000000000000000000000000000000060448201526064015b60405180910390fd5b6040513090610404908490610734565b600060405180830381855af49150503d806000811461043f576040519150601f19603f3d011682016040523d82523d6000602084013e610444565b606091505b505080915050806104975760405162461bcd60e51b815260206004820152601360248201527f44656c656761746563616c6c206661696c65640000000000000000000000000060448201526064016103eb565b505050565b60408051338152602081018390527f86a4f961b36de6c45328ad9f8656c035c3f2414b5710cb261e19c9b0a8851203910160405180910390a150565b6000816040516020016102a49190610695565b6000602082840312156104fd57600080fd5b5035919050565b7f4e487b7100000000000000000000000000000000000000000000000000000000600052604160045260246000fd5b600067ffffffffffffffff8084111561054e5761054e610504565b604051601f8501601f19908116603f0116810190828211818310171561057657610576610504565b8160405280935085815286868601111561058f57600080fd5b858560208301376000602087830101525050509392505050565b6000602082840312156105bb57600080fd5b813567ffffffffffffffff8111156105d257600080fd5b8201601f810184136105e357600080fd5b6105f284823560208401610533565b949350505050565b803573ffffffffffffffffffffffffffffffffffffffff8116811461061e57600080fd5b919050565b60006020828403121561063557600080fd5b61063e826105fa565b9392505050565b60005b83811015610660578181015183820152602001610648565b50506000910152565b60008151808452610681816020860160208601610645565b601f01601f19169290920160200192915050565b60208152600061063e6020830184610669565b600080604083850312156106bb57600080fd5b6106c4836105fa565b9150602083013567ffffffffffffffff8111156106e057600080fd5b8301601f810185136106f157600080fd5b61070085823560208401610533565b9150509250929050565b6000806040838503121561071d57600080fd5b610726836105fa565b946020939093013593505050565b60008251610746818460208701610645565b919091019291505056fea26469706673582212206a5fe4a4435dd1162f95c145813599e1f2c026226600b1d12e15fb4a09fa9a1c64736f6c63430008160033";
    final Bytes payload = Bytes.fromHexString(contractDeploymentHex);
    final Transaction plainTransfer =
        Transaction.builder()
            .sender(SENDER)
            .gasLimit(468428)
            .gasPrice(ETH_GAS_PRICE)
            .payload(payload)
            .signature(FAKE_SIGNATURE)
            .value(Wei.ZERO)
            .build();
    performPositiveTest(plainTransfer, 1);

    setVariableCostTo(VARIABlE_COST * 5);
    performPositiveTest(plainTransfer, 3);
  }

  @Test
  void inscriptionWith250BytesIsProfitable() {
    final Bytes payload = Bytes.random(250);
    final Transaction plainTransfer =
        Transaction.builder()
            .sender(SENDER)
            .to(RECIPIENT)
            .gasLimit(22984)
            .gasPrice(ETH_GAS_PRICE)
            .payload(payload)
            .signature(FAKE_SIGNATURE)
            .value(Wei.ZERO)
            .build();

    performNegativeTest(plainTransfer, 1);
    performPositiveTest(plainTransfer, 3);

    setVariableCostTo(VARIABlE_COST * 5);
    performNegativeTest(plainTransfer, 5);
    performPositiveTest(plainTransfer, 9);
  }

  @Test
  void inscriptionWith500BytesIsProfitable() {
    final Bytes payload = Bytes.random(500);
    final Transaction plainTransfer =
        Transaction.builder()
            .sender(SENDER)
            .to(RECIPIENT)
            .gasLimit(24972)
            .gasPrice(ETH_GAS_PRICE)
            .payload(payload)
            .signature(FAKE_SIGNATURE)
            .value(Wei.ZERO)
            .build();

    performNegativeTest(plainTransfer, 1);
    performPositiveTest(plainTransfer, 4);

    setVariableCostTo(VARIABlE_COST * 5);
    performPositiveTest(plainTransfer, 14);
  }

  @Test
  void inscriptionWith1000BytesIsNotProfitable() {
    final Bytes payload = Bytes.random(1000);
    final Transaction plainTransfer =
        Transaction.builder()
            .sender(SENDER)
            .to(RECIPIENT)
            .gasLimit(28900)
            .gasPrice(ETH_GAS_PRICE)
            .payload(payload)
            .signature(FAKE_SIGNATURE)
            .value(Wei.ZERO)
            .build();
    performNegativeTest(plainTransfer, 1);
    performPositiveTest(plainTransfer, 5);

    setVariableCostTo(VARIABlE_COST * 5);
    performPositiveTest(plainTransfer, 22);
  }

  @Test
  void smallExecutionGasTransactionIsProfitable() {
    final String contractCallArguments =
        "0x3e8b68c100000000000000000000000000000000000000000000000000000000000005dc000000000000000000000000000000000000000000000000000000000000001f";
    final Bytes payload = Bytes.fromHexString(contractCallArguments);
    final Transaction plainTransfer =
        Transaction.builder()
            .sender(SENDER)
            .to(RECIPIENT)
            .gasLimit(80695)
            .gasPrice(ETH_GAS_PRICE)
            .payload(payload)
            .signature(FAKE_SIGNATURE)
            .value(Wei.ZERO)
            .build();
    performPositiveTest(plainTransfer, 1);

    setVariableCostTo(VARIABlE_COST * 5);
    performPositiveTest(plainTransfer, 2);
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
