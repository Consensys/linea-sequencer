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
package net.consensys.linea.sequencer.liveness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import net.consensys.linea.jsonrpc.JsonRpcManager;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;

@ExtendWith(MockitoExtension.class)
public class LivenessPluginE2ETest {

  @Mock private ServiceManager serviceManager;

  @Mock private BlockchainService blockchainService;

  @Mock private BesuEvents besuEvents;

  @Mock private PicoCLIOptions picoCLIOptions;

  @Mock private JsonRpcManager jsonRpcManager;

  @Mock private BlockHeader blockHeader;

  @Mock private ScheduledExecutorService scheduledExecutorService;

  private LivenessPlugin livenessPlugin;

  private long currentEpochTime;

  @BeforeEach
  public void setup() {
    currentEpochTime = Instant.now().getEpochSecond();

    livenessPlugin = new LivenessPlugin();

    when(serviceManager.getService(BesuEvents.class)).thenReturn(Optional.of(besuEvents));
    when(serviceManager.getService(PicoCLIOptions.class)).thenReturn(Optional.of(picoCLIOptions));
    when(serviceManager.getService(BlockchainService.class))
        .thenReturn(Optional.of(blockchainService));

    try {
      Field jsonRpcManagerField = LivenessPlugin.class.getDeclaredField("jsonRpcManager");
      jsonRpcManagerField.setAccessible(true);
      jsonRpcManagerField.set(livenessPlugin, jsonRpcManager);
    } catch (Exception e) {
      throw new RuntimeException("Failed to inject jsonRpcManager", e);
    }
  }

  @Test
  public void shouldDetectAndReportSequencerInactivity() throws Exception {
    Field enabledField = LivenessPlugin.class.getDeclaredField("isPluginEnabled");
    enabledField.setAccessible(true);
    enabledField.set(livenessPlugin, true);

    Field maxBlockAgeSecondsField = LivenessPlugin.class.getDeclaredField("maxBlockAgeSeconds");
    maxBlockAgeSecondsField.setAccessible(true);
    maxBlockAgeSecondsField.set(livenessPlugin, 60L);

    Field contractAddressField =
        LivenessPlugin.class.getDeclaredField("livenessStateContractAddress");
    contractAddressField.setAccessible(true);
    contractAddressField.set(
        livenessPlugin, Address.fromHexString("0x1230000000000000000000000000000000000000"));

    Field gasLimitField = LivenessPlugin.class.getDeclaredField("gasLimit");
    gasLimitField.setAccessible(true);
    gasLimitField.set(livenessPlugin, 100000L);

    Field chainIdField = LivenessPlugin.class.getDeclaredField("chainId");
    chainIdField.setAccessible(true);
    chainIdField.set(livenessPlugin, BigInteger.valueOf(59144L));

    Credentials credentials =
        Credentials.create("0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63");
    Field credentialsField = LivenessPlugin.class.getDeclaredField("credentials");
    credentialsField.setAccessible(true);
    credentialsField.set(livenessPlugin, credentials);

    Field web3jField = LivenessPlugin.class.getDeclaredField("web3j");
    web3jField.setAccessible(true);
    web3jField.set(livenessPlugin, null);

    long oldTimestamp = currentEpochTime - 120;

    livenessPlugin.doRegister(serviceManager);

    String jsonCall1 = String.format("{\"status\":false,\"timestamp\":%d}", oldTimestamp);
    jsonRpcManager.submitNewJsonRpcCallAsync(jsonCall1);

    String jsonCall2 = String.format("{\"status\":true,\"timestamp\":%d}", currentEpochTime);
    jsonRpcManager.submitNewJsonRpcCallAsync(jsonCall2);

    ArgumentCaptor<String> jsonRpcCaptor = ArgumentCaptor.forClass(String.class);

    verify(jsonRpcManager, times(2)).submitNewJsonRpcCallAsync(jsonRpcCaptor.capture());

    List<String> jsonRpcCalls = jsonRpcCaptor.getAllValues();

    assertEquals(jsonCall1, jsonRpcCalls.get(0));
    assertEquals(jsonCall2, jsonRpcCalls.get(1));
  }

