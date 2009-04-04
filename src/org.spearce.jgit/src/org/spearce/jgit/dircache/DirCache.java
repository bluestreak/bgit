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

package org.spearce.jgit.dircache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Comparator;

import org.spearce.jgit.errors.CorruptObjectException;
import org.spearce.jgit.errors.UnmergedPathException;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.LockFile;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ObjectWriter;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.util.MutableInteger;
import org.spearce.jgit.util.NB;
import org.spearce.jgit.util.TemporaryBuffer;

/**
 * Support for the Git dircache (aka index file).
 * <p>
 * The index file keeps track of which objects are currently checked out in the
 * working directory, and the last modified time of those working files. Changes
 * in the working directory can be detected by comparing the modification times
 * to the cached modification time within the index file.
 * <p>
 * Index files are also used during merges, where the merge happens within the
 * index file first, and the working directory is updated as a post-merge step.
 * Conflicts are stored in the index file to allow tool (and human) based
 * resolutions to be easily performed.
 */
public class DirCache {
	private static final byte[] SIG_DIRC = { 'D', 'I', 'R', 'C' };

	private static final int EXT_TREE = 0x54524545 /* 'TREE' */;

	private static final int INFO_LEN = DirCacheEntry.INFO_LEN;

	private static final DirCacheEntry[] NO_ENTRIES = {};

	static final Comparator<DirCacheEntry> ENT_CMP = new Comparator<DirCacheEntry>() {
		public int compare(final DirCacheEntry o1, final DirCacheEntry o2) {
			final int cr = cmp(o1, o2);
			if (cr != 0)
				return cr;
			return o1.getStage() - o2.getStage();
		}
	};

	static int cmp(final DirCacheEntry a, final DirCacheEntry b) {
		return cmp(a.path, a.path.length, b);
	}

	static int cmp(final byte[] aPath, final int aLen, final DirCacheEntry b) {
		return cmp(aPath, aLen, b.path, b.path.length);
	}

	static int cmp(final byte[] aPath, final int aLen, final byte[] bPath,
			final int bLen) {
		for (int cPos = 0; cPos < aLen && cPos < bLen; cPos++) {
			final int cmp = (aPath[cPos] & 0xff) - (bPath[cPos] & 0xff);
			if (cmp != 0)
				return cmp;
		}
		return aLen - bLen;
	}

	/**
	 * Create a new empty index which is never stored on disk.
	 *
	 * @return an empty cache which has no backing store file. The cache may not
	 *         be read or written, but it may be queried and updated (in
	 *         memory).
	 */
	public static DirCache newInCore() {
		return new DirCache(null);
	}

	/**
	 * Create a new in-core index representation and read an index from disk.
	 * <p>
	 * The new index will be read before it is returned to the caller. Read
	 * failures are reported as exceptions and therefore prevent the method from
	 * returning a partially populated index.
	 *
	 * @param indexLocation
	 *            location of the index file on disk.
	 * @return a cache representing the contents of the specified index file (if
	 *         it exists) or an empty cache if the file does not exist.
	 * @throws IOException
	 *             the index file is present but could not be read.
	 * @throws CorruptObjectException
	 *             the index file is using a format or extension that this
	 *             library does not support.
	 */
	public static DirCache read(final File indexLocation)
			throws CorruptObjectException, IOException {
		final DirCache c = new DirCache(indexLocation);
		c.read();
		return c;
	}

	/**
	 * Create a new in-core index representation and read an index from disk.
	 * <p>
	 * The new index will be read before it is returned to the caller. Read
	 * failures are reported as exceptions and therefore prevent the method from
	 * returning a partially populated index.
	 *
	 * @param db
	 *            repository the caller wants to read the default index of.
	 * @return a cache representing the contents of the specified index file (if
	 *         it exists) or an empty cache if the file does not exist.
	 * @throws IOException
	 *             the index file is present but could not be read.
	 * @throws CorruptObjectException
	 *             the index file is using a format or extension that this
	 *             library does not support.
	 */
	public static DirCache read(final Repository db)
			throws CorruptObjectException, IOException {
		return read(new File(db.getDirectory(), "index"));
	}

