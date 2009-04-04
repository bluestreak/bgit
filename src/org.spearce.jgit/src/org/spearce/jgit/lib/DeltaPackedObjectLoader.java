/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Git Development Community nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.spearce.jgit.lib;

import java.io.IOException;
import java.util.zip.DataFormatException;

import org.spearce.jgit.errors.CorruptObjectException;

/** Reader for a deltified object stored in a pack file. */
abstract class DeltaPackedObjectLoader extends PackedObjectLoader {
	private static final int OBJ_COMMIT = Constants.OBJ_COMMIT;

	private final int deltaSize;

	DeltaPackedObjectLoader(final WindowCursor curs, final PackFile pr,
			final long dataOffset, final long objectOffset, final int deltaSz) {
		super(curs, pr, dataOffset, objectOffset);
		objectType = -1;
		deltaSize = deltaSz;
	}

	public int getType() throws IOException {
		if (objectType < 0)
			getCachedBytes();
		return objectType;
	}

	public long getSize() throws IOException {
		if (objectType < 0)
			getCachedBytes();
		return objectSize;
	}

	@Override
	public byte[] getCachedBytes() throws IOException {
		if (objectType != OBJ_COMMIT) {
			final UnpackedObjectCache.Entry cache = pack.readCache(dataOffset);
			if (cache != null) {
				curs.release();
				objectType = cache.type;
				objectSize = cache.data.length;
				return cache.data;
			}
		}

		try {
			final PackedObjectLoader baseLoader = getBaseLoader();
			final byte[] data = BinaryDelta.apply(baseLoader.getCachedBytes(),
					pack.decompress(dataOffset, deltaSize, curs));
			curs.release();
			objectType = baseLoader.getType();
			objectSize = data.length;
			if (objectType != OBJ_COMMIT)
				pack.saveCache(dataOffset, data, objectType);
			return data;
		} catch (DataFormatException dfe) {
			final CorruptObjectException coe;
			coe = new CorruptObjectException("Object at " + dataOffset + " in "
					+ pack.getPackFile() + " has bad zlib stream");
			coe.initCause(dfe);
			throw coe;
		}
	}

	@Override
	public long getRawSize() {
		return deltaSize;
	}

	/**
	 * @return the object loader for the base object
	 * @throws IOException
	 */
	protected abstract PackedObjectLoader getBaseLoader() throws IOException;
}
