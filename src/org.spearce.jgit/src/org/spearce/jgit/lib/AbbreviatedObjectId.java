/*
 * Copyright (C) 2008, Google Inc.
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
 * A prefix abbreviation of an {@link ObjectId}.
 * <p>
 * Sometimes Git produces abbreviated SHA-1 strings, using sufficient leading
 * digits from the ObjectId name to still be unique within the repository the
 * string was generated from. These ids are likely to be unique for a useful
 * period of time, especially if they contain at least 6-10 hex digits.
 * <p>
 * This class converts the hex string into a binary form, to make it more
 * efficient for matching against an object.
 */
public final class AbbreviatedObjectId {
	/**
	 * Convert an AbbreviatedObjectId from hex characters (US-ASCII).
	 *
	 * @param buf
	 *            the US-ASCII buffer to read from.
	 * @param offset
	 *            position to read the first character from.
	 * @param end
	 *            one past the last position to read (<code>end-offset</code> is
	 *            the length of the string).
	 * @return the converted object id.
	 */
	public static final AbbreviatedObjectId fromString(final byte[] buf,
			final int offset, final int end) {
		if (end - offset > AnyObjectId.STR_LEN)
			throw new IllegalArgumentException("Invalid id");
		return fromHexString(buf, offset, end);
	}

	/**
	 * Convert an AbbreviatedObjectId from hex characters.
	 *
	 * @param str
	 *            the string to read from. Must be &lt;= 40 characters.
	 * @return the converted object id.
	 */
	public static final AbbreviatedObjectId fromString(final String str) {
		if (str.length() > AnyObjectId.STR_LEN)
			throw new IllegalArgumentException("Invalid id: " + str);
		final byte[] b = Constants.encodeASCII(str);
		return fromHexString(b, 0, b.length);
	}

	private static final AbbreviatedObjectId fromHexString(final byte[] bs,
			int ptr, final int end) {
		try {
			final int a = hexUInt32(bs, ptr, end);
			final int b = hexUInt32(bs, ptr + 8, end);
			final int c = hexUInt32(bs, ptr + 16, end);
			final int d = hexUInt32(bs, ptr + 24, end);
			final int e = hexUInt32(bs, ptr + 32, end);
			return new AbbreviatedObjectId(end - ptr, a, b, c, d, e);
		} catch (ArrayIndexOutOfBoundsException e1) {
			try {
				final String str = new String(bs, ptr, end - ptr, "US-ASCII");
				throw new IllegalArgumentException("Invalid id: " + str);
			} catch (UnsupportedEncodingException e2) {
				throw new IllegalArgumentException("Invalid id");
			}
		}
	}

	private static final int hexUInt32(final byte[] bs, int p, final int end) {
		if (8 <= end - p)
			return AnyObjectId.hexUInt32(bs, p);

		int r = 0, n = 0;
		while (n < 8 && p < end) {
			final int v = AnyObjectId.fromhex[bs[p++]];
			if (v < 0)
				throw new ArrayIndexOutOfBoundsException();
			r <<= 4;
			r |= v;
			n++;
		}
		return r << (8 - n) * 4;
	}

	static int mask(final int nibbles, final int word, final int v) {
		final int b = (word - 1) * 8;
		if (b + 8 <= nibbles) {
			// We have all of the bits required for this word.
			//
			return v;
		}

		if (nibbles < b) {
			// We have none of the bits required for this word.
			//
			return 0;
		}

		final int s = 32 - (nibbles - b) * 4;
		return (v >>> s) << s;
	}

	/** Number of half-bytes used by this id. */
	final int nibbles;

	final int w1;

	final int w2;

	final int w3;

	final int w4;

	final int w5;

	AbbreviatedObjectId(final int n, final int new_1, final int new_2,
			final int new_3, final int new_4, final int new_5) {
		nibbles = n;
		w1 = new_1;
		w2 = new_2;
		w3 = new_3;
		w4 = new_4;
		w5 = new_5;
	}

	/** @return number of hex digits appearing in this id */
	public int length() {
		return nibbles;
	}

	/** @return true if this ObjectId is actually a complete id. */
	public boolean isComplete() {
		return length() == AnyObjectId.RAW_LEN * 2;
	}

	/** @return a complete ObjectId; null if {@link #isComplete()} is false */
	public ObjectId toObjectId() {
		return isComplete() ? new ObjectId(w1, w2, w3, w4, w5) : null;
	}

	/**
	 * Compares this abbreviation to a full object id.
	 *
	 * @param other
	 *            the other object id.
	 * @return &lt;0 if this abbreviation names an object that is less than
	 *         <code>other</code>; 0 if this abbreviation exactly matches the
	 *         first {@link #length()} digits of <code>other.name()</code>;
	 *         &gt;0 if this abbreviation names an object that is after
	 *         <code>other</code>.
	 */
	public int prefixCompare(final AnyObjectId other) {
		int cmp;

		cmp = NB.compareUInt32(w1, mask(1, other.w1));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w2, mask(2, other.w2));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w3, mask(3, other.w3));
		if (cmp != 0)
			return cmp;

		cmp = NB.compareUInt32(w4, mask(4, other.w4));
		if (cmp != 0)
			return cmp;

		return NB.compareUInt32(w5, mask(5, other.w5));
	}

	private int mask(final int word, final int v) {
		return mask(nibbles, word, v);
	}

	@Override
	public int hashCode() {
		return w2;
	}

	@Override
	public boolean equals(final Object o) {
		if (o instanceof AbbreviatedObjectId) {
			final AbbreviatedObjectId b = (AbbreviatedObjectId) o;
			return nibbles == b.nibbles && w1 == b.w1 && w2 == b.w2
					&& w3 == b.w3 && w4 == b.w4 && w5 == b.w5;
		}
		return false;
	}

	/**
	 * @return string form of the abbreviation, in lower case hexadecimal.
	 */
	public final String name() {
		final char[] b = new char[AnyObjectId.STR_LEN];

		AnyObjectId.formatHexChar(b, 0, w1);
		if (nibbles <= 8)
			return new String(b, 0, nibbles);

		AnyObjectId.formatHexChar(b, 8, w2);
		if (nibbles <= 16)
			return new String(b, 0, nibbles);

		AnyObjectId.formatHexChar(b, 16, w3);
		if (nibbles <= 24)
			return new String(b, 0, nibbles);

		AnyObjectId.formatHexChar(b, 24, w4);
		if (nibbles <= 32)
			return new String(b, 0, nibbles);

		AnyObjectId.formatHexChar(b, 32, w5);
		return new String(b, 0, nibbles);
	}

	@Override
	public String toString() {
		return "AbbreviatedObjectId[" + name() + "]";
	}
}
