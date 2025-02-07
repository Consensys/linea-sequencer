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
package linea.plugin.acc.test.rpc.linea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.web3j.crypto.Hash.sha3;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

import linea.plugin.acc.test.LineaPluginTestBase;
import linea.plugin.acc.test.tests.web3j.generated.AcceptanceTestToken;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.blockchain.Amount;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.account.TransferTransaction;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.core.Request;
import org.web3j.utils.Numeric;

public class SendBundleTest extends LineaPluginTestBase {
  private static final BigInteger TRANSFER_GAS_LIMIT = BigInteger.valueOf(100000L);
  private static final BigInteger TRANSFER_GAS_PRICE = BigInteger.TEN.pow(9);

  @Test
  public void singleTxBundleIsAcceptedAndMined() {
    final Account sender = accounts.getSecondaryBenefactor();
    final Account recipient = accounts.getPrimaryBenefactor();

    final TransferTransaction tx = accountTransactions.createTransfer(sender, recipient, 1);

    final String rawTx = tx.signedTransactionData();

    final var sendBundleRequest =
        new SendBundleRequest(new BundleParams(new String[] {rawTx}, Integer.toHexString(1)));
    final var sendBundleResponse = sendBundleRequest.execute(minerNode.nodeRequests());

    assertThat(sendBundleResponse.hasError()).isFalse();
    assertThat(sendBundleResponse.getResult().bundleHash()).isNotBlank();

    minerNode.verify(eth.expectSuccessfulTransactionReceipt(tx.transactionHash()));
  }

  @Test
  public void bundleIsAcceptedAndMined() {
    final Account sender = accounts.getSecondaryBenefactor();
    final Account recipient = accounts.getPrimaryBenefactor();

    final TransferTransaction tx1 = accountTransactions.createTransfer(sender, recipient, 1);
    final TransferTransaction tx2 = accountTransactions.createTransfer(recipient, sender, 1);

    final String[] rawTxs = new String[] {tx1.signedTransactionData(), tx2.signedTransactionData()};

    final var sendBundleRequest =
        new SendBundleRequest(new BundleParams(rawTxs, Integer.toHexString(1)));
    final var sendBundleResponse = sendBundleRequest.execute(minerNode.nodeRequests());

    assertThat(sendBundleResponse.hasError()).isFalse();
    assertThat(sendBundleResponse.getResult().bundleHash()).isNotBlank();

    minerNode.verify(eth.expectSuccessfulTransactionReceipt(tx1.transactionHash()));
    minerNode.verify(eth.expectSuccessfulTransactionReceipt(tx2.transactionHash()));
  }

