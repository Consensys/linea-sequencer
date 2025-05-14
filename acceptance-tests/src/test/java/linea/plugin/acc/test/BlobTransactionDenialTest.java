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
import java.util.ArrayList;
import java.util.List;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendRawTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

/**
 * Tests that verify the LineaPermissioningPlugin correctly rejects BLOB transactions
 * while allowing other transaction types.
 */
public class BlobTransactionDenialTest extends LineaPluginTestBase {

  private static final BigInteger GAS_PRICE = DefaultGasProvider.GAS_PRICE;
  private static final BigInteger GAS_LIMIT = DefaultGasProvider.GAS_LIMIT;
  private static final BigInteger VALUE = BigInteger.ZERO;
  private Web3j web3j;
  private Credentials credentials;
  private TransactionManager txManager;

  @Override
  @BeforeEach
  public void setup() throws Exception {
    super.setup();
    web3j = minerNode.nodeRequests().eth();
    credentials = Credentials.create(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY);
    txManager = new RawTransactionManager(web3j, credentials, CHAIN_ID);
  }

  @Test
  public void blobTransactionsAreRejected() throws Exception {
    // Create a blob transaction
    String recipient = accounts.getSecondaryBenefactor().getAddress();
    String data = "0x"; // Empty data
    
    // Send a raw blob transaction
    EthSendRawTransaction response = sendRawBlobTransaction(recipient, data);
    
    // Verify the transaction was rejected
    assertThat(response.hasError()).isTrue();
    assertThat(response.getError().getMessage()).contains("transaction type not supported");
  }

  @Test
  public void nonBlobTransactionsAreAccepted() throws Exception {
    // Create a regular transaction
    String recipient = accounts.getSecondaryBenefactor().getAddress();
    String data = "0x"; // Empty data
    
    // Send a regular transaction
    String txHash = txManager.sendTransaction(
        GAS_PRICE, 
        GAS_LIMIT, 
        recipient, 
        data, 
        VALUE
    ).getTransactionHash();
    
    // Verify the transaction was accepted and mined
    TransactionReceipt receipt = txManager.getTransactionReceipt(txHash).orElse(null);
    assertThat(receipt).isNotNull();
    assertThat(receipt.isStatusOK()).isTrue();
  }

  @Test
  public void mixedTransactionTypesAreFilteredCorrectly() throws Exception {
    // Create a regular transaction
    String recipient = accounts.getSecondaryBenefactor().getAddress();
    String data = "0x"; // Empty data
    
    // Send a regular transaction
    String regularTxHash = txManager.sendTransaction(
        GAS_PRICE, 
        GAS_LIMIT, 
        recipient, 
        data, 
        VALUE
    ).getTransactionHash();
    
    // Send a blob transaction
    EthSendRawTransaction blobResponse = sendRawBlobTransaction(recipient, data);
    
    // Send another regular transaction
    String regularTxHash2 = txManager.sendTransaction(
        GAS_PRICE, 
        GAS_LIMIT, 
        recipient, 
        data, 
        VALUE
    ).getTransactionHash();
    
    // Verify the regular transactions were accepted and mined
    TransactionReceipt receipt1 = txManager.getTransactionReceipt(regularTxHash).orElse(null);
    assertThat(receipt1).isNotNull();
    assertThat(receipt1.isStatusOK()).isTrue();
    
    TransactionReceipt receipt2 = txManager.getTransactionReceipt(regularTxHash2).orElse(null);
    assertThat(receipt2).isNotNull();
    assertThat(receipt2.isStatusOK()).isTrue();
    
    // Verify the blob transaction was rejected
    assertThat(blobResponse.hasError()).isTrue();
    assertThat(blobResponse.getError().getMessage()).contains("transaction type not supported");
  }

  /**
   * Helper method to send a raw blob transaction.
   * 
   * @param to The recipient address
   * @param data The transaction data
   * @return The response from the node
   * @throws IOException If there's an error sending the transaction
   */
  private EthSendRawTransaction sendRawBlobTransaction(String to, String data) throws IOException {
    // Create a blob transaction (type 0x03)
    // Note: This is a simplified version that creates the transaction envelope
    // with the correct type but doesn't include actual blob data
    
    // Get the next nonce
    BigInteger nonce = web3j.ethGetTransactionCount(
        credentials.getAddress(), 
        org.web3j.protocol.core.DefaultBlockParameterName.PENDING
    ).send().getTransactionCount();
    
    // Create a raw transaction with type 0x03 (BLOB)
    byte[] signedTx = createSignedBlobTransaction(
        nonce,
        to,
        VALUE,
        GAS_LIMIT,
        GAS_PRICE,
        Numeric.hexStringToByteArray(data)
    );
    
    // Send the raw transaction
    return web3j.ethSendRawTransaction(Numeric.toHexString(signedTx)).send();
  }
  
  /**
   * Creates a signed blob transaction.
   * 
   * @param nonce The transaction nonce
   * @param to The recipient address
   * @param value The transaction value
   * @param gasLimit The gas limit
   * @param gasPrice The gas price
   * @param data The transaction data
   * @return The signed transaction bytes
   */
  private byte[] createSignedBlobTransaction(
      BigInteger nonce,
      String to,
      BigInteger value,
      BigInteger gasLimit,
      BigInteger gasPrice,
      byte[] data) {
      
    // Create a transaction with type 0x03 (BLOB)
    RawTransaction rawTransaction = RawTransaction.createTransaction(
        CHAIN_ID,
        nonce,
        gasLimit,
        to,
        value,
        Numeric.toHexString(data),
        gasPrice,
        gasPrice
    );
    
    // Sign the transaction
    byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
    
    // Modify the transaction type to BLOB (0x03)
    byte[] modifiedSignedMessage = new byte[signedMessage.length + 1];
    modifiedSignedMessage[0] = 0x03; // BLOB transaction type
    System.arraycopy(signedMessage, 1, modifiedSignedMessage, 1, signedMessage.length - 1);
    
    return modifiedSignedMessage;
  }
}
