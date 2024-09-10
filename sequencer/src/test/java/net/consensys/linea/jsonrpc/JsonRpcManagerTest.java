package net.consensys.linea.jsonrpc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import net.consensys.linea.sequencer.txselection.selectors.TestTransactionEvaluationContext;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.plugin.data.ProcessableBlockHeader;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@WireMockTest
@ExtendWith(MockitoExtension.class)
class JsonRpcManagerTest {
  @TempDir private Path tempDataDir;
  private JsonRpcManager jsonRpcManager;

  @Mock private PendingTransaction pendingTransaction;
  @Mock private ProcessableBlockHeader pendingBlockHeader;
  @Mock private Transaction transaction;

  @BeforeEach
  void init(final WireMockRuntimeInfo wmInfo) {
    jsonRpcManager = new JsonRpcManager(tempDataDir, URI.create(wmInfo.getHttpBaseUrl()));
    jsonRpcManager.start();
  }

  @AfterEach
  void cleanup() {
    jsonRpcManager.shutdown();
  }

  @Test
  void rejectedTxIsReported() throws InterruptedException {
    // mock stubbing
    when(pendingBlockHeader.getNumber()).thenReturn(1L);
    when(pendingTransaction.getTransaction()).thenReturn(transaction);
    final Bytes randomEncodedBytes = Bytes.random(32);
    when(transaction.encoded()).thenReturn(randomEncodedBytes);

    // json-rpc stubbing
    stubFor(
        post(urlEqualTo("/"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"jsonrpc\":\"2.0\",\"result\":{ \"status\": \"SAVED\"},\"id\":1}")));

    final TestTransactionEvaluationContext context =
        new TestTransactionEvaluationContext(pendingTransaction)
            .setPendingBlockHeader(pendingBlockHeader);
    final TransactionSelectionResult result = TransactionSelectionResult.invalid("test");
    final Instant timestamp = Instant.now();

    // method under test
    final String jsonRpcCall =
        JsonRpcRequestBuilder.buildRejectedTxRequest(context, result, timestamp);
    jsonRpcManager.submitNewJsonRpcCall(jsonRpcCall);

    // sleep a bit to allow async processing
    Thread.sleep(1000);

    // assert that the expected json-rpc request was sent to WireMock
    verify(postRequestedFor(urlEqualTo("/")).withRequestBody(equalToJson(jsonRpcCall)));
  }
}