  @Test
  public void distributeTokensInBundle() throws Exception {
    final AcceptanceTestToken token = deployAcceptanceTestToken();

    final int numOfTransfers = 10;

    final TokenTransfer[] tokenTransfers = new TokenTransfer[numOfTransfers];
    for (int i = 0; i < numOfTransfers; i++) {
      tokenTransfers[i] =
          transferTokens(
              token,
              accounts.getPrimaryBenefactor(),
              i + 1,
              accounts.createAccount("recipient " + i),
              1);
    }

    final var rawTxs =
        Arrays.stream(tokenTransfers).map(TokenTransfer::rawTx).toArray(String[]::new);

    final var sendBundleRequest =
        new SendBundleRequest(new BundleParams(rawTxs, Integer.toHexString(2)));
    final var sendBundleResponse = sendBundleRequest.execute(minerNode.nodeRequests());

    assertThat(sendBundleResponse.hasError()).isFalse();
    assertThat(sendBundleResponse.getResult().bundleHash()).isNotBlank();

    Arrays.stream(tokenTransfers)
        .forEach(
            tokenTransfer -> {
              minerNode.verify(eth.expectSuccessfulTransactionReceipt(tokenTransfer.txHash));
              try {
                assertThat(token.balanceOf(tokenTransfer.recipient.getAddress()).send())
                    .isEqualTo(1);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  @Test
  public void payGasWithTokensInBundle() throws Exception {
    final AcceptanceTestToken token = deployAcceptanceTestToken();

    final var recipient = accounts.createAccount("recipient");
    final var transferReceipt = token.transfer(recipient.getAddress(), BigInteger.TEN).send();
    assertThat(transferReceipt.isStatusOK()).isTrue();
    assertThat(token.balanceOf(recipient.getAddress()).send()).isEqualTo(10);

    final var transferGasTx =
        accountTransactions.createTransfer(accounts.getSecondaryBenefactor(), recipient, 1);
    final var payGasWithTokenRawTx =
        transferTokens(token, recipient, 0, accounts.getSecondaryBenefactor(), 1);

    final var rawTxs =
        new String[] {transferGasTx.signedTransactionData(), payGasWithTokenRawTx.rawTx()};

    final var sendBundleRequest =
        new SendBundleRequest(new BundleParams(rawTxs, Integer.toHexString(3)));
    final var sendBundleResponse = sendBundleRequest.execute(minerNode.nodeRequests());

    assertThat(sendBundleResponse.hasError()).isFalse();
    assertThat(sendBundleResponse.getResult().bundleHash()).isNotBlank();

    minerNode.verify(eth.expectSuccessfulTransactionReceipt(transferGasTx.transactionHash()));
    minerNode.verify(eth.expectSuccessfulTransactionReceipt(payGasWithTokenRawTx.txHash()));

    final var payGasWithTokenReceipt =
        ethTransactions
            .getTransactionReceipt(payGasWithTokenRawTx.txHash())
            .execute(minerNode.nodeRequests())
            .orElseThrow();
    final var gasPrice =
        Wei.fromHexString(payGasWithTokenReceipt.getEffectiveGasPrice()).toBigInteger();

    final var expectedBalance =
        Amount.ether(1)
            .subtract(Amount.wei(gasPrice.multiply(payGasWithTokenReceipt.getGasUsed())));

    minerNode.verify(recipient.balanceEquals(expectedBalance));

    assertThat(token.balanceOf(recipient.getAddress()).send()).isEqualTo(9);
    assertThat(token.balanceOf(accounts.getSecondaryBenefactor().getAddress()).send()).isEqualTo(1);
  }

  private TokenTransfer transferTokens(
      final AcceptanceTestToken token,
      final Account sender,
      final int nonce,
      final Account recipient,
      final int amount) {
    final var transferCalldata =
        token.transfer(recipient.getAddress(), BigInteger.valueOf(amount)).encodeFunctionCall();

    final var transferTx =
        RawTransaction.createTransaction(
            CHAIN_ID,
            BigInteger.valueOf(nonce),
            TRANSFER_GAS_LIMIT,
            token.getContractAddress(),
            BigInteger.ZERO,
            transferCalldata,
            TRANSFER_GAS_PRICE,
            TRANSFER_GAS_PRICE.multiply(BigInteger.TEN).add(BigInteger.ONE));

    final String signedTransferTx =
        Numeric.toHexString(
            TransactionEncoder.signMessage(transferTx, sender.web3jCredentialsOrThrow()));

    final String hashTx = sha3(signedTransferTx);

    return new TokenTransfer(recipient, hashTx, signedTransferTx);
  }

  @RequiredArgsConstructor
  static class SendBundleRequest implements Transaction<SendBundleRequest.SendBundleResponse> {
    private final BundleParams bundleParams;

    @Override
    public SendBundleResponse execute(final NodeRequests nodeRequests) {
      try {
        return new Request<>(
                "linea_sendBundle",
                Arrays.asList(bundleParams),
                nodeRequests.getWeb3jService(),
                SendBundleResponse.class)
            .send();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    static class SendBundleResponse extends org.web3j.protocol.core.Response<Response> {}

    record Response(String bundleHash) {}
  }

  record BundleParams(String[] txs, String blockNumber) {}

  record TokenTransfer(Account recipient, String txHash, String rawTx) {}
}
