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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.BesuService;

public interface BundlePoolService extends BesuService {

  /** TransactionBundle record representing a collection of pending transactions with metadata. */
  record TransactionBundle(
      Hash bundleIdentifier,
      List<PendingTransaction> pendingTransactions,
      Long blockNumber,
      Optional<Long> minTimestamp,
      Optional<Long> maxTimestamp,
      Optional<List<Hash>> revertingTxHashes) {}

  /**
   * synchronized usage of serviceManager to get a bundle pool from the services manager or create
   * and register one. Bundle Pool is required by both rpc and selector plugins.
   *
   * @param serviceManager
   * @param maxBundlePoolSizeBytes
   * @return
   */
  static Optional<BundlePoolService> getOrCreateBundlePool(
      ServiceManager serviceManager, long maxBundlePoolSizeBytes) {
    synchronized (serviceManager) {
      var eventService =
          serviceManager
              .getService(BesuEvents.class)
              .orElseThrow(
                  () ->
                      new RuntimeException("Failed to obtain BesuEvents from the ServiceManager."));

      return serviceManager
          .getService(BundlePoolService.class)
          .or(
              () -> {
                var bundlePool = new LineaLimitedBundlePool(maxBundlePoolSizeBytes, eventService);
                serviceManager.addService(BundlePoolService.class, bundlePool);
                return Optional.of(bundlePool);
              });
    }
  }

  /**
   * Retrieves a list of TransactionBundles associated with a block number.
   *
   * @param blockNumber The block number to look up.
   * @return A list of TransactionBundles for the given block number, or an empty list if none are
   *     found.
   */
  List<TransactionBundle> getBundlesByBlockNumber(long blockNumber);

  /**
   * Finds a TransactionBundle that contains the specified pending transaction.
   *
   * @param blockNumber The block number to search for bundles.
   * @param pendingTransaction The pending transaction to search for.
   * @return An Optional containing the found TransactionBundle, or empty if none found.
   */
  Optional<TransactionBundle> getBundleByPendingTransaction(
      long blockNumber, PendingTransaction pendingTransaction);

  /**
   * Retrieves a TransactionBundle by its unique hash identifier.
   *
   * @param hash The hash identifier of the TransactionBundle.
   * @return The TransactionBundle associated with the hash, or null if not found.
   */
  TransactionBundle get(Hash hash);

  /**
   * Retrieves a TransactionBundle by its replacement UUID
   *
   * @param replacementUUID identifier of the TransactionBundle.
   * @return The TransactionBundle associated with the uuid, or null if not found.
   */
  TransactionBundle get(UUID replacementUUID);

  /**
   * Puts or replaces an existing TransactionBundle in the cache and updates the block index.
   *
   * @param hash The hash identifier of the TransactionBundle.
   * @param bundle The new TransactionBundle to replace the existing one.
   */
  void putOrReplace(Hash hash, TransactionBundle bundle);

  /**
   * Puts or replaces an existing TransactionBundle by UUIDin the cache and updates the block index.
   *
   * @param replacementUUID identifier of the TransactionBundle.
   * @param bundle The new TransactionBundle to replace the existing one.
   */
  void putOrReplace(UUID replacementUUID, TransactionBundle bundle);

  /**
   * removes an existing TransactionBundle in the cache and updates the block index.
   *
   * @param replacementUUID identifier of the TransactionBundle.
   * @return boolean indicating if bundle was found and removed
   */
  boolean remove(UUID replacementUUID);

  /**
   * removes an existing TransactionBundle in the cache and updates the block index.
   *
   * @param hash The hash identifier of the TransactionBundle.
   * @return boolean indicating if bundle was found and removed
   */
  boolean remove(Hash hash);

  /**
   * Removes all TransactionBundles associated with the given block number. First removes them from
   * the block index, then removes them from the cache.
   *
   * @param blockNumber The block number whose bundles should be removed.
   */
  void removeByBlockNumber(long blockNumber);
}
