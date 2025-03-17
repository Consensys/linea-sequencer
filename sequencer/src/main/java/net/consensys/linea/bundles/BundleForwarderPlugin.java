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

import java.net.URL;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.auto.service.AutoService;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import net.consensys.linea.AbstractLineaRequiredPlugin;
import net.consensys.linea.rpc.methods.LineaSendBundle;
import okhttp3.OkHttpClient;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.jetbrains.annotations.NotNull;

@AutoService(BesuPlugin.class)
public class BundleForwarderPlugin extends AbstractLineaRequiredPlugin {
  private OkHttpClient rpcClient;
  private List<BundleForwarder> forwarders;

  @Override
  public void doRegister(final ServiceManager serviceManager) {}

  @Override
  public void doStart() {
    final var forwardUrls = bundleConfiguration().bundleForwardUrls();
    if (!forwardUrls.isEmpty()) {
      rpcClient = new OkHttpClient();
      forwarders = forwardUrls.stream().map(BundleForwarder::new).toList();
      bundlePoolService.subscribeTransactionBundleAdded(this::forwardBundle);
    }
  }

  private void forwardBundle(final TransactionBundle bundle) {
    final var forwardTask = new ForwardBundleTask(bundle, 0);
    forwarders.forEach(bundleForwarder -> bundleForwarder.submit(forwardTask));
  }

  @RequiredArgsConstructor
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  private static class ForwardBundleTask implements Runnable, Comparable<ForwardBundleTask> {
    @EqualsAndHashCode.Include private final TransactionBundle bundle;
    // used to reschedule retries
    private final int retryCount;

    @Override
    public void run() {

    }

    @Override
    public int compareTo(@NotNull final ForwardBundleTask o) {
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

  private static class BundleForwarder {
    private final URL recipientUrl;
    private final ExecutorService executor;
    private final BlockingQueue<Runnable> queue;

    public BundleForwarder(final URL recipientUrl) {
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

    public void submit(final ForwardBundleTask task) {
      executor.submit(task);
    }
  }
}
