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

package net.consensys.linea.bundles;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.auto.service.AutoService;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.consensys.linea.AbstractLineaRequiredPlugin;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.jetbrains.annotations.NotNull;

@Slf4j
@AutoService(BesuPlugin.class)
public class BundleForwarderPlugin extends AbstractLineaRequiredPlugin {
  private static final Duration DEFAULT_CALL_TIMEOUT_MILLIS = Duration.ofSeconds(5);
  private List<BundleForwarder> forwarders;

  @Override
  public void doRegister(final ServiceManager serviceManager) {}

  @Override
  public void doStart() {
    final var forwardUrls = bundleConfiguration().bundleForwardUrls();
    if (!forwardUrls.isEmpty()) {
      final var rpcClient = createRpcClient();
      forwarders = forwardUrls.stream().map(url -> new BundleForwarder(rpcClient, url)).toList();
      bundlePoolService.subscribeTransactionBundleAdded(this::forwardBundle);
    }
  }

  private OkHttpClient createRpcClient() {
    return new OkHttpClient.Builder().callTimeout(DEFAULT_CALL_TIMEOUT_MILLIS).build();
  }

  private void forwardBundle(final TransactionBundle bundle) {
    forwarders.forEach(forwarder -> forwarder.submit(bundle));
  }

  private static class BundleForwarder {
    private final OkHttpClient rpcClient;
    private final URL recipientUrl;
    private final ExecutorService executor;
    private final BlockingQueue<Runnable> queue;

    public BundleForwarder(final OkHttpClient rpcClient, final URL recipientUrl) {
      this.rpcClient = rpcClient;
      this.recipientUrl = recipientUrl;
      this.queue = new PriorityBlockingQueue<>();
      this.executor =
          new ThreadPoolExecutor(
              0,
              1,
              10,
              TimeUnit.MINUTES,
              queue,
              Thread.ofVirtual()
                  .name("BundleForwarder[" + recipientUrl.toString() + "]", 0L)
                  .factory());
    }

    public void submit(final TransactionBundle bundle) {
      executor.submit(new SendBundleTask(bundle, 0));
    }

    public void retry(final TransactionBundle bundle, final int retry) {
      executor.submit(new SendBundleTask(bundle, retry));
    }

    @RequiredArgsConstructor
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    class SendBundleTask implements Runnable, Comparable<SendBundleTask> {
      private static final ObjectMapper OBJECT_MAPPER =
          new ObjectMapper().registerModule(new Jdk8Module());
      private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
      @EqualsAndHashCode.Include private final TransactionBundle bundle;
      // used to reschedule retries
      private final int retryCount;

      @Override
      public void run() {
        final RequestBody body;
        try {
          body = RequestBody.create(createRequestPayload(), JSON);
        } catch (JsonProcessingException e) {
          log.error("Error creating send bundle request body", e);
          return;
        }

        final Request request = new Request.Builder().url(recipientUrl).post(body).build();

        try (final Response response = rpcClient.newCall(request).execute()) {
          if (response.isSuccessful()) {
            log.info("Bundle forwarded successfully");
          } else {
            log.error("Bundle forward failed: {}", response.code());
          }
        } catch (IOException e) {
          log.warn("Error send bundle request, retrying later", e);
          retry(bundle, retryCount + 1);
        }
      }

      private String createRequestPayload() throws JsonProcessingException {
        final var request = new JsonRpcEnvelope(bundle.toBundleParameter());
        return OBJECT_MAPPER.writeValueAsString(request);
      }

      @Override
      public int compareTo(@NotNull final SendBundleTask o) {
        final int blockNumberPlusRetriesComp =
            Long.compare(this.blockNumberPlusRetries(), o.blockNumberPlusRetries());
        if (blockNumberPlusRetriesComp == 0) {
          // put retries at the end
          final int retryCountComp = Integer.compare(this.retryCount, o.retryCount);
          if (retryCountComp == 0) {
            // at last disambiguate by sequence
            return Long.compare(this.bundle.sequence(), o.bundle.sequence());
          }
          return retryCountComp;
        }
        return blockNumberPlusRetriesComp;
      }

      private long blockNumberPlusRetries() {
        return this.bundle.blockNumber() + retryCount;
      }
    }
  }

  @RequiredArgsConstructor
  @JsonAutoDetect(fieldVisibility = ANY)
  private static class JsonRpcEnvelope {
    private static final AtomicLong AUTO_ID = new AtomicLong();
    private final String jsonrpc = "2.0";
    private final String method = "linea_sendBundle";
    private final long id = AUTO_ID.getAndIncrement();
    private final BundleParameter params;
  }
}
