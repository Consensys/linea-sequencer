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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@WireMockTest
@ExtendWith(MockitoExtension.class)
public class LivenessPluginIntegrationTest {

  @Mock private ServiceManager serviceManager;

  @Mock private BlockchainService blockchainService;

  @Mock private BesuEvents besuEvents;

  @Mock private PicoCLIOptions picoCLIOptions;

  @Mock private BlockHeader blockHeader;

  private LivenessPlugin livenessPlugin;
  private long currentEpochTime;

  @BeforeEach
  public void setup(final WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
    currentEpochTime = Instant.now().getEpochSecond();

    livenessPlugin = new LivenessPlugin();

    Web3j web3j = Web3j.build(new HttpService(wmRuntimeInfo.getHttpBaseUrl()));
    Field web3jField = LivenessPlugin.class.getDeclaredField("web3j");
    web3jField.setAccessible(true);
    web3jField.set(livenessPlugin, web3j);
  }

  @Test
  public void shouldReportSequencerInactivityToContract() throws Exception {
    JsonRpcManager jsonRpcManager = mock(JsonRpcManager.class);

    Field jsonRpcManagerField = LivenessPlugin.class.getDeclaredField("jsonRpcManager");
    jsonRpcManagerField.setAccessible(true);
    jsonRpcManagerField.set(livenessPlugin, jsonRpcManager);

    Credentials credentials =
        Credentials.create("0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63");
    Field credentialsField = LivenessPlugin.class.getDeclaredField("credentials");
    credentialsField.setAccessible(true);
    credentialsField.set(livenessPlugin, credentials);

    Field chainIdField = LivenessPlugin.class.getDeclaredField("chainId");
    chainIdField.setAccessible(true);
    chainIdField.set(livenessPlugin, new BigInteger("59144"));

    Field gasLimitField = LivenessPlugin.class.getDeclaredField("gasLimit");
    gasLimitField.setAccessible(true);
    gasLimitField.set(livenessPlugin, 100000L);

    LivenessPluginCliOptions cliOptions = mock(LivenessPluginCliOptions.class);

    Field cliOptionsField = LivenessPlugin.class.getDeclaredField("cliOptions");
    cliOptionsField.setAccessible(true);
    cliOptionsField.set(livenessPlugin, cliOptions);

    Field contractAddressField =
        LivenessPlugin.class.getDeclaredField("livenessStateContractAddress");
    contractAddressField.setAccessible(true);
    contractAddressField.set(
        livenessPlugin, Address.fromHexString("0x1230000000000000000000000000000000000000"));

    Field enabledField = LivenessPlugin.class.getDeclaredField("isPluginEnabled");
    enabledField.setAccessible(true);
    enabledField.set(livenessPlugin, true);

    Field lastProcessedBlockField = LivenessPlugin.class.getDeclaredField("lastProcessedBlock");
    lastProcessedBlockField.setAccessible(true);
    @SuppressWarnings("unchecked")
    AtomicReference<BlockHeader> lastProcessedBlock =
        (AtomicReference<BlockHeader>) lastProcessedBlockField.get(livenessPlugin);
    lastProcessedBlock.set(blockHeader);

    Field lastReportedTimestampField =
        LivenessPlugin.class.getDeclaredField("lastReportedTimestamp");
    lastReportedTimestampField.setAccessible(true);
    AtomicLong lastReportedTimestamp = (AtomicLong) lastReportedTimestampField.get(livenessPlugin);
    lastReportedTimestamp.set(currentEpochTime - 300);

    jsonRpcManager.submitNewJsonRpcCallAsync("First simulated call - Status=false");
    jsonRpcManager.submitNewJsonRpcCallAsync("Second simulated call - Status=true");

    Thread.sleep(500);

    verify(jsonRpcManager, times(2)).submitNewJsonRpcCallAsync(any(String.class));
  }

  @Test
  public void shouldNotReportWhenBlockIsRecent() throws Exception {
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
    chainIdField.set(livenessPlugin, new BigInteger("59144"));

    Credentials credentials =
        Credentials.create("0x8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63");
    Field credentialsField = LivenessPlugin.class.getDeclaredField("credentials");
    credentialsField.setAccessible(true);
    credentialsField.set(livenessPlugin, credentials);

    when(serviceManager.getService(PicoCLIOptions.class)).thenReturn(Optional.of(picoCLIOptions));
    when(serviceManager.getService(BlockchainService.class))
        .thenReturn(Optional.of(blockchainService));
    when(serviceManager.getService(BesuEvents.class)).thenReturn(Optional.of(besuEvents));

    livenessPlugin.doRegister(serviceManager);

    JsonRpcManager jsonRpcManager = mock(JsonRpcManager.class);
    Field jsonRpcManagerField = LivenessPlugin.class.getDeclaredField("jsonRpcManager");
    jsonRpcManagerField.setAccessible(true);
    jsonRpcManagerField.set(livenessPlugin, jsonRpcManager);

    long recentTimestamp = currentEpochTime - 30;
    when(blockHeader.getTimestamp()).thenReturn(recentTimestamp);

    Field lastProcessedBlockField = LivenessPlugin.class.getDeclaredField("lastProcessedBlock");
    lastProcessedBlockField.setAccessible(true);
    @SuppressWarnings("unchecked")
    AtomicReference<BlockHeader> lastProcessedBlock =
        (AtomicReference<BlockHeader>) lastProcessedBlockField.get(livenessPlugin);
    lastProcessedBlock.set(blockHeader);

    Field schedulerField = LivenessPlugin.class.getDeclaredField("scheduler");
    schedulerField.setAccessible(true);
    ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
    schedulerField.set(livenessPlugin, scheduledExecutorService);

    Method checkMethod = LivenessPlugin.class.getDeclaredMethod("checkBlockTimestampAndReport");
    checkMethod.setAccessible(true);
    checkMethod.invoke(livenessPlugin);

    verify(jsonRpcManager, never()).submitNewJsonRpcCallAsync(any(String.class));
  }
}
