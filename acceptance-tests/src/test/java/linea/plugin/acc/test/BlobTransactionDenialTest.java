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

import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

/**
 * Tests that verify the LineaPermissioningPlugin correctly rejects BLOB transactions while allowing
 * other transaction types.
 */
public class BlobTransactionDenialTest extends LineaPluginTestBase {

  private static final BigInteger GAS_PRICE = DefaultGasProvider.GAS_PRICE;
  private static final BigInteger GAS_LIMIT = DefaultGasProvider.GAS_LIMIT;
  private static final BigInteger VALUE = BigInteger.ZERO;
  private static final String DATA = "0x";

  private Web3j web3j;
  private Credentials credentials;
  private TransactionManager txManager;
  private String recipient;

  @Override
  @BeforeEach
  public void setup() throws Exception {
    super.setup();
    web3j = minerNode.nodeRequests().eth();
    credentials = Credentials.create(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY);
    txManager = new RawTransactionManager(web3j, credentials, CHAIN_ID);
    recipient = accounts.getSecondaryBenefactor().getAddress();
  }

  @Test
  public void legacyTransactionsAreAccepted() throws Exception {
    // Act - Send a legacy transaction
    String txHash =
        txManager
            .sendTransaction(GAS_PRICE, GAS_LIMIT, recipient, DATA, VALUE)
            .getTransactionHash();

    // Assert
    minerNode.verify(eth.expectSuccessfulTransactionReceipt(txHash));
  }

  @Test
  public void eip1559TransactionsAreAccepted() throws Exception {
    // Act - Send an EIP-1559 transaction
    String txHash =
        txManager
            .sendEIP1559Transaction(
                CHAIN_ID, GAS_PRICE, GAS_PRICE, GAS_LIMIT, recipient, DATA, VALUE)
            .getTransactionHash();

    // Assert
    minerNode.verify(eth.expectSuccessfulTransactionReceipt(txHash));
  }

  @Test
  public void blobTransactionsAreRejected() throws Exception {
    // Send a blob transaction
    EthSendTransaction response = sendRawBlobTransaction();

    // Verify the transaction was rejected
    assertThat(response.hasError()).isTrue();
    assertThat(response.getError().getMessage()).contains("transaction type not supported");
  }

  // TODO - Test that block import from one node to another fails for blob tx

  /**
   * Helper method to send a raw blob transaction.
   *
   * @return The response from the node
   * @throws IOException If there's an error sending the transaction
   */
  private EthSendTransaction sendRawBlobTransaction() throws IOException {
    // Get the next nonce
    BigInteger nonce =
        web3j
            .ethGetTransactionCount(
                credentials.getAddress(), org.web3j.protocol.core.DefaultBlockParameterName.PENDING)
            .send()
            .getTransactionCount();

    RawTransaction rawTransaction =
        RawTransaction.createTransaction(
            CHAIN_ID,
            nonce,
            GAS_LIMIT,
            recipient,
            VALUE,
            Numeric.toHexString(Numeric.hexStringToByteArray(DATA)),
            GAS_PRICE,
            GAS_PRICE);

    // Sign the transaction
    byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);

    // Modify the transaction type to BLOB (0x03)
    byte[] modifiedSignedMessage = new byte[signedMessage.length + 1];
    modifiedSignedMessage[0] = 0x03; // BLOB transaction type
    System.arraycopy(signedMessage, 1, modifiedSignedMessage, 1, signedMessage.length - 1);

    // Send the raw transaction
    return web3j.ethSendRawTransaction(Numeric.toHexString(modifiedSignedMessage)).send();
  }
}