	/**
	 * Create a new in-core index representation, lock it, and read from disk.
	 * <p>
	 * The new index will be locked and then read before it is returned to the
	 * caller. Read failures are reported as exceptions and therefore prevent
	 * the method from returning a partially populated index.  On read failure,
	 * the lock is released.
	 *
	 * @param indexLocation
	 *            location of the index file on disk.
	 * @return a cache representing the contents of the specified index file (if
	 *         it exists) or an empty cache if the file does not exist.
	 * @throws IOException
	 *             the index file is present but could not be read, or the lock
	 *             could not be obtained.
	 * @throws CorruptObjectException
	 *             the index file is using a format or extension that this
	 *             library does not support.
	 */
	public static DirCache lock(final File indexLocation)
			throws CorruptObjectException, IOException {
		final DirCache c = new DirCache(indexLocation);
		if (!c.lock())
			throw new IOException("Cannot lock " + indexLocation);

		try {
			c.read();
		} catch (IOException e) {
			c.unlock();
			throw e;
		} catch (RuntimeException e) {
			c.unlock();
			throw e;
		} catch (Error e) {
			c.unlock();
			throw e;
		}

		return c;
	}

	/**
	 * Create a new in-core index representation, lock it, and read from disk.
	 * <p>
	 * The new index will be locked and then read before it is returned to the
	 * caller. Read failures are reported as exceptions and therefore prevent
	 * the method from returning a partially populated index.
	 *
	 * @param db
	 *            repository the caller wants to read the default index of.
	 * @return a cache representing the contents of the specified index file (if
	 *         it exists) or an empty cache if the file does not exist.
	 * @throws IOException
	 *             the index file is present but could not be read, or the lock
	 *             could not be obtained.
	 * @throws CorruptObjectException
	 *             the index file is using a format or extension that this
	 *             library does not support.
	 */
	public static DirCache lock(final Repository db)
			throws CorruptObjectException, IOException {
		return lock(new File(db.getDirectory(), "index"));
	}

	/** Location of the current version of the index file. */
	private final File liveFile;

	/** Modification time of the file at the last read/write we did. */
	private long lastModified;

	/** Individual file index entries, sorted by path name. */
	private DirCacheEntry[] sortedEntries;

	/** Number of positions within {@link #sortedEntries} that are valid. */
	private int entryCnt;

	/** Cache tree for this index; null if the cache tree is not available. */
	private DirCacheTree tree;

	/** Our active lock (if we hold it); null if we don't have it locked. */
	private LockFile myLock;

	/**
	 * Create a new in-core index representation.
	 * <p>
	 * The new index will be empty. Callers may wish to read from the on disk
	 * file first with {@link #read()}.
	 *
	 * @param indexLocation
	 *            location of the index file on disk.
	 */
	public DirCache(final File indexLocation) {
		liveFile = indexLocation;
		clear();
	}

	/**
	 * Create a new builder to update this cache.
	 * <p>
	 * Callers should add all entries to the builder, then use
	 * {@link DirCacheBuilder#finish()} to update this instance.
	 *
	 * @return a new builder instance for this cache.
	 */
	public DirCacheBuilder builder() {
		return new DirCacheBuilder(this, entryCnt + 16);
	}

	/**
	 * Create a new editor to recreate this cache.
	 * <p>
	 * Callers should add commands to the editor, then use
	 * {@link DirCacheEditor#finish()} to update this instance.
	 *
	 * @return a new builder instance for this cache.
	 */
	public DirCacheEditor editor() {
		return new DirCacheEditor(this, entryCnt + 16);
	}

	void replace(final DirCacheEntry[] e, final int cnt) {
		sortedEntries = e;
		entryCnt = cnt;
		tree = null;
	}

	/**
	 * Read the index from disk, if it has changed on disk.
	 * <p>
	 * This method tries to avoid loading the index if it has not changed since
	 * the last time we consulted it. A missing index file will be treated as
	 * though it were present but had no file entries in it.
	 *
	 * @throws IOException
	 *             the index file is present but could not be read. This
	 *             DirCache instance may not be populated correctly.
	 * @throws CorruptObjectException
	 *             the index file is using a format or extension that this
	 *             library does not support.
	 */
	public void read() throws IOException, CorruptObjectException {
		if (liveFile == null)
			throw new IOException("DirCache does not have a backing file");
		if (!liveFile.exists())
			clear();
		else if (liveFile.lastModified() != lastModified) {
			try {
				final FileInputStream inStream = new FileInputStream(liveFile);
				try {
					clear();
					readFrom(inStream);
				} finally {
					try {
						inStream.close();
					} catch (IOException err2) {
						// Ignore any close failures.
					}
				}
			} catch (FileNotFoundException fnfe) {
				// Someone must have deleted it between our exists test
				// and actually opening the path. That's fine, its empty.
				//
				clear();
			}
		}
	}

