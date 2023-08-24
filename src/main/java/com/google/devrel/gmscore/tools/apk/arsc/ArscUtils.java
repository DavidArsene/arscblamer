/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devrel.gmscore.tools.apk.arsc;

import com.google.common.base.Preconditions;
import com.google.devrel.gmscore.tools.common.ApkUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

/** Utility class for loading a resource table from an {@code apk}. */
public class ArscUtils {

  private static final String RESOURCES_ARSC = "resources.arsc";

  /** Get the resources.arsc resource table in the {@code apk}. */
  public static ResourceTableChunk getResourceTable(@Nullable File apk) throws IOException {
    Preconditions.checkNotNull(apk, "APK is required.");
    byte[] resourceBytes = ApkUtils.getFile(apk, RESOURCES_ARSC);
    if (resourceBytes == null) {
      throw new IOException(String.format("Unable to find %s in APK.", RESOURCES_ARSC));
    }
    List<Chunk> chunks = new ResourceFile(resourceBytes).getChunks();
    Preconditions.checkState(chunks.size() == 1,
        "%s should only have one root chunk.", RESOURCES_ARSC);
    Chunk resourceTable = chunks.get(0);
    Preconditions.checkState(resourceTable instanceof ResourceTableChunk,
        "%s root chunk must be a ResourceTableChunk.", RESOURCES_ARSC);
    return (ResourceTableChunk) resourceTable;
  }
}
