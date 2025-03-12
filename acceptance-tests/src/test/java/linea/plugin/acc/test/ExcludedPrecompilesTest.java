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

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import linea.plugin.acc.test.tests.web3j.generated.ExcludedPrecompiles;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.web3j.abi.datatypes.generated.Bytes8;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

public class ExcludedPrecompilesTest extends LineaPluginTestBase {
  private static final BigInteger GAS_LIMIT = DefaultGasProvider.GAS_LIMIT;
  private static final BigInteger GAS_PRICE = DefaultGasProvider.GAS_PRICE;

  @Override
  public List<String> getTestCliOptions() {
    return new TestCommandLineOptionsBuilder()
        // disable line count validation to accept excluded precompile txs in the txpool
        .set("--plugin-linea-tx-pool-simulation-check-api-enabled=", "false")
        .set("--plugin-linea-module-limit-file-path=", getResourcePath("/moduleLimits.toml"))
        .build();
  }

  @Test
  public void transactionsWithExcludedPrecompilesAreNotAccepted() throws Exception {
    final ExcludedPrecompiles excludedPrecompiles = deployExcludedPrecompiles();
    final Web3j web3j = minerNode.nodeRequests().eth();
    final String contractAddress = excludedPrecompiles.getContractAddress();

    // fund a new account
    final var recipient = accounts.createAccount("recipient");
    final var txHashFundRecipient =
        accountTransactions
            .createTransfer(accounts.getPrimaryBenefactor(), recipient, 10, BigInteger.valueOf(1))
            .execute(minerNode.nodeRequests());
    minerNode.verify(eth.expectSuccessfulTransactionReceipt(txHashFundRecipient.toHexString()));

    record InvalidCall(
        String senderPrivateKey, int nonce, String encodedContractCall, String expectedTraceLog) {}

    final InvalidCall[] invalidCalls = {
      new InvalidCall(
          Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY,
          2,
          excludedPrecompiles
              .callRIPEMD160("I am not allowed here".getBytes(StandardCharsets.UTF_8))
              .encodeFunctionCall(),
          "Tx 0xe4648fd59d4289e59b112bf60931336440d306c85c2aac5a8b0c64ab35bc55b7 line count for module PRECOMPILE_RIPEMD_BLOCKS=1 is above the limit 0"),
      new InvalidCall(
          Accounts.GENESIS_ACCOUNT_TWO_PRIVATE_KEY,
          0,
          encodedCallBlake2F(excludedPrecompiles),
          "Tx 0x9f457b1b5244b03c54234f7f9e8225d4253135dd3c99a46dc527d115e7ea5dac line count for module PRECOMPILE_BLAKE_ROUNDS=12 is above the limit 0")
    };

    final var invalidTxHashes =
        Arrays.stream(invalidCalls)
            .map(
                invalidCall -> {
                  // this tx must not be accepted but not mined
                  final RawTransaction txInvalid =
                      RawTransaction.createTransaction(
                          CHAIN_ID,
                          BigInteger.valueOf(invalidCall.nonce),
                          GAS_LIMIT.divide(BigInteger.TEN),
                          contractAddress,
                          BigInteger.ZERO,
                          invalidCall.encodedContractCall,
                          GAS_PRICE,
                          GAS_PRICE);

                  final byte[] signedTxInvalid =
                      TransactionEncoder.signMessage(
                          txInvalid, Credentials.create(invalidCall.senderPrivateKey));

                  final EthSendTransaction signedTxInvalidResp;
                  try {
                    signedTxInvalidResp =
                        web3j.ethSendRawTransaction(Numeric.toHexString(signedTxInvalid)).send();
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }

                  assertThat(signedTxInvalidResp.hasError()).isFalse();
                  return signedTxInvalidResp.getTransactionHash();
                })
            .toList();

    assertThat(getTxPoolContent()).hasSize(invalidTxHashes.size());

    // transfer used as sentry to ensure a new block is mined without the invalid txs
    final var transferTxHash1 =
        accountTransactions
            .createTransfer(recipient, accounts.getSecondaryBenefactor(), 1)
            .execute(minerNode.nodeRequests());

    // first sentry is mined and no tx of the bundle is mined
    minerNode.verify(eth.expectSuccessfulTransactionReceipt(transferTxHash1.toHexString()));
    Arrays.stream(invalidCalls)
        .forEach(
            invalidCall ->
                minerNode.verify(
                    eth.expectNoTransactionReceipt(Hash.sha3(invalidCall.encodedContractCall))));

    final String log = getLog();
    // verify trace log contains the exclusion cause
    Arrays.stream(invalidCalls)
        .forEach(invalidCall -> assertThat(log).contains(invalidCall.expectedTraceLog));
  }

