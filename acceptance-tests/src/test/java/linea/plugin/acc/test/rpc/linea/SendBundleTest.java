package linea.plugin.acc.test.rpc.linea;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import linea.plugin.acc.test.LineaPluginTestBase;
import linea.plugin.acc.test.TestCommandLineOptionsBuilder;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.NodeRequests;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.account.TransferTransaction;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.Request;

public class SendBundleTest extends LineaPluginTestBase {

  @Override
  public List<String> getTestCliOptions() {
    return new TestCommandLineOptionsBuilder().build();
  }

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
}
