package net.consensys.linea.rpc.methods;

import static java.util.Optional.empty;
import static org.hyperledger.besu.ethereum.core.PrivateTransactionDataFixture.SIGNATURE_ALGORITHM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.consensys.linea.rpc.services.LineaLimitedBundlePool;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.plugin.services.RpcEndpointService;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LineaSendBundleTest {

  private LineaSendBundle lineaSendBundle;
  private RpcEndpointService rpcEndpointService;
  private LineaLimitedBundlePool bundlePool;
  private Transaction mockTX1 =
      new TransactionTestFixture()
          .nonce(1)
          .gasLimit(21000)
          .createTransaction(SIGNATURE_ALGORITHM.get().generateKeyPair());

  @BeforeEach
  void setup() {
    rpcEndpointService = mock(RpcEndpointService.class);
    bundlePool = spy(new LineaLimitedBundlePool(4096));
    lineaSendBundle = new LineaSendBundle(rpcEndpointService, bundlePool);
  }

  @Test
  void testExecute_ValidBundle() {
    List<String> transactions = List.of(mockTX1.encoded().toHexString());
    var expectedTxBundleHash = Hash.hash(mockTX1.encoded());

    Optional<Long> minTimestamp = Optional.of(1000L);
    Optional<Long> maxTimestamp = Optional.of(System.currentTimeMillis() + 5000L);

    LineaSendBundle.BundleParameter bundleParams =
        new LineaSendBundle.BundleParameter(
            transactions, 123L, minTimestamp, maxTimestamp, empty(), empty(), empty());

    PluginRpcRequest request = mock(PluginRpcRequest.class);
    when(request.getParams()).thenReturn(new Object[] {bundleParams});

    // Execute
    LineaSendBundle.BundleResponse response = lineaSendBundle.execute(request);

    // Validate response
    assertNotNull(response);
    assertEquals(expectedTxBundleHash, response.bundleHash());
  }

  @Test
  void testExecute_ValidBundle_withReplacementId() {
    List<String> transactions = List.of(mockTX1.encoded().toHexString());
    UUID replId = UUID.randomUUID();
    var expectedUUIDBundleHash =
        Hash.hash(
            Bytes.concatenate(
                Bytes.ofUnsignedLong(replId.getMostSignificantBits()),
                Bytes.ofUnsignedLong(replId.getLeastSignificantBits())));

    Optional<Long> minTimestamp = Optional.of(1000L);
    Optional<Long> maxTimestamp = Optional.of(System.currentTimeMillis() + 5000L);

    LineaSendBundle.BundleParameter bundleParams =
        new LineaSendBundle.BundleParameter(
            transactions,
            123L,
            minTimestamp,
            maxTimestamp,
            empty(),
            Optional.of(replId.toString()),
            empty());

    PluginRpcRequest request = mock(PluginRpcRequest.class);
    when(request.getParams()).thenReturn(new Object[] {bundleParams});

    // Execute
    LineaSendBundle.BundleResponse response = lineaSendBundle.execute(request);

    // Validate response
    assertNotNull(response);
    assertEquals(expectedUUIDBundleHash, response.bundleHash());
  }

  @Test
  void testExecute_ExpiredBundle() {
    List<String> transactions = List.of(mockTX1.encoded().toHexString());
    Optional<Long> maxTimestamp = Optional.of(5000L);
    LineaSendBundle.BundleParameter bundleParams =
        new LineaSendBundle.BundleParameter(
            transactions, 123L, empty(), maxTimestamp, empty(), empty(), empty());

    PluginRpcRequest request = mock(PluginRpcRequest.class);
    when(request.getParams()).thenReturn(new Object[] {bundleParams});

    Exception exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              lineaSendBundle.execute(request);
            });

    assertTrue(exception.getMessage().contains("bundle max timestamp is in the past"));
  }

  @Test
  void testExecute_InvalidRequest_ThrowsException() {
    PluginRpcRequest request = mock(PluginRpcRequest.class);
    when(request.getParams()).thenReturn(new Object[] {"invalid_param"});

    Exception exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              lineaSendBundle.execute(request);
            });

    assertTrue(exception.getMessage().contains("malformed linea_sendBundle json param"));
  }

  @Test
  void testExecute_EmptyTransactions_ThrowsException() {
    LineaSendBundle.BundleParameter bundleParams =
        new LineaSendBundle.BundleParameter(
            List.of(), 123L, empty(), empty(), empty(), empty(), empty());

    PluginRpcRequest request = mock(PluginRpcRequest.class);
    when(request.getParams()).thenReturn(new Object[] {bundleParams});

    Exception exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              lineaSendBundle.execute(request);
            });

    assertTrue(exception.getMessage().contains("Malformed bundle, no bundle transactions present"));
  }
}
