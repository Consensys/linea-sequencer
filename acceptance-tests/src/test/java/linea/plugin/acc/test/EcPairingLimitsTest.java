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
import java.util.Arrays;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.junit.jupiter.api.Test;
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

  @Test
  public void ecPairingTest() throws Exception {
    final var ecPairing = deployEcPairing();

    // TODO: fix naming and remove unnecessary stuff, use
    // ExcludedPrecompilesTest.invalidModExpCallsAreNotMined
    //  as reference
    final var ecPairingSenders = new Account[3];
    final var fundTxHashes = new String[3];

    // Send funds to the accounts so as those accounts can pay for their own transactions
    for (int i = 0; i < 3; i++) {
      ecPairingSenders[i] = accounts.createAccount("sender" + i);
      fundTxHashes[i] =
          accountTransactions
              .createTransfer(
                  accounts.getSecondaryBenefactor(), ecPairingSenders[i], 1, BigInteger.valueOf(i))
              .execute(minerNode.nodeRequests())
              .toHexString();
    }

    // Verify that the transactions for transferring funds were successful
    Arrays.stream(fundTxHashes)
        .forEach(
            fundTxHash -> minerNode.verify(eth.expectSuccessfulTransactionReceipt(fundTxHash)));

    // Actual input we send to the ECPAIRING contract
    // transactionInputs x block

    String inputTwoNonTrivialValidPairing =
        "01395d002b3ca9180fb924650ef0656ead838fd027d487fed681de0d674c30da097c3a9a072f9c85edf7a36812f8ee05e2cc73140749dcd7d29ceb34a84121882bd3295ff81c577fe772543783411c36f463676d9692ca4250588fbad0b44dc707d8d8329e62324af8091e3a4ffe5a57cb8664d1f5f6838c55261177118e9313230f1851ba0d3d7d36c8603c7118c86bd2b6a7a1610c4af9e907cb702beff1d812843e703009c1c1a2f1088dcf4d91e9ed43189aa6327cae9a68be22a1aee5cb05dcb6449ff95e1a04c3132ce3be82a897811d2087e082e0399985449942a45b0cb5122006e9b7ceb5307fa4015b132b3945bb972c83459f598659fc4b5a9d32127a664dd11342beb666506dac296731e404de80a25e05f40b2405c4c00c28fc2bd236cb7a7b0e0543e6b6e0d7308576aeeec4dea2f740654854215d7813826f1b28f411c2931b52b2ad62de524be4eaac555dfed67d59e2d0f6c4607b23526b181c4319cc974dd174c5918ac1892326badb2603a04bc8f565221c06eec8a126";

    final Bytes[][] invalidInputs = {
      {Bytes.fromHexString(inputTwoNonTrivialValidPairing)},
    };

    for (int i = 0; i < invalidInputs.length; i++) {
      final var invalidCallTxHashes = new String[invalidInputs[i].length];
      for (int j = 0; j < invalidInputs[i].length; j++) {

        int nTransactions = 17;
        for (int k = 0; k < nTransactions; k++) {
          final var mulmodOverflow =
              encodedCallEcPairing(
                  ecPairing, ecPairingSenders[j], nTransactions - 1 - k, invalidInputs[i][j]);
          // With decreasing nonce we force the transactions to be mined in the same block

          final Web3j web3j = minerNode.nodeRequests().eth();
          final EthSendTransaction resp =
              web3j.ethSendRawTransaction(Numeric.toHexString(mulmodOverflow)).send();
          invalidCallTxHashes[j] = resp.getTransactionHash();
        }
      }

      // TODO: is this necessary?
      // transfer used as sentry to ensure a new block is mined without the invalid modexp call
      final var transferTxHash =
          accountTransactions
              .createTransfer(
                  accounts.getPrimaryBenefactor(),
                  accounts.getSecondaryBenefactor(),
                  1,
                  BigInteger.valueOf(i + 1))
              .execute(minerNode.nodeRequests());

      // sentry is mined and the invalid modexp txs are not
      minerNode.verify(eth.expectSuccessfulTransactionReceipt(transferTxHash.toHexString()));

      // TODO: check LineaPluginTestBase::assertTransactionsMinedInSameBlock

      final var blockLog = getAndResetLog();
      assertThat(blockLog)
          .contains(
              "Cumulated line count for module PRECOMPILE_ECPAIRING_FINAL_EXPONENTIATIONS=17 is above the limit 16, stopping selection");

      // TODO: we may want to assert that it is exactly the 17th transaction that is not mined

      /*
      Arrays.stream(invalidCallTxHashes)
          .forEach(
              invalidCallTxHash -> {
                minerNode.verify(eth.expectNoTransactionReceipt(invalidCallTxHash));
                assertThat(blockLog)
                    .contains(
                      "Cumulated line count for module PRECOMPILE_ECPAIRING_FINAL_EXPONENTIATIONS=17 is above the limit 16, stopping selection");
              });
      */
    }
  }
}
