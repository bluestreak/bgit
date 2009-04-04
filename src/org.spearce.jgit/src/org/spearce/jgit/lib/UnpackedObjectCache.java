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

import java.lang.ref.SoftReference;

class UnpackedObjectCache {
	private static final int CACHE_SZ = 1024;

	private static final SoftReference<Entry> DEAD;

	private static int hash(final long position) {
		return (((int) position) << 22) >>> 22;
	}

	private static int maxByteCount;

	private static final Slot[] cache;

	private static Slot lruHead;

	private static Slot lruTail;

	private static int openByteCount;

	static {
		DEAD = new SoftReference<Entry>(null);
		maxByteCount = new WindowCacheConfig().getDeltaBaseCacheLimit();

		cache = new Slot[CACHE_SZ];
		for (int i = 0; i < CACHE_SZ; i++)
			cache[i] = new Slot();
	}

	static synchronized void reconfigure(final WindowCacheConfig cfg) {
		final int dbLimit = cfg.getDeltaBaseCacheLimit();
		if (maxByteCount != dbLimit) {
			maxByteCount = dbLimit;
			releaseMemory();
		}
	}

	static synchronized Entry get(final WindowedFile pack, final long position) {
		final Slot e = cache[hash(position)];
		if (e.provider == pack && e.position == position) {
			final Entry buf = e.data.get();
			if (buf != null) {
				moveToHead(e);
				return buf;
			}
		}
		return null;
	}

	static synchronized void store(final WindowedFile pack,
			final long position, final byte[] data, final int objectType) {
		if (data.length > maxByteCount)
			return; // Too large to cache.

		final Slot e = cache[hash(position)];
		clearEntry(e);

		openByteCount += data.length;
		releaseMemory();

		e.provider = pack;
		e.position = position;
		e.sz = data.length;
		e.data = new SoftReference<Entry>(new Entry(data, objectType));
		moveToHead(e);
	}

	private static void releaseMemory() {
		while (openByteCount > maxByteCount && lruTail != null) {
			final Slot currOldest = lruTail;
			final Slot nextOldest = currOldest.lruPrev;

			clearEntry(currOldest);
			currOldest.lruPrev = null;
			currOldest.lruNext = null;

			if (nextOldest == null)
				lruHead = null;
			else
				nextOldest.lruNext = null;
			lruTail = nextOldest;
		}
	}

	static synchronized void purge(final WindowedFile file) {
		for (final Slot e : cache) {
			if (e.provider == file) {
				clearEntry(e);
				unlink(e);
			}
		}
	}

	private static void moveToHead(final Slot e) {
		unlink(e);
		e.lruPrev = null;
		e.lruNext = lruHead;
		if (lruHead != null)
			lruHead.lruPrev = e;
		else
			lruTail = e;
		lruHead = e;
	}

	private static void unlink(final Slot e) {
		final Slot prev = e.lruPrev;
		final Slot next = e.lruNext;
		if (prev != null)
			prev.lruNext = next;
		if (next != null)
			next.lruPrev = prev;
	}

	private static void clearEntry(final Slot e) {
		openByteCount -= e.sz;
		e.provider = null;
		e.data = DEAD;
		e.sz = 0;
	}

	private UnpackedObjectCache() {
		throw new UnsupportedOperationException();
	}

	static class Entry {
		final byte[] data;

		final int type;

		Entry(final byte[] aData, final int aType) {
			data = aData;
			type = aType;
		}
	}

	private static class Slot {
		Slot lruPrev;

		Slot lruNext;

		WindowedFile provider;

		long position;

		int sz;

		SoftReference<Entry> data = DEAD;
	}
}