  @Test
  public void invalidModExpCallsAreNotMined() throws Exception {
    final var modExp = deployModExp();

    final var modExpSenders = new Account[3];
    final var foundTxHashes = new String[3];
    for (int i = 0; i < 3; i++) {
      modExpSenders[i] = accounts.createAccount("sender" + i);
      foundTxHashes[i] =
          accountTransactions
              .createTransfer(
                  accounts.getSecondaryBenefactor(), modExpSenders[i], 1, BigInteger.valueOf(i))
              .execute(minerNode.nodeRequests())
              .toHexString();
    }
    Arrays.stream(foundTxHashes)
        .forEach(
            fundTxHash -> minerNode.verify(eth.expectSuccessfulTransactionReceipt(fundTxHash)));

    final Bytes[][] invalidInputs = {
      {Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000201")},
      {
        Bytes.fromHexString("00000000000000000000000000000000000000000000000000000000000003"),
        Bytes.fromHexString("ff")
      },
      {
        Bytes.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"),
        Bytes.fromHexString("00000000000000000000000000000000000000000000000000000000000003"),
        Bytes.fromHexString("ff")
      }
    };

    for (int i = 0; i < invalidInputs.length; i++) {
      final var invalidCallTxHashes = new String[invalidInputs[i].length];
      for (int j = 0; j < invalidInputs[i].length; j++) {

        // use always the same nonce since we expect this tx not to be mined
        final var mulmodOverflow =
            encodedCallModExp(modExp, modExpSenders[j], 0, invalidInputs[i][j]);

        final Web3j web3j = minerNode.nodeRequests().eth();
        final EthSendTransaction resp =
            web3j.ethSendRawTransaction(Numeric.toHexString(mulmodOverflow)).send();
        invalidCallTxHashes[j] = resp.getTransactionHash();
      }

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
      final var blockLog = getAndResetLog();
      Arrays.stream(invalidCallTxHashes)
          .forEach(
              invalidCallTxHash -> {
                minerNode.verify(eth.expectNoTransactionReceipt(invalidCallTxHash));
                assertThat(blockLog)
                    .contains(
                        "Tx "
                            + invalidCallTxHash
                            + " line count for module PRECOMPILE_MODEXP_EFFECTIVE_CALLS=2147483647 is above the limit 10000, removing from the txpool");
              });
    }
  }

  @Test
  public void ecPairingTest() throws Exception {
    final var ecPairing = deployEcPairing();

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

    String inputTwoNonTrivialValidPairing = "01395d002b3ca9180fb924650ef0656ead838fd027d487fed681de0d674c30da097c3a9a072f9c85edf7a36812f8ee05e2cc73140749dcd7d29ceb34a84121882bd3295ff81c577fe772543783411c36f463676d9692ca4250588fbad0b44dc707d8d8329e62324af8091e3a4ffe5a57cb8664d1f5f6838c55261177118e9313230f1851ba0d3d7d36c8603c7118c86bd2b6a7a1610c4af9e907cb702beff1d812843e703009c1c1a2f1088dcf4d91e9ed43189aa6327cae9a68be22a1aee5cb05dcb6449ff95e1a04c3132ce3be82a897811d2087e082e0399985449942a45b0cb5122006e9b7ceb5307fa4015b132b3945bb972c83459f598659fc4b5a9d32127a664dd11342beb666506dac296731e404de80a25e05f40b2405c4c00c28fc2bd236cb7a7b0e0543e6b6e0d7308576aeeec4dea2f740654854215d7813826f1b28f411c2931b52b2ad62de524be4eaac555dfed67d59e2d0f6c4607b23526b181c4319cc974dd174c5918ac1892326badb2603a04bc8f565221c06eec8a126";

    final Bytes[][] invalidInputs = {
      {Bytes.fromHexString(inputTwoNonTrivialValidPairing)},
    };

    for (int i = 0; i < invalidInputs.length; i++) {
      final var invalidCallTxHashes = new String[invalidInputs[i].length];
      for (int j = 0; j < invalidInputs[i].length; j++) {

        // use always the same nonce since we expect this tx not to be mined
        for (int k = 0; k < 32; k++) {
          final var mulmodOverflow =
            encodedCallEcPairing(ecPairing, ecPairingSenders[j], 31 - k, invalidInputs[i][j]);
          // With decreasing nonce we force the transactions to be mined in the same block

          final Web3j web3j = minerNode.nodeRequests().eth();
          final EthSendTransaction resp =
            web3j.ethSendRawTransaction(Numeric.toHexString(mulmodOverflow)).send();
          invalidCallTxHashes[j] = resp.getTransactionHash();
        }
      }

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

      // Check LineaPluginTestBase::assertTransactionsMinedInSameBlock

      final var blockLog = getAndResetLog();
      Arrays.stream(invalidCallTxHashes)
        .forEach(
          invalidCallTxHash -> {
            minerNode.verify(eth.expectNoTransactionReceipt(invalidCallTxHash));
            assertThat(blockLog)
              .contains(
                "Tx "
                  + invalidCallTxHash
                  + " line count for module PRECOMPILE_MODEXP_EFFECTIVE_CALLS=2147483647 is above the limit 10000, removing from the txpool");
          });
    }
  }

  private String encodedCallBlake2F(final ExcludedPrecompiles excludedPrecompiles) {
    return excludedPrecompiles
        .callBlake2f(
            BigInteger.valueOf(12),
            List.of(
                Bytes32.fromHexString(
                        "0x48c9bdf267e6096a3ba7ca8485ae67bb2bf894fe72f36e3cf1361d5f3af54fa5")
                    .toArrayUnsafe(),
                Bytes32.fromHexString(
                        "0xd182e6ad7f520e511f6c3e2b8c68059b6bbd41fbabd9831f79217e1319cde05b")
                    .toArrayUnsafe()),
            List.of(
                Bytes32.fromHexString(
                        "0x6162630000000000000000000000000000000000000000000000000000000000")
                    .toArrayUnsafe(),
                Bytes32.ZERO.toArrayUnsafe(),
                Bytes32.ZERO.toArrayUnsafe(),
                Bytes32.ZERO.toArrayUnsafe()),
            List.of(Bytes8.DEFAULT.getValue(), Bytes8.DEFAULT.getValue()),
            true)
        .encodeFunctionCall();
  }
}
