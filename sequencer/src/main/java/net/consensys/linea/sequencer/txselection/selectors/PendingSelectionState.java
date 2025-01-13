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
package net.consensys.linea.sequencer.txselection.selectors;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.LinkedHashMap;
import java.util.SequencedMap;

import org.hyperledger.besu.datatypes.Hash;

public class PendingSelectionState<T> {
  private final SequencedMap<Hash, T> pendingState = new LinkedHashMap<>();
  private T confirmedState;

  /**
   * Create a new pending state initialized with the given state as confirmed
   *
   * @param initialState the initial state
   */
  public PendingSelectionState(final T initialState) {
    this.confirmedState = initialState;
  }

  /**
   * Append the unconfirmed state related to the passed tx hash to the pending list. The appended
   * value remains pending, meaning it could be discarded, until {@link
   * PendingSelectionState#confirm(Hash)} is called for the same tx hash or a following one.
   *
   * @param txHash Hash of the transaction
   * @param unconfirmedState The state to set as the last unconfirmed
   */
  public void appendUnconfirmed(final Hash txHash, final T unconfirmedState) {
    pendingState.putLast(txHash, unconfirmedState);
  }

  /**
   * Sets the state referred by the specified tx hash has the confirmed one, allowing to forget all
   * the preceding entries.
   *
   * @param txHash the tx hash, it must exist in the pending list, otherwise an exception is thrown
   */
  public void confirm(final Hash txHash) {
    checkArgument(
        pendingState.containsKey(txHash), "The specified tx hash has no associated pending state.");

    final var it = pendingState.entrySet().iterator();
    while (it.hasNext()) {
      final var entry = it.next();
      it.remove();
      if (entry.getKey().equals(txHash)) {
        confirmedState = entry.getValue();
        break;
      }
    }
  }

  /**
   * Discards the unconfirmed states starting from the specified tx hash.
   *
   * @param txHash the tx hash, could not be present, in which case there is no change to the
   *     pending state
   */
  public void discard(final Hash txHash) {
    boolean afterRemoved = false;
    final var it = pendingState.entrySet().iterator();
    while (it.hasNext()) {
      final var entry = it.next();
      if (afterRemoved || entry.getKey().equals(txHash)) {
        it.remove();
        afterRemoved = true;
      }
    }
  }

  /**
   * Gets the latest, including unconfirmed, state. Note that the returned values could not yet be
   * confirmed and could be discarded in the future.
   *
   * @return a map with the line count per module
   */
  public T getLast() {
    if (pendingState.isEmpty()) {
      return confirmedState;
    }
    return pendingState.lastEntry().getValue();
  }

  /**
   * Gets the confirmed state, that could be equal to the initial value, if there were no commits.
   *
   * @return the confirmed state
   */
  public T getConfirmed() {
    return confirmedState;
  }
}
