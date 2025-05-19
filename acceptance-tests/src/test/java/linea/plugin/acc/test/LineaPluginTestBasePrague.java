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

/*
 * This file initializes a Besu node configured for the Prague fork and makes it available to acceptance tests.
 * We take code from the PragueAcceptanceTestHelper in the Besu codebase to help us emulate Engine API calls to the Besu node.
 *
 * We intend to replace the LineaPluginTestBase class via the strangler pattern—
 * i.e., we will gradually replace references to LineaPluginTestBase with
 * LineaPluginTestBasePrague in test classes, one by one.
 */

package linea.plugin.acc.test;

import static net.consensys.linea.metrics.LineaMetricCategory.PRICING_CONF;
import static net.consensys.linea.metrics.LineaMetricCategory.SEQUENCER_PROFITABILITY;
import static net.consensys.linea.metrics.LineaMetricCategory.TX_POOL_PROFITABILITY;
import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import linea.plugin.acc.test.tests.web3j.generated.AcceptanceTestToken;
import linea.plugin.acc.test.tests.web3j.generated.EcAdd;
import linea.plugin.acc.test.tests.web3j.generated.EcMul;
import linea.plugin.acc.test.tests.web3j.generated.EcPairing;
import linea.plugin.acc.test.tests.web3j.generated.EcRecover;
import linea.plugin.acc.test.tests.web3j.generated.ExcludedPrecompiles;
import linea.plugin.acc.test.tests.web3j.generated.ModExp;
import linea.plugin.acc.test.tests.web3j.generated.MulmodExecutor;
import linea.plugin.acc.test.tests.web3j.generated.RevertExample;
import linea.plugin.acc.test.tests.web3j.generated.SimpleStorage;
import linea.plugin.acc.test.utils.MemoryAppender;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt32;
import org.hyperledger.besu.consensus.clique.CliqueExtraData;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadAttributesParameter;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.plugin.services.metrics.MetricCategory;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.EngineAPIService;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.account.Accounts;
import org.hyperledger.besu.tests.acceptance.dsl.condition.txpool.TxPoolConditions;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.RunnableNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfigurationBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.NodeConfigurationFactory;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory.CliqueOptions;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.txpool.TxPoolTransactions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.exceptions.TransactionException;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;

/** Base class for plugin tests. */
@Slf4j
public abstract class LineaPluginTestBasePrague extends LineaPluginTestBase {
  protected EngineAPIService engineApiService;
  private final String GENESIS_FILE_TEMPLATE_PATH = "/clique/clique-prague.json.tpl";

  @BeforeEach
  @Override
  public void setup() throws Exception {
    minerNode =
        createCliqueNodeWithExtraCliOptionsAndRpcApis(
            "miner1", getCliqueOptions(), getTestCliOptions(), Set.of("LINEA", "MINER"), true);
    minerNode.setTransactionPoolConfiguration(
        ImmutableTransactionPoolConfiguration.builder()
            .from(TransactionPoolConfiguration.DEFAULT)
            .noLocalPriority(true)
            .build());
    cluster.start(minerNode);
    this.engineApiService = new EngineAPIService(minerNode, ethTransactions, 1670496243);
  }

  // Ideally GenesisConfigurationFactory.createCliqueGenesisConfig would support a custom template
  // path. So we have resorted to inlining its logic here to allow a flexible file path.
  @Override
  protected String provideGenesisConfig(
      final Collection<? extends RunnableNode> validators, final CliqueOptions cliqueOptions) {
    // Target state
    final String genesisTemplate =
        GenesisConfigurationFactory.readGenesisFile(GENESIS_FILE_TEMPLATE_PATH);
    final String hydratedGenesisTemplate =
        genesisTemplate
            .replace("%blockperiodseconds%", String.valueOf(cliqueOptions.blockPeriodSeconds()))
            .replace("%epochlength%", String.valueOf(cliqueOptions.epochLength()))
            .replace("%createemptyblocks%", String.valueOf(cliqueOptions.createEmptyBlocks()));

    final List<Address> addresses =
        validators.stream().map(RunnableNode::getAddress).collect(Collectors.toList());
    final String extraDataString = CliqueExtraData.createGenesisExtraDataString(addresses);
    final String genesis = hydratedGenesisTemplate.replaceAll("%extraData%", extraDataString);

    return maybeCustomGenesisExtraData()
        .map(ed -> setGenesisCustomExtraData(genesis, ed))
        .orElse(genesis);
  }
}
