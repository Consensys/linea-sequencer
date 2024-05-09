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

package net.consensys.linea.extradata;

import java.util.Optional;

import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;
import net.consensys.linea.AbstractLineaRequiredPlugin;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.BesuEvents;

/** This plugin registers handlers that are activated when new blocks are imported */
@Slf4j
@AutoService(BesuPlugin.class)
public class LineaExtraDataPlugin extends AbstractLineaRequiredPlugin {
  public static final String NAME = "linea";
  private BesuEvents besuEventsService;

  @Override
  public Optional<String> getName() {
    return Optional.of(NAME);
  }

  @Override
  public void doRegister(final BesuContext context) {
    besuEventsService =
        context
            .getService(BesuEvents.class)
            .orElseThrow(
                () -> new RuntimeException("Failed to obtain BesuEvents from the BesuContext."));
  }

  @Override
  public void beforeExternalServices() {
    super.beforeExternalServices();
    besuEventsService.addBlockAddedListener(new LineaExtraDataHandler());
  }
}
