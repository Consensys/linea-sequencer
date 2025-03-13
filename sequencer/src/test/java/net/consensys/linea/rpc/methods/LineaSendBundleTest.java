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
package net.consensys.linea.rpc.methods;

import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.consensys.linea.rpc.services.BundlePoolService;
import net.consensys.linea.rpc.services.LineaLimitedBundlePool;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPrivateKey;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.plugin.data.ValidationResult;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LineaSendBundleTest {
  private static final KeyPair KEY_PAIR =
      new KeyPair(
          SECPPrivateKey.create(BigInteger.valueOf(Long.MAX_VALUE), SignatureAlgorithm.ALGORITHM),
          SECPPublicKey.create(BigInteger.valueOf(Long.MIN_VALUE), SignatureAlgorithm.ALGORITHM));
  @TempDir Path dataDir;
  private LineaSendBundle lineaSendBundle;
  private BesuEvents mockEvents;
  private LineaLimitedBundlePool bundlePool;
  private TransactionPoolService transactionPoolService;

  private Transaction mockTX1 =
      new TransactionTestFixture().nonce(0).gasLimit(21000).createTransaction(KEY_PAIR);

  private Transaction mockTX2 =
      new TransactionTestFixture().nonce(1).gasLimit(21000).createTransaction(KEY_PAIR);

  @BeforeEach
  void setup() {
    mockEvents = mock(BesuEvents.class);
    transactionPoolService = mock(TransactionPoolService.class);
    bundlePool = spy(new LineaLimitedBundlePool(dataDir, 4096L, mockEvents));
    lineaSendBundle = new LineaSendBundle().init(transactionPoolService, bundlePool);
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

    when(transactionPoolService.validateTransaction(any(), anyBoolean(), anyBoolean()))
        .thenReturn(okValidationResult());

    // Execute
    LineaSendBundle.BundleResponse response = lineaSendBundle.execute(request);

    // Validate response
    assertNotNull(response);
    assertEquals(expectedTxBundleHash.toHexString(), response.bundleHash());
  }

  @Test
  void testExecute_ValidBundle_withReplacement() {
    List<String> transactions = List.of(mockTX1.encoded().toHexString());
    UUID replId = UUID.randomUUID();
    var expectedUUIDBundleHash = LineaLimitedBundlePool.UUIDToHash(replId);

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

    when(transactionPoolService.validateTransaction(any(), anyBoolean(), anyBoolean()))
        .thenReturn(okValidationResult());

    // Execute
    LineaSendBundle.BundleResponse response = lineaSendBundle.execute(request);

    // Validate response
    assertNotNull(response);
    assertEquals(expectedUUIDBundleHash.toHexString(), response.bundleHash());

    // Replace bundle:
    transactions = List.of(mockTX2.encoded().toHexString(), mockTX1.encoded().toHexString());
    bundleParams =
        new LineaSendBundle.BundleParameter(
            transactions,
            12345L,
            minTimestamp,
            maxTimestamp,
            empty(),
            Optional.of(replId.toString()),
            empty());
    when(request.getParams()).thenReturn(new Object[] {bundleParams});

    // re-execute
    response = lineaSendBundle.execute(request);

    // Validate response
    assertNotNull(response);
    assertEquals(expectedUUIDBundleHash.toHexString(), response.bundleHash());

    // assert the new block number:
    assertTrue(bundlePool.get(expectedUUIDBundleHash).blockNumber().equals(12345L));
    List<BundlePoolService.TransactionBundle.PendingBundleTx> pts =
        bundlePool.get(expectedUUIDBundleHash).pendingTransactions();
    // assert the new tx2 is present
    assertTrue(pts.stream().map(pt -> pt.getTransaction()).anyMatch(t -> t.equals(mockTX2)));
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

    when(transactionPoolService.validateTransaction(any(), anyBoolean(), anyBoolean()))
        .thenReturn(okValidationResult());

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

    when(transactionPoolService.validateTransaction(any(), anyBoolean(), anyBoolean()))
        .thenReturn(okValidationResult());

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

    when(transactionPoolService.validateTransaction(any(), anyBoolean(), anyBoolean()))
        .thenReturn(okValidationResult());

    Exception exception =
        assertThrows(
            RuntimeException.class,
            () -> {
              lineaSendBundle.execute(request);
            });

    assertTrue(exception.getMessage().contains("Malformed bundle, no bundle transactions present"));
  }

  @Test
  void testExecute_InvalidTransaction_ThrowsException() {
    List<String> transactions =
        List.of(mockTX1.encoded().toHexString(), mockTX2.encoded().toHexString());
    LineaSendBundle.BundleParameter bundleParams =
        new LineaSendBundle.BundleParameter(
            transactions, 123L, empty(), empty(), empty(), empty(), empty());

    PluginRpcRequest request = mock(PluginRpcRequest.class);
    when(request.getParams()).thenReturn(new Object[] {bundleParams});

    // first tx is valid
    when(transactionPoolService.validateTransaction(eq(mockTX1), anyBoolean(), anyBoolean()))
        .thenReturn(okValidationResult());
    // second tx is invalid
    when(transactionPoolService.validateTransaction(eq(mockTX2), anyBoolean(), anyBoolean()))
        .thenReturn(failedValidationResult("INVALID_NONCE"));

    assertThatThrownBy(() -> lineaSendBundle.execute(request))
        .isInstanceOf(RuntimeException.class)
        .hasMessage(
            "Invalid transaction: idx=1,hash=0x122b7205ef66fd0943186c6ea548158804123aeeb12930b09755976f5286ff17,reason=INVALID_NONCE");
  }

  private static ValidationResult okValidationResult() {
    return new ValidationResult() {

      @Override
      public boolean isValid() {
        return true;
      }

      @Override
      public String getErrorMessage() {
        return "";
      }
    };
  }

  private static ValidationResult failedValidationResult(final String errorMessage) {
    return new ValidationResult() {

      @Override
      public boolean isValid() {
        return false;
      }

      @Override
      public String getErrorMessage() {
        return errorMessage;
      }
    };
  }
}
