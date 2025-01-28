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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.datatypes.parameters.UnsignedLongParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A pool for managing TransactionBundles with limited size and FIFO eviction. Provides access via
 * hash identifiers or block numbers.
 */
public class LineaLimitedBundlePool {

  private static final Logger logger = LoggerFactory.getLogger(LineaLimitedBundlePool.class);

  private final Cache<Hash, TransactionBundle> cache;
  private final Map<Long, List<TransactionBundle>> blockIndex;

  /**
   * Initializes the LineaLimitedBundlePool with a maximum size and expiration time.
   *
   * @param maxSize The maximum number of TransactionBundles in the pool.
   * @param maxSizeInBytes The maximum size in bytes of the pool objects.
   */
  public LineaLimitedBundlePool(long maxSizeInBytes) {
    this.cache =
        Caffeine.newBuilder()
            .maximumWeight(maxSizeInBytes) // Maximum size in bytes
            .weigher(
                (Hash key, TransactionBundle value) -> {
                  // Calculate the size of a TransactionBundle in bytes
                  return calculateWeight(value);
                })
            .removalListener(
                (Hash key, TransactionBundle bundle, RemovalCause cause) -> {
                  if (bundle != null && cause.wasEvicted()) {
                    logger.info(
                        "Dropping transaction bundle {}:{} due to {}",
                        bundle.blockNumber,
                        bundle.bundleIdentifier.toHexString(),
                        cause.name());
                    removeFromBlockIndex(bundle);
                  }
                })
            .build();

    this.blockIndex = new ConcurrentHashMap<>();
  }

  /**
   * Retrieves a list of TransactionBundles associated with a block number.
   *
   * @param blockNumber The block number to look up.
   * @return A list of TransactionBundles for the given block number, or an empty list if none are
   *     found.
   */
  public List<TransactionBundle> getBundlesByBlockNumber(long blockNumber) {
    return blockIndex.getOrDefault(blockNumber, Collections.emptyList());
  }

  /**
   * Finds a TransactionBundle that contains the specified pending transaction.
   *
   * @param blockNumber The block number to search for bundles.
   * @param pendingTransaction The pending transaction to search for.
   * @return An Optional containing the found TransactionBundle, or empty if none found.
   */
  public Optional<TransactionBundle> getBundleByPendingTransaction(
      long blockNumber, PendingTransaction pendingTransaction) {
    return getBundlesByBlockNumber(blockNumber).stream()
        .filter(bundle -> bundle.pendingTransactions().contains(pendingTransaction))
        .findAny();
  }

  /**
   * Retrieves a TransactionBundle by its unique hash identifier.
   *
   * @param hash The hash identifier of the TransactionBundle.
   * @return The TransactionBundle associated with the hash, or null if not found.
   */
  public TransactionBundle get(Hash hash) {
    return cache.getIfPresent(hash);
  }

  /**
   * Adds a new TransactionBundle to the cache and updates the block index.
   *
   * @param hash The hash identifier of the TransactionBundle.
   * @param bundle The TransactionBundle to add.
   */
  public void put(Hash hash, TransactionBundle bundle) {
    cache.put(hash, bundle);
    addToBlockIndex(bundle);
  }

  /**
   * Replaces an existing TransactionBundle in the cache and updates the block index.
   *
   * @param hash The hash identifier of the TransactionBundle.
   * @param bundle The new TransactionBundle to replace the existing one.
   */
  public void replace(Hash hash, TransactionBundle bundle) {
    TransactionBundle existing = cache.getIfPresent(hash);
    if (existing != null) {
      removeFromBlockIndex(existing);
    }
    cache.put(hash, bundle);
    addToBlockIndex(bundle);
  }

  /**
   * Removes all TransactionBundles associated with the given block number. First removes them from
   * the block index, then removes them from the cache.
   *
   * @param blockNumber The block number whose bundles should be removed.
   */
  public void removeByBlockNumber(long blockNumber) {
    List<TransactionBundle> bundles = blockIndex.remove(blockNumber);
    if (bundles != null) {
      for (TransactionBundle bundle : bundles) {
        cache.invalidate(bundle.bundleIdentifier());
      }
    }
  }

  /**
   * Adds a TransactionBundle to the block index.
   *
   * @param bundle The TransactionBundle to add.
   */
  private void addToBlockIndex(TransactionBundle bundle) {
    long blockNumber = bundle.blockNumber().getValue();
    blockIndex.computeIfAbsent(blockNumber, k -> new ArrayList<>()).add(bundle);
  }

  /**
   * Removes a TransactionBundle from the block index.
   *
   * @param bundle The TransactionBundle to remove.
   */
  private void removeFromBlockIndex(TransactionBundle bundle) {
    long blockNumber = bundle.blockNumber().getValue();
    List<TransactionBundle> bundles = blockIndex.get(blockNumber);
    if (bundles != null) {
      bundles.remove(bundle);
      if (bundles.isEmpty()) {
        blockIndex.remove(blockNumber);
      }
    }
  }

  private int calculateWeight(TransactionBundle bundle) {
    return bundle.pendingTransactions.stream()
        .mapToInt(pt -> pt.getTransaction().getPayload().size())
        .sum();
  }

  /** TransactionBundle record representing a collection of pending transactions with metadata. */
  public record TransactionBundle(
      Hash bundleIdentifier,
      List<PendingTransaction> pendingTransactions,
      UnsignedLongParameter blockNumber,
      Optional<Long> minTimestamp,
      Optional<Long> maxTimestamp,
      Optional<List<Hash>> revertingTxHashes) {}
}
