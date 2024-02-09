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

package net.consensys.linea.zktracer.module.mmio.dispatchers;

import lombok.RequiredArgsConstructor;
import net.consensys.linea.zktracer.module.mmio.CallStackReader;
import net.consensys.linea.zktracer.module.mmio.MmioData;
import net.consensys.linea.zktracer.module.mmu.MmuData;
import net.consensys.linea.zktracer.types.UnsignedByte;

@RequiredArgsConstructor
public class KillingOneDispatcher implements MmioDispatcher {
  private final MmuData mmuData;

  private final CallStackReader callStackReader;

  @Override
  public MmioData dispatch() {
    MmioData mmioData = new MmioData();

    int targetContext = mmuData.targetContextId();
    mmioData.cnA(targetContext);
    mmioData.cnB(0);
    mmioData.cnC(0);

    int targetLimbOffset = mmuData.targetLimbOffset().toInt();
    mmioData.indexA(targetLimbOffset);
    mmioData.indexB(0);
    mmioData.indexC(0);

    mmioData.valA(callStackReader.valueFromMemory(mmioData.cnA(), mmioData.indexA()));
    mmioData.valB(UnsignedByte.EMPTY_BYTES16);
    mmioData.valC(UnsignedByte.EMPTY_BYTES16);

    mmioData.valANew(UnsignedByte.EMPTY_BYTES16);
    mmioData.valBNew(UnsignedByte.EMPTY_BYTES16);
    mmioData.valCNew(UnsignedByte.EMPTY_BYTES16);

    mmioData.updateLimbsInMemory(callStackReader.callStack());

    return mmioData;
  }

  @Override
  public void update(MmioData mmioData, int counter) {}
}