	/** Empty this index, removing all entries. */
	public void clear() {
		lastModified = 0;
		sortedEntries = NO_ENTRIES;
		entryCnt = 0;
		tree = null;
	}

	private void readFrom(final FileInputStream inStream) throws IOException,
			CorruptObjectException {
		final FileChannel fd = inStream.getChannel();
		final long sizeOnDisk = fd.size();
		final BufferedInputStream in = new BufferedInputStream(inStream);

		// Read the index header and verify we understand it.
		//
		final byte[] hdr = new byte[12];
		NB.readFully(in, hdr, 0, 12);
		if (!is_DIRC(hdr))
			throw new CorruptObjectException("Not a DIRC file.");
		final int ver = NB.decodeInt32(hdr, 4);
		if (ver != 2)
			throw new CorruptObjectException("Unknown DIRC version " + ver);
		entryCnt = NB.decodeInt32(hdr, 8);
		if (entryCnt < 0)
			throw new CorruptObjectException("DIRC has too many entries.");

		// Load the individual file entries.
		//
		final byte[] infos = new byte[INFO_LEN * entryCnt];
		sortedEntries = new DirCacheEntry[entryCnt];
		for (int i = 0; i < entryCnt; i++)
			sortedEntries[i] = new DirCacheEntry(infos, i * INFO_LEN, in);
		lastModified = liveFile.lastModified();

		// After the file entries are index extensions.
		//
		while (fd.position() - in.available() < sizeOnDisk - 20) {
			NB.readFully(in, hdr, 0, 8);
			switch (NB.decodeInt32(hdr, 0)) {
			case EXT_TREE: {
				final byte[] raw = new byte[NB.decodeInt32(hdr, 4)];
				NB.readFully(in, raw, 0, raw.length);
				tree = new DirCacheTree(raw, new MutableInteger(), null);
				break;
			}
			default:
				if (hdr[0] >= 'A' && hdr[0] <= 'Z') {
					// The extension is optional and is here only as
					// a performance optimization. Since we do not
					// understand it, we can safely skip past it.
					//
					NB.skipFully(in, NB.decodeUInt32(hdr, 4));
				} else {
					// The extension is not an optimization and is
					// _required_ to understand this index format.
					// Since we did not trap it above we must abort.
					//
					throw new CorruptObjectException("DIRC extension '"
							+ Constants.CHARSET.decode(
									ByteBuffer.wrap(hdr, 0, 4)).toString()
							+ "' not supported by this version.");
				}
			}
		}
	}

	private static boolean is_DIRC(final byte[] hdr) {
		if (hdr.length < SIG_DIRC.length)
			return false;
		for (int i = 0; i < SIG_DIRC.length; i++)
			if (hdr[i] != SIG_DIRC[i])
				return false;
		return true;
	}

	/**
	 * Try to establish an update lock on the cache file.
	 *
	 * @return true if the lock is now held by the caller; false if it is held
	 *         by someone else.
	 * @throws IOException
	 *             the output file could not be created. The caller does not
	 *             hold the lock.
	 */
	public boolean lock() throws IOException {
		if (liveFile == null)
			throw new IOException("DirCache does not have a backing file");
		final LockFile tmp = new LockFile(liveFile);
		if (tmp.lock()) {
			tmp.setNeedStatInformation(true);
			myLock = tmp;
			return true;
		}
		return false;
	}

	/**
	 * Write the entry records from memory to disk.
	 * <p>
	 * The cache must be locked first by calling {@link #lock()} and receiving
	 * true as the return value. Applications are encouraged to lock the index,
	 * then invoke {@link #read()} to ensure the in-memory data is current,
	 * prior to updating the in-memory entries.
	 * <p>
	 * Once written the lock is closed and must be either committed with
	 * {@link #commit()} or rolled back with {@link #unlock()}.
	 *
	 * @throws IOException
	 *             the output file could not be created. The caller no longer
	 *             holds the lock.
	 */
	public void write() throws IOException {
		final LockFile tmp = myLock;
		requireLocked(tmp);
		try {
			writeTo(new BufferedOutputStream(tmp.getOutputStream()));
		} catch (IOException err) {
			tmp.unlock();
			throw err;
		} catch (RuntimeException err) {
			tmp.unlock();
			throw err;
		} catch (Error err) {
			tmp.unlock();
			throw err;
		}
	}

