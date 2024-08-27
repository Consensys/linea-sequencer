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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class JsonRpcClient {
  private static final int MAX_RETRIES = 3;
  private static final ExecutorService executorService = Executors.newCachedThreadPool();

  public static String sendRequest(final URI endpoint, final String jsonInputString)
      throws Exception {
    HttpURLConnection conn = getHttpURLConnection(endpoint, jsonInputString);

    int responseCode = conn.getResponseCode();
    if (responseCode == HttpURLConnection.HTTP_OK) {
      try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8)) {
        return scanner.useDelimiter("\\A").next();
      }
    } else {
      throw new RuntimeException("Failed : HTTP error code : " + responseCode);
    }
  }

  private static HttpURLConnection getHttpURLConnection(
      final URI endpoint, final String jsonInputString) throws IOException {
    final URL url = endpoint.toURL();
    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json; utf-8");
    conn.setRequestProperty("Accept", "application/json");
    conn.setDoOutput(true);

    try (final OutputStream os = conn.getOutputStream()) {
      final byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
      os.write(input, 0, input.length);
    }
    return conn;
  }

  public static Future<String> sendRequestWithRetries(
      final URI endpoint, final String jsonInputString) {
    Callable<String> task =
        () -> {
          int attempt = 0;
          while (attempt < MAX_RETRIES) {
            try {
              return sendRequest(endpoint, jsonInputString);
            } catch (Exception e) {
              attempt++;
              if (attempt >= MAX_RETRIES) {
                throw e;
              }
            }
          }
          return null;
        };
    return executorService.submit(task);
  }
}
