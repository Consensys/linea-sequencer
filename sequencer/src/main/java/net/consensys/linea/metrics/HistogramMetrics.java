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

package net.consensys.linea.metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Histogram;
import org.hyperledger.besu.plugin.services.metrics.LabelledGauge;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

@Slf4j
public class HistogramMetrics {

  public interface LabelValue {
    String value();
  }

  private static final double[] DEFAULT_HISTOGRAM_BUCKETS = {0.9, 1.0, 1.2, 2, 5, 10, 100, 1000};
  private static final String LABEL_VALUES_SEPARATOR = "\u2060";
  private final LabelledMetric<Histogram> histogram;
  private final Map<String, Double> mins;
  private final Map<String, Double> maxs;

  @SafeVarargs
  public HistogramMetrics(
      final MetricsSystem metricsSystem,
      final LineaMetricCategory category,
      final String name,
      final String help,
      final Class<? extends LabelValue>... labels) {

    final var labelNames = getLabelNames(labels);

    final LabelledGauge minRatio =
        metricsSystem.createLabelledGauge(category, name + "_min", "Lowest " + help, labelNames);

    final LabelledGauge maxRatio =
        metricsSystem.createLabelledGauge(category, name + "_max", "Highest " + help, labelNames);

    final var combinations = getLabelValuesCombinations(labels);
    mins = HashMap.newHashMap(combinations.size());
    maxs = HashMap.newHashMap(combinations.size());
    for (final var combination : combinations) {
      final var key = String.join(LABEL_VALUES_SEPARATOR, combination);
      mins.put(key, Double.POSITIVE_INFINITY);
      minRatio.labels(() -> mins.get(key), combination);
      maxs.put(key, 0.0);
      maxRatio.labels(() -> maxs.get(key), combination);
    }

    this.histogram =
        metricsSystem.createLabelledHistogram(
            category,
            name,
            StringUtils.capitalize(help) + " buckets",
            DEFAULT_HISTOGRAM_BUCKETS,
            labelNames);
  }

  @SafeVarargs
  private String[] getLabelNames(final Class<? extends LabelValue>... labels) {
    return Arrays.stream(labels)
        .map(Class::getSimpleName)
        .map(sn -> sn.toLowerCase(Locale.ROOT))
        .toArray(String[]::new);
  }

  @SafeVarargs
  private List<String[]> getLabelValuesCombinations(final Class<? extends LabelValue>... labels) {
    if (labels.length == 0) {
      return Collections.singletonList(new String[0]);
    }
    if (labels.length == 1) {
      return Arrays.stream(labels[0].getEnumConstants())
          .map(lv -> new String[] {lv.value()})
          .toList();
    }
    final var head = labels[0];
    final var tail = Arrays.copyOfRange(labels, 1, labels.length);
    final var tailCombinations = getLabelValuesCombinations(tail);
    final int newSize = tailCombinations.size() * head.getEnumConstants().length;
    final List<String[]> combinations = new ArrayList<>(newSize);
    for (final var headValue : head.getEnumConstants()) {
      for (final var tailValues : tailCombinations) {
        final var combination = new String[tailValues.length + 1];
        combination[0] = headValue.value();
        System.arraycopy(tailValues, 0, combination, 1, tailValues.length);
        combinations.add(combination);
      }
    }
    return combinations;
  }

  public void track(final double value, final String... labelValues) {

    final var key = String.join(LABEL_VALUES_SEPARATOR, labelValues);

    // Update lowest seen
    mins.compute(key, (unused, currMin) -> Math.min(currMin, value));

    // Update highest seen
    maxs.compute(key, (unused, currMax) -> Math.max(currMax, value));

    // Record the observation
    histogram.labels(labelValues).observe(value);
  }
}
