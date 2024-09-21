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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.tx.RawTransactionManager;
import org.web3j.utils.Convert;

public class TransactionPoolDenyListReloadTest extends LineaPluginTestBase {

  private static final BigInteger GAS_PRICE = Convert.toWei("20", Convert.Unit.GWEI).toBigInteger();
  private static final BigInteger GAS_LIMIT = BigInteger.valueOf(210000);
  private static final BigInteger VALUE = BigInteger.ONE; // 1 wei

  final Credentials notDenied = Credentials.create(Accounts.GENESIS_ACCOUNT_ONE_PRIVATE_KEY);
  final Credentials willbeDenied = Credentials.create(Accounts.GENESIS_ACCOUNT_TWO_PRIVATE_KEY);

  @TempDir static Path tempDir;
  static Path tempDenyList ;

  @Override
  public List<String> getTestCliOptions() {
    tempDenyList = tempDir.resolve("denyList.txt");
    return new TestCommandLineOptionsBuilder()
        .set("--plugin-linea-deny-list-path=", tempDenyList.toString())
        .build();
  }

  @Test
  public void emptyDenyList() throws Exception {
    final Web3j miner = minerNode.nodeRequests().eth();

    RawTransactionManager transactionManager = new RawTransactionManager(miner, willbeDenied, CHAIN_ID);
    assertAddressAllowed(transactionManager,willbeDenied.getAddress() );
  }

  @Test
  public void emptyDenyList_thenDenySender_cannotAddTxToPool() throws Exception {
    final Web3j miner = minerNode.nodeRequests().eth();
    RawTransactionManager transactionManager = new RawTransactionManager(miner, willbeDenied, CHAIN_ID);

    assertAddressAllowed(transactionManager, willbeDenied.getAddress());
    addAddressToDenyList(willbeDenied.getAddress());

    reloadPluginConfig();

    assertAddressNotAllowed(transactionManager, willbeDenied.getAddress());
  }

  public void senderOnDenyListThenRemoved_canAddTxToPool() throws Exception {
    final Web3j miner = minerNode.nodeRequests().eth();

    RawTransactionManager transactionManager = new RawTransactionManager(miner, willbeDenied, CHAIN_ID);
    EthSendTransaction transactionResponse =
        transactionManager.sendTransaction(GAS_PRICE, GAS_LIMIT, notDenied.getAddress(), "", VALUE);

    assertThat(transactionResponse.getTransactionHash()).isNull();
    assertThat(transactionResponse.getError().getMessage())
        .isEqualTo(
            "sender 0x627306090abab3a6e1400e9345bc60c78a8bef57 is blocked as appearing on the SDN or other legally prohibited list");

    // TODO then remove that account from denyList.txt
    // TODO assert that denyList.txt is empty
    // TODO assert that that sender can now send transactions.

    EthSendTransaction transactionResponse2 =
        transactionManager.sendTransaction(GAS_PRICE, GAS_LIMIT, notDenied.getAddress(), "", VALUE);

    assertThat(transactionResponse2.getTransactionHash()).isNotNull();
    assertThat(transactionResponse2.getError().getMessage()).isNull();
  }

  public void transactionWithRecipientOnDenyListCannotBeAddedToPool() throws Exception {
    final Web3j miner = minerNode.nodeRequests().eth();

    RawTransactionManager transactionManager =
        new RawTransactionManager(miner, notDenied, CHAIN_ID);
    EthSendTransaction transactionResponse =
        transactionManager.sendTransaction(GAS_PRICE, GAS_LIMIT, willbeDenied.getAddress(), "", VALUE);

    assertThat(transactionResponse.getTransactionHash()).isNull();
    assertThat(transactionResponse.getError().getMessage())
        .isEqualTo(
            "recipient 0x627306090abab3a6e1400e9345bc60c78a8bef57 is blocked as appearing on the SDN or other legally prohibited list");
  }

  public void transactionCallingContractOnDenyListCannotBeAddedToPool() throws Exception {
    final Web3j miner = minerNode.nodeRequests().eth();

    RawTransactionManager transactionManager =
        new RawTransactionManager(miner, notDenied, CHAIN_ID);
    EthSendTransaction transactionResponse =
        transactionManager.sendTransaction(
            GAS_PRICE,
            GAS_LIMIT,
            "0x000000000000000000000000000000000000000a",
            "0xdeadbeef",
            VALUE);

    assertThat(transactionResponse.getTransactionHash()).isNull();
    assertThat(transactionResponse.getError().getMessage())
        .isEqualTo("destination address is a precompile address and cannot receive transactions");
  }

  private void addAddressToDenyList(String address) throws IOException {
    Files.writeString(tempDenyList, address);
  }

  private void assertAddressAllowed(RawTransactionManager transactionManager, String address) throws IOException {
    EthSendTransaction transactionResponse =
            transactionManager.sendTransaction(GAS_PRICE, GAS_LIMIT, address, "", VALUE);
    assertThat(transactionResponse.getTransactionHash()).isNotNull();
    assertThat(transactionResponse.getError()).isNull();
  }

  private void assertAddressNotAllowed(RawTransactionManager transactionManager, String address) throws IOException {
    EthSendTransaction transactionResponse =
            transactionManager.sendTransaction(GAS_PRICE, GAS_LIMIT, address, "", VALUE);

    assertThat(transactionResponse.getTransactionHash()).isNull();
    assertThat(transactionResponse.getError().getMessage())
            .isEqualTo(
                    "recipient "+ address + " is blocked as appearing on the SDN or other legally prohibited list");
  }

  private void reloadPluginConfig() {
    final var reqLinea = new ReloadPluginConfigRequest();
//    System.out.println("88888888"+reqLinea);
    final var respLinea = reqLinea.execute(minerNode.nodeRequests());
//    System.out.println("88888888"+respLinea);
    assertThat(respLinea.booleanValue()).isTrue();
  }

  static class ReloadPluginConfigRequest implements Transaction<Boolean> {

    public ReloadPluginConfigRequest() {
    }

    @Override
    public Boolean execute(final NodeRequests nodeRequests) {
      try {
        return new Request<>(
                "plugins_reloadPluginConfig",
                List.of(),
                nodeRequests.getWeb3jService(),
                ReloadPluginConfigResponse.class)
                .send()
                .getResult();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  static class ReloadPluginConfigResponse extends org.web3j.protocol.core.Response<Boolean> {}
}
