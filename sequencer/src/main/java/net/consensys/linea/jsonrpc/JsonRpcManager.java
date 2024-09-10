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

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class is responsible for managing JSON-RPC requests for reporting rejected transactions
 */
@Slf4j
public class JsonRpcManager {
    private static final int MAX_THREADS = Math.min(32, Runtime.getRuntime().availableProcessors() * 2);
    private static final long MAX_RETRY_DURATION = TimeUnit.HOURS.toMillis(2);

    private final Path jsonDirectory;
    private final URI rejectedTxEndpoint;
    private final ExecutorService executorService;
    private final ScheduledExecutorService schedulerService;

    /**
     * Creates a new JSON-RPC manager.
     * @param jsonDirectory The directory to store and load JSON files containing json-rpc calls
     * @param rejectedTxEndpoint The endpoint to send rejected transactions to
     */
    public JsonRpcManager(final Path jsonDirectory, final URI rejectedTxEndpoint) {
        this.jsonDirectory = jsonDirectory;
        this.rejectedTxEndpoint = rejectedTxEndpoint;
        this.executorService = Executors.newFixedThreadPool(MAX_THREADS);
        this.schedulerService = Executors.newScheduledThreadPool(1);
    }

    /**
     * Load existing JSON-RPC and submit them.
     */
    public void start() {
        loadExistingJsonFiles();
    }

    /**
     * Shuts down the executor service and scheduler service.
     */
    public void shutdown() {
        executorService.shutdown();
        schedulerService.shutdown();
    }

    /**
     * Submits a new JSON-RPC call.
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
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(jsonDirectory, "rpc_*.json")) {
            for (Path path : stream) {
                submitJsonRpcCall(path);
            }
        } catch (IOException e) {
            log.error("Failed to load existing JSON files", e);
        }
    }

    private void submitJsonRpcCall(final Path jsonFile) {
        executorService.submit(() -> {
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
                    log.warn("Failed to send JSON-RPC call to {}, retrying: {}", rejectedTxEndpoint, jsonFile);
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
        // Implement your JSON-RPC call logic here
        // Return true if successful, false otherwise
        return false; // Placeholder
    }

    private Path saveJsonToFile(final String jsonContent) throws IOException {
        final String fileName = "rpc_" + System.currentTimeMillis() + ".json";
        final Path filePath = jsonDirectory.resolve(fileName);
        Files.write(filePath, jsonContent.getBytes());
        return filePath;
    }
}
