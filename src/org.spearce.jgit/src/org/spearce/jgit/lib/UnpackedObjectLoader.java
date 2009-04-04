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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.spearce.jgit.errors.CorruptObjectException;
import org.spearce.jgit.util.MutableInteger;
import org.spearce.jgit.util.RawParseUtils;

/**
 * Loose object loader. This class loads an object not stored in a pack.
 */
public class UnpackedObjectLoader extends ObjectLoader {
	private final int objectType;

	private final int objectSize;

	private final byte[] bytes;

	/**
	 * Construct an ObjectLoader for the specified SHA-1
	 *
	 * @param db
	 *            repository
	 * @param id
	 *            SHA-1
	 * @throws IOException
	 */
	public UnpackedObjectLoader(final Repository db, final AnyObjectId id)
			throws IOException {
		this(readCompressed(db, id), id);
	}

	private static byte[] readCompressed(final Repository db,
			final AnyObjectId id) throws FileNotFoundException, IOException {
		final FileInputStream objStream = new FileInputStream(db.toFile(id));
		final byte[] compressed;
		try {
			compressed = new byte[objStream.available()];
			int off = 0;
			while (off < compressed.length)
				off += objStream.read(compressed, off, compressed.length - off);
		} finally {
			objStream.close();
		}
		return compressed;
	}

	/**
	 * Construct an ObjectLoader from a loose object's compressed form.
	 *
	 * @param compressed
	 *            entire content of the loose object file.
	 * @throws CorruptObjectException
	 *             The compressed data supplied does not match the format for a
	 *             valid loose object.
	 */
	public UnpackedObjectLoader(final byte[] compressed)
			throws CorruptObjectException {
		this(compressed, null);
	}

	private UnpackedObjectLoader(final byte[] compressed, final AnyObjectId id)
			throws CorruptObjectException {
		// Try to determine if this is a legacy format loose object or
		// a new style loose object. The legacy format was completely
		// compressed with zlib so the first byte must be 0x78 (15-bit
		// window size, deflated) and the first 16 bit word must be
		// evenly divisible by 31. Otherwise its a new style loose
		// object.
		//
		final Inflater inflater = InflaterCache.get();
		try {
			final int fb = compressed[0] & 0xff;
			if (fb == 0x78 && (((fb << 8) | compressed[1] & 0xff) % 31) == 0) {
				inflater.setInput(compressed);
				final byte[] hdr = new byte[64];
				int avail = 0;
				while (!inflater.finished() && avail < hdr.length)
					try {
						avail += inflater.inflate(hdr, avail, hdr.length
								- avail);
					} catch (DataFormatException dfe) {
						final CorruptObjectException coe;
						coe = new CorruptObjectException(id, "bad stream");
						coe.initCause(dfe);
						inflater.end();
						throw coe;
					}
				if (avail < 5)
					throw new CorruptObjectException(id, "no header");

				final MutableInteger p = new MutableInteger();
				objectType = Constants.decodeTypeString(id, hdr, (byte) ' ', p);
				objectSize = RawParseUtils.parseBase10(hdr, p.value, p);
				if (objectSize < 0)
					throw new CorruptObjectException(id, "negative size");
				if (hdr[p.value++] != 0)
					throw new CorruptObjectException(id, "garbage after size");
				bytes = new byte[objectSize];
				if (p.value < avail)
					System.arraycopy(hdr, p.value, bytes, 0, avail - p.value);
				decompress(id, inflater, avail - p.value);
			} else {
				int p = 0;
				int c = compressed[p++] & 0xff;
				final int typeCode = (c >> 4) & 7;
				int size = c & 15;
				int shift = 4;
				while ((c & 0x80) != 0) {
					c = compressed[p++] & 0xff;
					size += (c & 0x7f) << shift;
					shift += 7;
				}

				switch (typeCode) {
				case Constants.OBJ_COMMIT:
				case Constants.OBJ_TREE:
				case Constants.OBJ_BLOB:
				case Constants.OBJ_TAG:
					objectType = typeCode;
					break;
				default:
					throw new CorruptObjectException(id, "invalid type");
				}

				objectSize = size;
				bytes = new byte[objectSize];
				inflater.setInput(compressed, p, compressed.length - p);
				decompress(id, inflater, 0);
			}
		} finally {
			InflaterCache.release(inflater);
		}
	}

	private void decompress(final AnyObjectId id, final Inflater inf, int p)
			throws CorruptObjectException {
		try {
			while (!inf.finished())
				p += inf.inflate(bytes, p, objectSize - p);
		} catch (DataFormatException dfe) {
			final CorruptObjectException coe;
			coe = new CorruptObjectException(id, "bad stream");
			coe.initCause(dfe);
			throw coe;
		}
		if (p != objectSize)
			throw new CorruptObjectException(id, "incorrect length");
	}

	public int getType() {
		return objectType;
	}

	public long getSize() {
		return objectSize;
	}

	@Override
	public byte[] getCachedBytes() throws IOException {
		return bytes;
	}

	@Override
	public int getRawType() {
		return objectType;
	}

	@Override
	public long getRawSize() {
		return objectSize;
	}
}
