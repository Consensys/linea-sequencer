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
package net.consensys.linea.sequencer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.PermissioningService;
import org.hyperledger.besu.plugin.services.permissioning.TransactionPermissioningProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class LineaPermissioningPluginTest {

  @Mock private ServiceManager serviceManager;
  @Mock private PermissioningService permissioningService;
  @Mock private Transaction transaction;

  private LineaPermissioningPlugin plugin;

  @BeforeEach
  public void setUp() {
    plugin = new LineaPermissioningPlugin();
    when(serviceManager.getService(PermissioningService.class))
        .thenReturn(Optional.of(permissioningService));
  }

  @Test
  public void shouldRegisterWithServiceManager() {
    // When
    plugin.doRegister(serviceManager);

    // Then
    verify(serviceManager).getService(PermissioningService.class);
  }

  @Test
  public void shouldThrowExceptionWhenPermissioningServiceNotAvailable() {
    // Given
    when(serviceManager.getService(PermissioningService.class)).thenReturn(Optional.empty());

    // When/Then
    assertThatThrownBy(() -> plugin.doRegister(serviceManager))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to obtain PermissioningService from the ServiceManager");
  }

  @Test
  public void shouldRegisterTransactionPermissioningProvider() {
    // Given
    plugin.doRegister(serviceManager);

    // When
    plugin.doStart();

    // Then
    verify(permissioningService)
        .registerTransactionPermissioningProvider(any(TransactionPermissioningProvider.class));
  }

  @Test
  public void shouldRejectBlobTransactions() {
    // Given
    plugin.doRegister(serviceManager);
    plugin.doStart();

    // Capture the transaction permissioning provider
    ArgumentCaptor<TransactionPermissioningProvider> providerCaptor =
        ArgumentCaptor.forClass(TransactionPermissioningProvider.class);
    verify(permissioningService).registerTransactionPermissioningProvider(providerCaptor.capture());
    TransactionPermissioningProvider provider = providerCaptor.getValue();

    // When - BLOB transaction
    when(transaction.getType()).thenReturn(TransactionType.BLOB);
    boolean blobResult = provider.isPermitted(transaction);

    // Then
    assertThat(blobResult).isFalse();
  }

  @Test
  public void shouldAcceptNonBlobTransactions() {
    // Given
    plugin.doRegister(serviceManager);
    plugin.doStart();

    // Capture the transaction permissioning provider
    ArgumentCaptor<TransactionPermissioningProvider> providerCaptor =
        ArgumentCaptor.forClass(TransactionPermissioningProvider.class);
    verify(permissioningService).registerTransactionPermissioningProvider(providerCaptor.capture());
    TransactionPermissioningProvider provider = providerCaptor.getValue();

    // When - FRONTIER transaction
    when(transaction.getType()).thenReturn(TransactionType.FRONTIER);
    boolean frontierResult = provider.isPermitted(transaction);

    // When - ACCESS_LIST transaction
    when(transaction.getType()).thenReturn(TransactionType.ACCESS_LIST);
    boolean accessListResult = provider.isPermitted(transaction);

    // When - EIP1559 transaction
    when(transaction.getType()).thenReturn(TransactionType.EIP1559);
    boolean eip1559Result = provider.isPermitted(transaction);

    // Then
    assertThat(frontierResult).isTrue();
    assertThat(accessListResult).isTrue();
    assertThat(eip1559Result).isTrue();
  }
}
