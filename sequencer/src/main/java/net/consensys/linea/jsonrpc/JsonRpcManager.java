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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
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
  private static final long INITIAL_RETRY_DELAY = 1000L;
  private static final long MAX_RETRY_DURATION = TimeUnit.HOURS.toMillis(2);
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  private final OkHttpClient client = new OkHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Map<Path, Long> fileStartTimes = new ConcurrentHashMap<>();

  private final Path rejTxRpcDirectory;
  private final URI rejectedTxEndpoint;
  private final ExecutorService executorService;
  private final ScheduledExecutorService retrySchedulerService;

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
    this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    this.retrySchedulerService = Executors.newSingleThreadScheduledExecutor();
  }

  /** Load existing JSON-RPC and submit them. */
  public JsonRpcManager start() {
    try {
      // Create the rej-tx-rpc directory if it doesn't exist
      Files.createDirectories(rejTxRpcDirectory);

      // Load existing JSON files
      processExistingJsonFiles();
      return this;
    } catch (IOException e) {
      log.error("Failed to create or access rej-tx-rpc directory", e);
      throw new UncheckedIOException(e);
    }
  }

  /** Shuts down the executor service and scheduler service. */
  public void shutdown() {
    executorService.shutdown();
    retrySchedulerService.shutdown();
  }

  /**
   * Submits a new JSON-RPC call.
   *
   * @param jsonContent The JSON content to submit
   */
  public void submitNewJsonRpcCall(final String jsonContent) {
    try {
      final Path jsonFile = saveJsonToFile(jsonContent);
      fileStartTimes.put(jsonFile, System.currentTimeMillis());
      submitJsonRpcCall(jsonFile, INITIAL_RETRY_DELAY);
    } catch (final IOException e) {
      log.error("Failed to save JSON content", e);
    }
  }

  private void processExistingJsonFiles() {
    try {
      final TreeSet<Path> sortedFiles = new TreeSet<>(Comparator.comparing(Path::getFileName));

      try (DirectoryStream<Path> stream =
          Files.newDirectoryStream(rejTxRpcDirectory, "rpc_*.json")) {
        for (Path path : stream) {
          sortedFiles.add(path);
        }
      }

      for (Path path : sortedFiles) {
        fileStartTimes.put(path, System.currentTimeMillis());
        submitJsonRpcCall(path, INITIAL_RETRY_DELAY);
      }

      log.info("Loaded {} existing JSON files for rej-tx reporting", sortedFiles.size());
    } catch (final IOException e) {
      log.error("Failed to load existing JSON files", e);
    }
  }

  private void submitJsonRpcCall(final Path jsonFile, final long nextDelay) {
    executorService.submit(
        () -> {
          if (!Files.exists(jsonFile)) {
            log.debug("json-rpc file no longer exists, skipping processing: {}", jsonFile);
            fileStartTimes.remove(jsonFile);
            return;
          }
          try {
            final String jsonContent = new String(Files.readAllBytes(jsonFile));
            final boolean success = sendJsonRpcCall(jsonContent);
            if (success) {
              Files.deleteIfExists(jsonFile);
              fileStartTimes.remove(jsonFile);
            } else {
              log.error(
                  "Failed to send JSON-RPC call to {}, retrying: {}", rejectedTxEndpoint, jsonFile);
              scheduleRetry(jsonFile, nextDelay);
            }
          } catch (final Exception e) {
            log.error("Failed to process json-rpc file: {}", jsonFile, e);
            scheduleRetry(jsonFile, nextDelay);
          }
        });
  }

  private void scheduleRetry(final Path jsonFile, final long currentDelay) {
    final Long startTime = fileStartTimes.get(jsonFile);
    if (startTime == null) {
      log.debug("No start time found for file: {}. Skipping retry.", jsonFile);
      return;
    }

    // check if we're still within the maximum retry duration
    if (System.currentTimeMillis() - startTime < MAX_RETRY_DURATION) {
      // schedule a retry with exponential backoff
      long nextDelay = Math.min(currentDelay * 2, TimeUnit.MINUTES.toMillis(1)); // Cap at 1 minute
      retrySchedulerService.schedule(
          () -> submitJsonRpcCall(jsonFile, nextDelay), currentDelay, TimeUnit.MILLISECONDS);
    } else {
      log.error("Exceeded maximum retry duration for rej-tx json-rpc file: {}", jsonFile);
      fileStartTimes.remove(jsonFile);
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
    long timestamp = System.currentTimeMillis();
    for (int attempt = 0; attempt < 100; attempt++) {
      final String fileName = String.format("rpc_%d_%d.json", timestamp, attempt);
      final Path filePath = rejTxRpcDirectory.resolve(fileName);
      try {
        return Files.writeString(filePath, jsonContent, StandardOpenOption.CREATE_NEW);
      } catch (final FileAlreadyExistsException e) {
        log.trace("File already exists {}, retrying.", filePath);
      }
    }
    throw new IOException("Failed to save JSON content after 100 attempts");
  }
}
