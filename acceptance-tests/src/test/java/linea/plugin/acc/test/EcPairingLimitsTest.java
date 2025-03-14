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
package linea.plugin.acc.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import linea.plugin.acc.test.tests.web3j.generated.EcPairing;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Numeric;

public class EcPairingLimitsTest extends LineaPluginTestBase {

  @Override
  public List<String> getTestCliOptions() {
    return new TestCommandLineOptionsBuilder()
        // disable line count validation to accept excluded precompile txs in the txpool
        .set("--plugin-linea-tx-pool-simulation-check-api-enabled=", "false")
        .set("--plugin-linea-module-limit-file-path=", getResourcePath("/moduleLimits.toml"))
        .build();
  }

  /*
  Structure of the input:
  Ax + Ay
  BxIm + BxRe
  ByIm + ByRe
  */

  // Requires 1 Miller Loop and 1 final exponentiation
  static final String nonTrivial =
      "01395d002b3ca9180fb924650ef0656ead838fd027d487fed681de0d674c30da097c3a9a072f9c85edf7a36812f8ee05e2cc73140749dcd7d29ceb34a8412188"
          + "2bd3295ff81c577fe772543783411c36f463676d9692ca4250588fbad0b44dc707d8d8329e62324af8091e3a4ffe5a57cb8664d1f5f6838c55261177118e9313"
          + "230f1851ba0d3d7d36c8603c7118c86bd2b6a7a1610c4af9e907cb702beff1d812843e703009c1c1a2f1088dcf4d91e9ed43189aa6327cae9a68be22a1aee5cb";

  // Requires 1 G2 membership check
  static final String leftTrivialValid =
      "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
          + "266152e278e5dab4e14f0d93a3e54550d08dc30ef4fe911257bd3e313864b85922cebabf989f812c0a6e67362bcb83d55c6378a4f500ecc8a6a5518b3d1695e0"
          + "070a5a339edbbb67c35d0d44b3ffff6b5803b198af7645c892f6af2fa8abf6f2117f82e731f61e688908fa2c831c6a1c7775e6f9cfd49e06d1d24d3d13e5936a";

  @ParameterizedTest
  @MethodSource("ecPairingLimitsTestSource")
  public void ecPairingLimitsTest(
      int nTransactions, BiFunction<Integer, Integer, String> input, String target)
      throws Exception {

    // Deploy the EcPairing contract
    final EcPairing ecPairing = deployEcPairing();

    // Create an account to send the transactions
    Account ecPairingSender = accounts.createAccount("ecPairingSender");

    // Fund the account using secondary benefactor
    String fundTxHash =
        accountTransactions
            .createTransfer(accounts.getSecondaryBenefactor(), ecPairingSender, 1, BigInteger.ZERO)
            .execute(minerNode.nodeRequests())
            .toHexString();
    // Verify that the transaction for transferring funds was successful
    minerNode.verify(eth.expectSuccessfulTransactionReceipt(fundTxHash));

    String[] txHashes = new String[nTransactions];
    for (int k = 0; k < nTransactions; k++) {
      // With decreasing nonce we force the transactions to be included in the same block
      // k     = 0                , 1                , ..., nTransactions - 1
      // nonce = nTransactions - 1, nTransactions - 2, ..., 0
      int nonce = nTransactions - 1 - k;

      // Craft the transaction data
      final byte[] encodedCallEcPairing =
          encodedCallEcPairing(
              ecPairing,
              ecPairingSender,
              nonce,
              Bytes.fromHexString(input.apply(k, nTransactions)));

      // Send the transaction
      final Web3j web3j = minerNode.nodeRequests().eth();
      final EthSendTransaction resp =
          web3j.ethSendRawTransaction(Numeric.toHexString(encodedCallEcPairing)).send();

      // Store the transaction hash
      txHashes[nonce] = resp.getTransactionHash();
    }

    // Transfer used as sentry to ensure a new block is mined
    final Hash transferTxHash =
        accountTransactions
            .createTransfer(
                accounts.getPrimaryBenefactor(),
                accounts.getSecondaryBenefactor(),
                1,
                BigInteger.ONE) // nonce is 1 as primary benefactor also deploys the contract
            .execute(minerNode.nodeRequests());
    // Wait for the sentry to be mined
    minerNode.verify(eth.expectSuccessfulTransactionReceipt(transferTxHash.toHexString()));

    // Assert that all the transactions involving the EcPairing precompile, but the last one, were
    // included in the same block
    assertTransactionsMinedInSameBlock(
        minerNode.nodeRequests().eth(), Arrays.asList(txHashes).subList(0, nTransactions - 1));

    // Assert that the last transaction was included in another block
    assertTransactionsMinedInSeparateBlocks(
        minerNode.nodeRequests().eth(), List.of(txHashes[0], txHashes[nTransactions - 1]));

    // Assert that the target string is contained in the block log
    final String blockLog = getAndResetLog();
    assertThat(blockLog).contains(target);
  }

  private static Stream<Arguments> ecPairingLimitsTestSource() {
    List<Arguments> arguments = new ArrayList<>();

    arguments.add(
        Arguments.of(
            17, // 1 final exponentiation per transaction
            (BiFunction<Integer, Integer, String>) (k, nTransactions) -> nonTrivial,
            "Cumulated line count for module PRECOMPILE_ECPAIRING_FINAL_EXPONENTIATIONS=17 is above the limit 16, stopping selection"));

    arguments.add(
        Arguments.of(
            9, // 8 Miller Loops per transaction except the last one which has 1
            (BiFunction<Integer, Integer, String>)
                (k, nTransactions) -> nonTrivial.repeat(k < nTransactions - 1 ? 8 : 1),
            "Cumulated line count for module PRECOMPILE_ECPAIRING_MILLER_LOOPS=65 is above the limit 64, stopping selection"));

    arguments.add(
        Arguments.of(
            9, // 8 g2 membership checks per transaction except the last one which has 1
            (BiFunction<Integer, Integer, String>)
                (k, nTransactions) -> leftTrivialValid.repeat(k < nTransactions - 1 ? 8 : 1),
            "Cumulated line count for module PRECOMPILE_ECPAIRING_G2_MEMBERSHIP_CALLS=65 is above the limit 64, stopping selection"));

    return arguments.stream();
  }
}