	private void writeTo(final OutputStream os) throws IOException {
		final MessageDigest foot = Constants.newMessageDigest();
		final DigestOutputStream dos = new DigestOutputStream(os, foot);

		// Write the header.
		//
		final byte[] tmp = new byte[128];
		System.arraycopy(SIG_DIRC, 0, tmp, 0, SIG_DIRC.length);
		NB.encodeInt32(tmp, 4, /* version */2);
		NB.encodeInt32(tmp, 8, entryCnt);
		dos.write(tmp, 0, 12);

		// Write the individual file entries.
		//
		if (lastModified <= 0) {
			// Write a new index, as no entries require smudging.
			//
			for (int i = 0; i < entryCnt; i++)
				sortedEntries[i].write(dos);
		} else {
			final int smudge_s = (int) (lastModified / 1000);
			final int smudge_ns = ((int) (lastModified % 1000)) * 1000000;
			for (int i = 0; i < entryCnt; i++) {
				final DirCacheEntry e = sortedEntries[i];
				if (e.mightBeRacilyClean(smudge_s, smudge_ns))
					e.smudgeRacilyClean();
				e.write(dos);
			}
		}

		if (tree != null) {
			final TemporaryBuffer bb = new TemporaryBuffer();
			tree.write(tmp, bb);
			bb.close();

			NB.encodeInt32(tmp, 0, EXT_TREE);
			NB.encodeInt32(tmp, 4, (int) bb.length());
			dos.write(tmp, 0, 8);
			bb.writeTo(dos, null);
		}

		os.write(foot.digest());
		os.close();
	}

	/**
	 * Commit this change and release the lock.
	 * <p>
	 * If this method fails (returns false) the lock is still released.
	 *
	 * @return true if the commit was successful and the file contains the new
	 *         data; false if the commit failed and the file remains with the
	 *         old data.
	 * @throws IllegalStateException
	 *             the lock is not held.
	 */
	public boolean commit() {
		final LockFile tmp = myLock;
		requireLocked(tmp);
		myLock = null;
		if (!tmp.commit())
			return false;
		lastModified = tmp.getCommitLastModified();
		return true;
	}

	private void requireLocked(final LockFile tmp) {
		if (liveFile == null)
			throw new IllegalStateException("DirCache is not locked");
		if (tmp == null)
			throw new IllegalStateException("DirCache "
					+ liveFile.getAbsolutePath() + " not locked.");
	}

	/**
	 * Unlock this file and abort this change.
	 * <p>
	 * The temporary file (if created) is deleted before returning.
	 */
	public void unlock() {
		final LockFile tmp = myLock;
		if (tmp != null) {
			myLock = null;
			tmp.unlock();
		}
	}

	/**
	 * Locate the position a path's entry is at in the index.
	 * <p>
	 * If there is at least one entry in the index for this path the position of
	 * the lowest stage is returned. Subsequent stages can be identified by
	 * testing consecutive entries until the path differs.
	 * <p>
	 * If no path matches the entry -(position+1) is returned, where position is
	 * the location it would have gone within the index.
	 *
	 * @param path
	 *            the path to search for.
	 * @return if >= 0 then the return value is the position of the entry in the
	 *         index; pass to {@link #getEntry(int)} to obtain the entry
	 *         information. If < 0 the entry does not exist in the index.
	 */
	public int findEntry(final String path) {
		if (entryCnt == 0)
			return -1;
		final byte[] p = Constants.encode(path);
		return findEntry(p, p.length);
	}

	int findEntry(final byte[] p, final int pLen) {
		int low = 0;
		int high = entryCnt;
		do {
			int mid = (low + high) >> 1;
			final int cmp = cmp(p, pLen, sortedEntries[mid]);
			if (cmp < 0)
				high = mid;
			else if (cmp == 0) {
				while (mid > 0 && cmp(p, pLen, sortedEntries[mid - 1]) == 0)
					mid--;
				return mid;
			} else
				low = mid + 1;
		} while (low < high);
		return -(low + 1);
	}

