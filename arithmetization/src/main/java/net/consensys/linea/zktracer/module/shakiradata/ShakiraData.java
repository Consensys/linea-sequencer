/*
 * Copyright ConsenSys Inc.
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

package net.consensys.linea.zktracer.module.shakiradata;

import java.nio.MappedByteBuffer;
import java.util.List;

import net.consensys.linea.zktracer.ColumnHeader;
import net.consensys.linea.zktracer.container.stacked.set.StackedSet;
import net.consensys.linea.zktracer.module.Module;

public class ShakiraData implements Module {
  private StackedSet<ShakiraDataOperation> state = new StackedSet<>();

  @Override
  public String moduleKey() {
    return "SHAKIRA_DATA";
  }

  @Override
  public void enterTransaction() {
    this.state.enter();
  }

  @Override
  public void popTransaction() {
    this.state.pop();
  }

  @Override
  public int lineCount() {
    return this.state.lineCount();
  }

  @Override
  public List<ColumnHeader> columnsHeaders() {
    return Trace.headers(this.lineCount());
  }

  public int call(final ShakiraDataOperation operation) {
    this.state.add(operation);

    return operation.prevHubStamp();
  }

  @Override
  public void commit(List<MappedByteBuffer> buffers) {
    Trace trace = new Trace(buffers);
    int stamp = 0;
    for (ShakiraDataOperation o : this.state) {
      stamp++;
      o.trace(trace, stamp);
    }
  }
}
