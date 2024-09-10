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

package net.consensys.linea.jsonrpc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** This class is responsible for managing JSON-RPC requests for reporting rejected transactions */
@Slf4j
public class JsonRpcManager {
  private static final int MAX_THREADS =
      Math.min(32, Runtime.getRuntime().availableProcessors() * 2);
  private static final long MAX_RETRY_DURATION = TimeUnit.HOURS.toMillis(2);
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final OkHttpClient client = new OkHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final Path rejTxRpcDirectory;
  private final URI rejectedTxEndpoint;
  private final ExecutorService executorService;
  private final ScheduledExecutorService schedulerService;

  /**
   * Creates a new JSON-RPC manager.
   *
   * @param besuDataDir Path to Besu data directory. The json-rpc files will be stored here under
   *     rej-tx-rpc subdirectory.
   * @param rejectedTxEndpoint The endpoint to send rejected transactions to
   */
  public JsonRpcManager(final Path besuDataDir, final URI rejectedTxEndpoint) {
    this.rejTxRpcDirectory = besuDataDir.resolve("rej_tx_rpc");
    this.rejectedTxEndpoint = rejectedTxEndpoint;
    this.executorService = Executors.newFixedThreadPool(MAX_THREADS);
    this.schedulerService = Executors.newScheduledThreadPool(1);
  }

  /** Load existing JSON-RPC and submit them. */
  public JsonRpcManager start() {
    try {
      // Create the rej-tx-rpc directory if it doesn't exist
      Files.createDirectories(rejTxRpcDirectory);

      // Load existing JSON files
      loadExistingJsonFiles();
      return this;
    } catch (IOException e) {
      log.error("Failed to create or access rej-tx-rpc directory", e);
      throw new UncheckedIOException(e);
    }
  }

  /** Shuts down the executor service and scheduler service. */
  public void shutdown() {
    executorService.shutdown();
    schedulerService.shutdown();
  }

  /**
   * Submits a new JSON-RPC call.
   *
   * @param jsonContent The JSON content to submit
   */
  public void submitNewJsonRpcCall(final String jsonContent) {
    try {
      final Path jsonFile = saveJsonToFile(jsonContent);
      submitJsonRpcCall(jsonFile);
    } catch (final IOException e) {
      log.error("Failed to save JSON content", e);
    }
  }

  private void loadExistingJsonFiles() {
    try (final DirectoryStream<Path> stream =
        Files.newDirectoryStream(rejTxRpcDirectory, "rpc_*.json")) {
      for (final Path path : stream) {
        submitJsonRpcCall(path);
      }
    } catch (final IOException e) {
      log.error("Failed to load existing JSON files", e);
    }
  }

  private void submitJsonRpcCall(final Path jsonFile) {
    executorService.submit(
        () -> {
          if (!Files.exists(jsonFile)) {
            log.debug("json-rpc file no longer exists, skipping processing: {}", jsonFile);
            return;
          }
          try {
            final String jsonContent = new String(Files.readAllBytes(jsonFile));
            final boolean success = sendJsonRpcCall(jsonContent);
            if (success) {
              Files.deleteIfExists(jsonFile);
            } else {
              log.warn(
                  "Failed to send JSON-RPC call to {}, retrying: {}", rejectedTxEndpoint, jsonFile);
              scheduleRetry(jsonFile, System.currentTimeMillis(), 1000);
            }
          } catch (final IOException e) {
            log.error("Failed to process json-rpc file: {}", jsonFile, e);
          }
        });
  }

  private void scheduleRetry(final Path jsonFile, final long startTime, final long delay) {
    // check if we're still within the maximum retry duration
    if (System.currentTimeMillis() - startTime < MAX_RETRY_DURATION) {
      // schedule a retry
      schedulerService.schedule(() -> submitJsonRpcCall(jsonFile), delay, TimeUnit.MILLISECONDS);

      // exponential backoff with a maximum delay of 1 minute
      long nextDelay = Math.min(delay * 2, TimeUnit.MINUTES.toMillis(1));
      scheduleRetry(jsonFile, startTime, nextDelay);
    }
  }

  private boolean sendJsonRpcCall(final String jsonContent) {
    final RequestBody body = RequestBody.create(jsonContent, JSON);
    final Request request =
        new Request.Builder().url(rejectedTxEndpoint.toString()).post(body).build();

    try (final Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        log.error("Unexpected response code from rejected-tx endpoint: {}", response.code());
        return false;
      }

      // process the response body here ...
      final String responseBody = response.body() != null ? response.body().string() : null;
      if (responseBody == null) {
        log.error("Unexpected empty response body from rejected-tx endpoint");
        return false;
      }

      final JsonNode jsonNode = objectMapper.readTree(responseBody);
      if (jsonNode == null) {
        log.error("Failed to parse JSON response from rejected-tx endpoint: {}", responseBody);
        return false;
      }
      if (jsonNode.has("error")) {
        log.error("Error response from rejected-tx endpoint: {}", jsonNode.get("error"));
        return false;
      }
      // Check for result
      if (jsonNode.has("result")) {
        String status = jsonNode.get("result").get("status").asText();
        log.debug("Rejected-tx JSON-RPC call successful. Status: {}", status);
        return true;
      }

      log.warn("Unexpected rejected-tx JSON-RPC response format: {}", responseBody);
      return false;
    } catch (final IOException e) {
      log.error("Failed to send JSON-RPC call to rejected-tx endpoint {}", rejectedTxEndpoint, e);
      return false;
    }
  }

  private Path saveJsonToFile(final String jsonContent) throws IOException {
    final String fileName = "rpc_" + System.currentTimeMillis() + ".json";
    final Path filePath = rejTxRpcDirectory.resolve(fileName);
    Files.write(filePath, jsonContent.getBytes());
    return filePath;
  }
}