	/**
	 * Determine the next index position past all entries with the same name.
	 * <p>
	 * As index entries are sorted by path name, then stage number, this method
	 * advances the supplied position to the first position in the index whose
	 * path name does not match the path name of the supplied position's entry.
	 *
	 * @param position
	 *            entry position of the path that should be skipped.
	 * @return position of the next entry whose path is after the input.
	 */
	public int nextEntry(final int position) {
		DirCacheEntry last = sortedEntries[position];
		int nextIdx = position + 1;
		while (nextIdx < entryCnt) {
			final DirCacheEntry next = sortedEntries[nextIdx];
			if (cmp(last, next) != 0)
				break;
			last = next;
			nextIdx++;
		}
		return nextIdx;
	}

	int nextEntry(final byte[] p, final int pLen, int nextIdx) {
		while (nextIdx < entryCnt) {
			final DirCacheEntry next = sortedEntries[nextIdx];
			if (!DirCacheTree.peq(p, next.path, pLen))
				break;
			nextIdx++;
		}
		return nextIdx;
	}

	/**
	 * Total number of file entries stored in the index.
	 * <p>
	 * This count includes unmerged stages for a file entry if the file is
	 * currently conflicted in a merge. This means the total number of entries
	 * in the index may be up to 3 times larger than the number of files in the
	 * working directory.
	 * <p>
	 * Note that this value counts only <i>files</i>.
	 *
	 * @return number of entries available.
	 * @see #getEntry(int)
	 */
	public int getEntryCount() {
		return entryCnt;
	}

	/**
	 * Get a specific entry.
	 *
	 * @param i
	 *            position of the entry to get.
	 * @return the entry at position <code>i</code>.
	 */
	public DirCacheEntry getEntry(final int i) {
		return sortedEntries[i];
	}

	/**
	 * Get a specific entry.
	 *
	 * @param path
	 *            the path to search for.
	 * @return the entry at position <code>i</code>.
	 */
	public DirCacheEntry getEntry(final String path) {
		final int i = findEntry(path);
		return i < 0 ? null : sortedEntries[i];
	}

	/**
	 * Recursively get all entries within a subtree.
	 *
	 * @param path
	 *            the subtree path to get all entries within.
	 * @return all entries recursively contained within the subtree.
	 */
	public DirCacheEntry[] getEntriesWithin(String path) {
		if (!path.endsWith("/"))
			path += "/";
		final byte[] p = Constants.encode(path);
		final int pLen = p.length;

		int eIdx = findEntry(p, pLen);
		if (eIdx < 0)
			eIdx = -(eIdx + 1);
		final int lastIdx = nextEntry(p, pLen, eIdx);
		final DirCacheEntry[] r = new DirCacheEntry[lastIdx - eIdx];
		System.arraycopy(sortedEntries, eIdx, r, 0, r.length);
		return r;
	}

	void toArray(final int i, final DirCacheEntry[] dst, final int off,
			final int cnt) {
		System.arraycopy(sortedEntries, i, dst, off, cnt);
	}

	/**
	 * Obtain (or build) the current cache tree structure.
	 * <p>
	 * This method can optionally recreate the cache tree, without flushing the
	 * tree objects themselves to disk.
	 *
	 * @param build
	 *            if true and the cache tree is not present in the index it will
	 *            be generated and returned to the caller.
	 * @return the cache tree; null if there is no current cache tree available
	 *         and <code>build</code> was false.
	 */
	public DirCacheTree getCacheTree(final boolean build) {
		if (build) {
			if (tree == null)
				tree = new DirCacheTree();
			tree.validate(sortedEntries, entryCnt, 0, 0);
		}
		return tree;
	}

	/**
	 * Write all index trees to the object store, returning the root tree.
	 *
	 * @param ow
	 *            the writer to use when serializing to the store.
	 * @return identity for the root tree.
	 * @throws UnmergedPathException
	 *             one or more paths contain higher-order stages (stage > 0),
	 *             which cannot be stored in a tree object.
	 * @throws IOException
	 *             an unexpected error occurred writing to the object store.
	 */
	public ObjectId writeTree(final ObjectWriter ow)
			throws UnmergedPathException, IOException {
		return getCacheTree(true).writeTree(sortedEntries, 0, 0, ow);
	}
}