  @Test
  public void shouldNotReportWhenBlockIsRecent() throws Exception {
    boolean enabled = true;
    long maxBlockAgeSeconds = 60L;
    String contractAddress = "0x1230000000000000000000000000000000000000";
    long gasLimit = 100000L;

    Field enabledField = LivenessPlugin.class.getDeclaredField("isPluginEnabled");
    enabledField.setAccessible(true);
    enabledField.set(livenessPlugin, enabled);

    Field maxBlockAgeSecondsField = LivenessPlugin.class.getDeclaredField("maxBlockAgeSeconds");
    maxBlockAgeSecondsField.setAccessible(true);
    maxBlockAgeSecondsField.set(livenessPlugin, maxBlockAgeSeconds);

    Field contractAddressField =
        LivenessPlugin.class.getDeclaredField("livenessStateContractAddress");
    contractAddressField.setAccessible(true);
    contractAddressField.set(livenessPlugin, Address.fromHexString(contractAddress));

    Field gasLimitField = LivenessPlugin.class.getDeclaredField("gasLimit");
    gasLimitField.setAccessible(true);
    gasLimitField.set(livenessPlugin, gasLimit);

    Field chainIdField = LivenessPlugin.class.getDeclaredField("chainId");
    chainIdField.setAccessible(true);
    chainIdField.set(livenessPlugin, BigInteger.valueOf(59144L));

    Credentials credentials =
        Credentials.create("0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63");
    Field credentialsField = LivenessPlugin.class.getDeclaredField("credentials");
    credentialsField.setAccessible(true);
    credentialsField.set(livenessPlugin, credentials);

    Field web3jField = LivenessPlugin.class.getDeclaredField("web3j");
    web3jField.setAccessible(true);
    web3jField.set(livenessPlugin, null);

    livenessPlugin.doRegister(serviceManager);

    Field schedulerField = LivenessPlugin.class.getDeclaredField("scheduler");
    schedulerField.setAccessible(true);
    schedulerField.set(livenessPlugin, scheduledExecutorService);

    long recentTimestamp = currentEpochTime - 30;
    when(blockHeader.getTimestamp()).thenReturn(recentTimestamp);

    Field lastProcessedBlockField = LivenessPlugin.class.getDeclaredField("lastProcessedBlock");
    lastProcessedBlockField.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.concurrent.atomic.AtomicReference<BlockHeader> lastProcessedBlock =
        (java.util.concurrent.atomic.AtomicReference<BlockHeader>)
            lastProcessedBlockField.get(livenessPlugin);
    lastProcessedBlock.set(blockHeader);

    Field lastReportedTimestampField =
        LivenessPlugin.class.getDeclaredField("lastReportedTimestamp");
    lastReportedTimestampField.setAccessible(true);
    java.util.concurrent.atomic.AtomicLong lastReportedTimestamp =
        (java.util.concurrent.atomic.AtomicLong) lastReportedTimestampField.get(livenessPlugin);
    lastReportedTimestamp.set(currentEpochTime - 120);

    java.lang.reflect.Method checkBlockTimestampAndReportMethod =
        LivenessPlugin.class.getDeclaredMethod("checkBlockTimestampAndReport");
    checkBlockTimestampAndReportMethod.setAccessible(true);
    checkBlockTimestampAndReportMethod.invoke(livenessPlugin);

    verify(jsonRpcManager, never()).submitNewJsonRpcCallAsync(anyString());
  }

  @Test
  public void shouldCallUpdateStatusWithCorrectParameters() throws Exception {
    Field enabledField = LivenessPlugin.class.getDeclaredField("isPluginEnabled");
    enabledField.setAccessible(true);
    enabledField.set(livenessPlugin, true);

    Field maxBlockAgeSecondsField = LivenessPlugin.class.getDeclaredField("maxBlockAgeSeconds");
    maxBlockAgeSecondsField.setAccessible(true);
    maxBlockAgeSecondsField.set(livenessPlugin, 60L);

    Field contractAddressField =
        LivenessPlugin.class.getDeclaredField("livenessStateContractAddress");
    contractAddressField.setAccessible(true);
    contractAddressField.set(
        livenessPlugin, Address.fromHexString("0x1230000000000000000000000000000000000000"));

    Field gasLimitField = LivenessPlugin.class.getDeclaredField("gasLimit");
    gasLimitField.setAccessible(true);
    gasLimitField.set(livenessPlugin, 100000L);

    Field chainIdField = LivenessPlugin.class.getDeclaredField("chainId");
    chainIdField.setAccessible(true);
    chainIdField.set(livenessPlugin, BigInteger.valueOf(59144L));

    Credentials credentials =
        Credentials.create("0x0000000000000000000000000000000000000000000000000000000000000000");
    Field credentialsField = LivenessPlugin.class.getDeclaredField("credentials");
    credentialsField.setAccessible(true);
    credentialsField.set(livenessPlugin, credentials);

    Field web3jField = LivenessPlugin.class.getDeclaredField("web3j");
    web3jField.setAccessible(true);
    web3jField.set(livenessPlugin, null);

    long oldTimestamp = currentEpochTime - 120;

    livenessPlugin.doRegister(serviceManager);

    String jsonCall1 = String.format("{\"status\":false,\"timestamp\":%d}", oldTimestamp);
    jsonRpcManager.submitNewJsonRpcCallAsync(jsonCall1);

    String jsonCall2 = String.format("{\"status\":true,\"timestamp\":%d}", currentEpochTime);
    jsonRpcManager.submitNewJsonRpcCallAsync(jsonCall2);

    ArgumentCaptor<String> jsonRpcCaptor = ArgumentCaptor.forClass(String.class);

    verify(jsonRpcManager, times(2)).submitNewJsonRpcCallAsync(jsonRpcCaptor.capture());

    List<String> jsonRpcCalls = jsonRpcCaptor.getAllValues();

    assertEquals(jsonCall1, jsonRpcCalls.get(0));
    assertEquals(jsonCall2, jsonRpcCalls.get(1));
  }
}
