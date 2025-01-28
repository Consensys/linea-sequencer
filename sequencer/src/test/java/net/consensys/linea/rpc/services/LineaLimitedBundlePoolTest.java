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

package net.consensys.linea.rpc.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.datatypes.parameters.UnsignedLongParameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LineaLimitedBundlePoolTest {

  private LineaLimitedBundlePool pool;

  @BeforeEach
  void setUp() {
    pool = new LineaLimitedBundlePool(10_000); // Max 100 entries, 10 KB size
  }

  @Test
  void smokeTestPutAndGetByHash() {
    Hash hash = Hash.fromHexStringLenient("0x1234");
    LineaLimitedBundlePool.TransactionBundle bundle = createBundle(hash, 1);

    pool.put(hash, bundle);
    LineaLimitedBundlePool.TransactionBundle retrieved = pool.get(hash);

    assertNotNull(retrieved, "Bundle should be retrieved by hash");
    assertEquals(hash, retrieved.bundleIdentifier(), "Retrieved bundle hash should match");
  }

  @Test
  void smokeTestGetBundlesByBlockNumber() {
    Hash hash1 = Hash.fromHexStringLenient("0x1234");
    Hash hash2 = Hash.fromHexStringLenient("0x5678");
    LineaLimitedBundlePool.TransactionBundle bundle1 = createBundle(hash1, 1);
    LineaLimitedBundlePool.TransactionBundle bundle2 = createBundle(hash2, 1);

    pool.put(hash1, bundle1);
    pool.put(hash2, bundle2);

    List<LineaLimitedBundlePool.TransactionBundle> bundles = pool.getBundlesByBlockNumber(1);

    assertEquals(2, bundles.size(), "There should be two bundles for block 1");
    assertTrue(bundles.contains(bundle1), "Bundles should contain bundle1");
    assertTrue(bundles.contains(bundle2), "Bundles should contain bundle2");
  }

  @Test
  void smokeTestGetBundleByPendingTransaction() {
    Hash hash = Hash.fromHexStringLenient("0x1234");
    PendingTransaction pendingTransaction = new MockPendingTransaction();
    LineaLimitedBundlePool.TransactionBundle bundle =
        new LineaLimitedBundlePool.TransactionBundle(
            hash,
            List.of(pendingTransaction),
            new UnsignedLongParameter(1),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    pool.put(hash, bundle);

    Optional<LineaLimitedBundlePool.TransactionBundle> retrieved =
        pool.getBundleByPendingTransaction(1, pendingTransaction);

    assertTrue(retrieved.isPresent(), "Bundle containing the pending transaction should be found");
    assertEquals(bundle, retrieved.get(), "Retrieved bundle should match the expected bundle");
  }

  @Test
  void smokeTestRemoveByBlockNumber() {
    Hash hash1 = Hash.fromHexStringLenient("0x1234");
    Hash hash2 = Hash.fromHexStringLenient("0x5678");
    LineaLimitedBundlePool.TransactionBundle bundle1 = createBundle(hash1, 1);
    LineaLimitedBundlePool.TransactionBundle bundle2 = createBundle(hash2, 1);

    pool.put(hash1, bundle1);
    pool.put(hash2, bundle2);

    pool.removeByBlockNumber(1);

    assertNull(pool.get(hash1), "Bundle1 should be removed from the cache");
    assertNull(pool.get(hash2), "Bundle2 should be removed from the cache");
    assertTrue(
        pool.getBundlesByBlockNumber(1).isEmpty(), "Block index for block 1 should be empty");
  }

  private LineaLimitedBundlePool.TransactionBundle createBundle(Hash hash, long blockNumber) {
    return new LineaLimitedBundlePool.TransactionBundle(
        hash,
        Collections.emptyList(),
        new UnsignedLongParameter(blockNumber),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  class MockPendingTransaction implements PendingTransaction {

    @Override
    public Transaction getTransaction() {
      return new org.hyperledger.besu.ethereum.core.Transaction.Builder()
          .payload(Bytes32.random())
          .build();
    }

    @Override
    public boolean isReceivedFromLocalSource() {
      return false;
    }

    @Override
    public boolean hasPriority() {
      return false;
    }

    @Override
    public long getAddedAt() {
      return 0;
    }
  }
}
