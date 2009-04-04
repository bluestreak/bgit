/*
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

import java.io.UnsupportedEncodingException;

import org.spearce.jgit.util.NB;

/**
 * A mutable SHA-1 abstraction.
 */
public class MutableObjectId extends AnyObjectId {
	/**
	 * Empty constructor. Initialize object with default (zeros) value.
	 */
	public MutableObjectId() {
		super();
	}

	/**
	 * Copying constructor.
	 * 
	 * @param src
	 *            original entry, to copy id from
	 */
	MutableObjectId(MutableObjectId src) {
		this.w1 = src.w1;
		this.w2 = src.w2;
		this.w3 = src.w3;
		this.w4 = src.w4;
		this.w5 = src.w5;
	}

	/** Make this id match {@link ObjectId#zeroId()}. */
	public void clear() {
		w1 = 0;
		w2 = 0;
		w3 = 0;
		w4 = 0;
		w5 = 0;
	}

	/**
	 * Convert an ObjectId from raw binary representation.
	 * 
	 * @param bs
	 *            the raw byte buffer to read from. At least 20 bytes must be
	 *            available within this byte array.
	 */
	public void fromRaw(final byte[] bs) {
		fromRaw(bs, 0);
	}

	/**
	 * Convert an ObjectId from raw binary representation.
	 * 
	 * @param bs
	 *            the raw byte buffer to read from. At least 20 bytes after p
	 *            must be available within this byte array.
	 * @param p
	 *            position to read the first byte of data from.
	 */
	public void fromRaw(final byte[] bs, final int p) {
		w1 = NB.decodeInt32(bs, p);
		w2 = NB.decodeInt32(bs, p + 4);
		w3 = NB.decodeInt32(bs, p + 8);
		w4 = NB.decodeInt32(bs, p + 12);
		w5 = NB.decodeInt32(bs, p + 16);
	}

	/**
	 * Convert an ObjectId from binary representation expressed in integers.
	 * 
	 * @param ints
	 *            the raw int buffer to read from. At least 5 integers must be
	 *            available within this integers array.
	 */
	public void fromRaw(final int[] ints) {
		fromRaw(ints, 0);
	}

	/**
	 * Convert an ObjectId from binary representation expressed in integers.
	 * 
	 * @param ints
	 *            the raw int buffer to read from. At least 5 integers after p
	 *            must be available within this integers array.
	 * @param p
	 *            position to read the first integer of data from.
	 * 
	 */
	public void fromRaw(final int[] ints, final int p) {
		w1 = ints[p];
		w2 = ints[p + 1];
		w3 = ints[p + 2];
		w4 = ints[p + 3];
		w5 = ints[p + 4];
	}

	/**
	 * Convert an ObjectId from hex characters (US-ASCII).
	 * 
	 * @param buf
	 *            the US-ASCII buffer to read from. At least 40 bytes after
	 *            offset must be available within this byte array.
	 * @param offset
	 *            position to read the first character from.
	 */
	public void fromString(final byte[] buf, final int offset) {
		fromHexString(buf, offset);
	}

	/**
	 * Convert an ObjectId from hex characters.
	 * 
	 * @param str
	 *            the string to read from. Must be 40 characters long.
	 */
	public void fromString(final String str) {
		if (str.length() != STR_LEN)
			throw new IllegalArgumentException("Invalid id: " + str);
		fromHexString(Constants.encodeASCII(str), 0);
	}

	private void fromHexString(final byte[] bs, int p) {
		try {
			w1 = hexUInt32(bs, p);
			w2 = hexUInt32(bs, p + 8);
			w3 = hexUInt32(bs, p + 16);
			w4 = hexUInt32(bs, p + 24);
			w5 = hexUInt32(bs, p + 32);
		} catch (ArrayIndexOutOfBoundsException e1) {
			try {
				final String str = new String(bs, p, STR_LEN, "US-ASCII");
				throw new IllegalArgumentException("Invalid id: " + str);
			} catch (UnsupportedEncodingException e2) {
				throw new IllegalArgumentException("Invalid id");
			} catch (StringIndexOutOfBoundsException e2) {
				throw new IllegalArgumentException("Invalid id");
			}
		}
	}

	@Override
	public ObjectId toObjectId() {
		return new ObjectId(this);
	}
}
