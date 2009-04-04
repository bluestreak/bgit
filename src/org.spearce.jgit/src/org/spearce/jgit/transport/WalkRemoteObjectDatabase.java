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

package org.spearce.jgit.transport;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.spearce.jgit.errors.TransportException;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ProgressMonitor;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.util.NB;

/**
 * Transfers object data through a dumb transport.
 * <p>
 * Implementations are responsible for resolving path names relative to the
 * <code>objects/</code> subdirectory of a single remote Git repository or
 * naked object database and make the content available as a Java input stream
 * for reading during fetch. The actual object traversal logic to determine the
 * names of files to retrieve is handled through the generic, protocol
 * independent {@link WalkFetchConnection}.
 */
abstract class WalkRemoteObjectDatabase {
	static final String ROOT_DIR = "../";

	static final String INFO_PACKS = "info/packs";

	static final String INFO_ALTERNATES = "info/alternates";

	static final String INFO_HTTP_ALTERNATES = "info/http-alternates";

	static final String INFO_REFS = ROOT_DIR + Constants.INFO_REFS;

	abstract URIish getURI();

	/**
	 * Obtain the list of available packs (if any).
	 * <p>
	 * Pack names should be the file name in the packs directory, that is
	 * <code>pack-035760ab452d6eebd123add421f253ce7682355a.pack</code>. Index
	 * names should not be included in the returned collection.
	 * 
	 * @return list of pack names; null or empty list if none are available.
	 * @throws IOException
	 *             The connection is unable to read the remote repository's list
	 *             of available pack files.
	 */
	abstract Collection<String> getPackNames() throws IOException;

	/**
	 * Obtain alternate connections to alternate object databases (if any).
	 * <p>
	 * Alternates are typically read from the file {@link #INFO_ALTERNATES} or
	 * {@link #INFO_HTTP_ALTERNATES}. The content of each line must be resolved
	 * by the implementation and a new database reference should be returned to
	 * represent the additional location.
	 * <p>
	 * Alternates may reuse the same network connection handle, however the
	 * fetch connection will {@link #close()} each created alternate.
	 * 
	 * @return list of additional object databases the caller could fetch from;
	 *         null or empty list if none are configured.
	 * @throws IOException
	 *             The connection is unable to read the remote repository's list
	 *             of configured alternates.
	 */
	abstract Collection<WalkRemoteObjectDatabase> getAlternates()
			throws IOException;

	/**
	 * Open a single file for reading.
	 * <p>
	 * Implementors should make every attempt possible to ensure
	 * {@link FileNotFoundException} is used when the remote object does not
	 * exist. However when fetching over HTTP some misconfigured servers may
	 * generate a 200 OK status message (rather than a 404 Not Found) with an
	 * HTML formatted message explaining the requested resource does not exist.
	 * Callers such as {@link WalkFetchConnection} are prepared to handle this
	 * by validating the content received, and assuming content that fails to
	 * match its hash is an incorrectly phrased FileNotFoundException.
	 * 
	 * @param path
	 *            location of the file to read, relative to this objects
	 *            directory (e.g.
	 *            <code>cb/95df6ab7ae9e57571511ef451cf33767c26dd2</code> or
	 *            <code>pack/pack-035760ab452d6eebd123add421f253ce7682355a.pack</code>).
	 * @return a stream to read from the file. Never null.
	 * @throws FileNotFoundException
	 *             the requested file does not exist at the given location.
	 * @throws IOException
	 *             The connection is unable to read the remote's file, and the
	 *             failure occurred prior to being able to determine if the file
	 *             exists, or after it was determined to exist but before the
	 *             stream could be created.
	 */
	abstract FileStream open(String path) throws FileNotFoundException,
			IOException;

	/**
	 * Create a new connection for a discovered alternate object database
	 * <p>
	 * This method is typically called by {@link #readAlternates(String)} when
	 * subclasses us the generic alternate parsing logic for their
	 * implementation of {@link #getAlternates()}.
	 * 
	 * @param location
	 *            the location of the new alternate, relative to the current
	 *            object database.
	 * @return a new database connection that can read from the specified
	 *         alternate.
	 * @throws IOException
	 *             The database connection cannot be established with the
	 *             alternate, such as if the alternate location does not
	 *             actually exist and the connection's constructor attempts to
	 *             verify that.
	 */
	abstract WalkRemoteObjectDatabase openAlternate(String location)
			throws IOException;

	/**
	 * Close any resources used by this connection.
	 * <p>
	 * If the remote repository is contacted by a network socket this method
	 * must close that network socket, disconnecting the two peers. If the
	 * remote repository is actually local (same system) this method must close
	 * any open file handles used to read the "remote" repository.
	 */
	abstract void close();

	/**
	 * Delete a file from the object database.
	 * <p>
	 * Path may start with <code>../</code> to request deletion of a file that
	 * resides in the repository itself.
	 * <p>
	 * When possible empty directories must be removed, up to but not including
	 * the current object database directory itself.
	 * <p>
	 * This method does not support deletion of directories.
	 *
	 * @param path
	 *            name of the item to be removed, relative to the current object
	 *            database.
	 * @throws IOException
	 *             deletion is not supported, or deletion failed.
	 */
	void deleteFile(final String path) throws IOException {
		throw new IOException("Deleting '" + path + "' not supported.");
	}

	/**
	 * Open a remote file for writing.
	 * <p>
	 * Path may start with <code>../</code> to request writing of a file that
	 * resides in the repository itself.
	 * <p>
	 * The requested path may or may not exist. If the path already exists as a
	 * file the file should be truncated and completely replaced.
	 * <p>
	 * This method creates any missing parent directories, if necessary.
	 *
	 * @param path
	 *            name of the file to write, relative to the current object
	 *            database.
	 * @return stream to write into this file. Caller must close the stream to
	 *         complete the write request. The stream is not buffered and each
	 *         write may cause a network request/response so callers should
	 *         buffer to smooth out small writes.
	 * @param monitor
	 *            (optional) progress monitor to post write completion to during
	 *            the stream's close method.
	 * @param monitorTask
	 *            (optional) task name to display during the close method.
	 * @throws IOException
	 *             writing is not supported, or attempting to write the file
	 *             failed, possibly due to permissions or remote disk full, etc.
	 */
	OutputStream writeFile(final String path, final ProgressMonitor monitor,
			final String monitorTask) throws IOException {
		throw new IOException("Writing of '" + path + "' not supported.");
	}

	/**
	 * Atomically write a remote file.
	 * <p>
	 * This method attempts to perform as atomic of an update as it can,
	 * reducing (or eliminating) the time that clients might be able to see
	 * partial file content. This method is not suitable for very large
	 * transfers as the complete content must be passed as an argument.
	 * <p>
	 * Path may start with <code>../</code> to request writing of a file that
	 * resides in the repository itself.
	 * <p>
	 * The requested path may or may not exist. If the path already exists as a
	 * file the file should be truncated and completely replaced.
	 * <p>
	 * This method creates any missing parent directories, if necessary.
	 *
	 * @param path
	 *            name of the file to write, relative to the current object
	 *            database.
	 * @param data
	 *            complete new content of the file.
	 * @throws IOException
	 *             writing is not supported, or attempting to write the file
	 *             failed, possibly due to permissions or remote disk full, etc.
	 */
	void writeFile(final String path, final byte[] data) throws IOException {
		final OutputStream os = writeFile(path, null, null);
		try {
			os.write(data);
		} finally {
			os.close();
		}
	}

	/**
	 * Delete a loose ref from the remote repository.
	 *
	 * @param name
	 *            name of the ref within the ref space, for example
	 *            <code>refs/heads/pu</code>.
	 * @throws IOException
	 *             deletion is not supported, or deletion failed.
	 */
	void deleteRef(final String name) throws IOException {
		deleteFile(ROOT_DIR + name);
	}

	/**
	 * Delete a reflog from the remote repository.
	 *
	 * @param name
	 *            name of the ref within the ref space, for example
	 *            <code>refs/heads/pu</code>.
	 * @throws IOException
	 *             deletion is not supported, or deletion failed.
	 */
	void deleteRefLog(final String name) throws IOException {
		deleteFile(ROOT_DIR + Constants.LOGS + "/" + name);
	}

	/**
	 * Overwrite (or create) a loose ref in the remote repository.
	 * <p>
	 * This method creates any missing parent directories, if necessary.
	 *
	 * @param name
	 *            name of the ref within the ref space, for example
	 *            <code>refs/heads/pu</code>.
	 * @param value
	 *            new value to store in this ref. Must not be null.
	 * @throws IOException
	 *             writing is not supported, or attempting to write the file
	 *             failed, possibly due to permissions or remote disk full, etc.
	 */
	void writeRef(final String name, final ObjectId value) throws IOException {
		final ByteArrayOutputStream b;

		b = new ByteArrayOutputStream(Constants.OBJECT_ID_LENGTH * 2 + 1);
		value.copyTo(b);
		b.write('\n');

		writeFile(ROOT_DIR + name, b.toByteArray());
	}

	/**
	 * Rebuild the {@link #INFO_PACKS} for dumb transport clients.
	 * <p>
	 * This method rebuilds the contents of the {@link #INFO_PACKS} file to
	 * match the passed list of pack names.
	 *
	 * @param packNames
	 *            names of available pack files, in the order they should appear
	 *            in the file. Valid pack name strings are of the form
	 *            <code>pack-035760ab452d6eebd123add421f253ce7682355a.pack</code>.
	 * @throws IOException
	 *             writing is not supported, or attempting to write the file
	 *             failed, possibly due to permissions or remote disk full, etc.
	 */
	void writeInfoPacks(final Collection<String> packNames) throws IOException {
		final StringBuilder w = new StringBuilder();
		for (final String n : packNames) {
			w.append("P ");
			w.append(n);
			w.append('\n');
		}
		writeFile(INFO_PACKS, Constants.encodeASCII(w.toString()));
	}

	/**
	 * Open a buffered reader around a file.
	 * <p>
	 * This is shorthand for calling {@link #open(String)} and then wrapping it
	 * in a reader suitable for line oriented files like the alternates list.
	 * 
	 * @return a stream to read from the file. Never null.
	 * @param path
	 *            location of the file to read, relative to this objects
	 *            directory (e.g. <code>info/packs</code>).
	 * @throws FileNotFoundException
	 *             the requested file does not exist at the given location.
	 * @throws IOException
	 *             The connection is unable to read the remote's file, and the
	 *             failure occurred prior to being able to determine if the file
	 *             exists, or after it was determined to exist but before the
	 *             stream could be created.
	 */
	BufferedReader openReader(final String path) throws IOException {
		final InputStream is = open(path).in;
		return new BufferedReader(new InputStreamReader(is, Constants.CHARSET));
	}

	/**
	 * Read a standard Git alternates file to discover other object databases.
	 * <p>
	 * This method is suitable for reading the standard formats of the
	 * alternates file, such as found in <code>objects/info/alternates</code>
	 * or <code>objects/info/http-alternates</code> within a Git repository.
	 * <p>
	 * Alternates appear one per line, with paths expressed relative to this
	 * object database.
	 * 
	 * @param listPath
	 *            location of the alternate file to read, relative to this
	 *            object database (e.g. <code>info/alternates</code>).
	 * @return the list of discovered alternates. Empty list if the file exists,
	 *         but no entries were discovered.
	 * @throws FileNotFoundException
	 *             the requested file does not exist at the given location.
	 * @throws IOException
	 *             The connection is unable to read the remote's file, and the
	 *             failure occurred prior to being able to determine if the file
	 *             exists, or after it was determined to exist but before the
	 *             stream could be created.
	 */
	Collection<WalkRemoteObjectDatabase> readAlternates(final String listPath)
			throws IOException {
		final BufferedReader br = openReader(listPath);
		try {
			final Collection<WalkRemoteObjectDatabase> alts = new ArrayList<WalkRemoteObjectDatabase>();
			for (;;) {
				String line = br.readLine();
				if (line == null)
					break;
				if (!line.endsWith("/"))
					line += "/";
				alts.add(openAlternate(line));
			}
			return alts;
		} finally {
			br.close();
		}
	}

	/**
	 * Read a standard Git packed-refs file to discover known references.
	 *
	 * @param avail
	 *            return collection of references. Any existing entries will be
	 *            replaced if they are found in the packed-refs file.
	 * @throws TransportException
	 *             an error occurred reading from the packed refs file.
	 */
	protected void readPackedRefs(final Map<String, Ref> avail)
			throws TransportException {
		try {
			final BufferedReader br = openReader(ROOT_DIR
					+ Constants.PACKED_REFS);
			try {
				readPackedRefsImpl(avail, br);
			} finally {
				br.close();
			}
		} catch (FileNotFoundException notPacked) {
			// Perhaps it wasn't worthwhile, or is just an older repository.
		} catch (IOException e) {
			throw new TransportException(getURI(), "error in packed-refs", e);
		}
	}

	private void readPackedRefsImpl(final Map<String, Ref> avail,
			final BufferedReader br) throws IOException {
		Ref last = null;
		for (;;) {
			String line = br.readLine();
			if (line == null)
				break;
			if (line.charAt(0) == '#')
				continue;
			if (line.charAt(0) == '^') {
				if (last == null)
					throw new TransportException("Peeled line before ref.");
				final ObjectId id = ObjectId.fromString(line.substring(1));
				last = new Ref(Ref.Storage.PACKED, last.getName(), last
						.getObjectId(), id, true);
				avail.put(last.getName(), last);
				continue;
			}

			final int sp = line.indexOf(' ');
			if (sp < 0)
				throw new TransportException("Unrecognized ref: " + line);
			final ObjectId id = ObjectId.fromString(line.substring(0, sp));
			final String name = line.substring(sp + 1);
			last = new Ref(Ref.Storage.PACKED, name, id);
			avail.put(last.getName(), last);
		}
	}

	static final class FileStream {
		final InputStream in;

		final long length;

		/**
		 * Create a new stream of unknown length.
		 * 
		 * @param i
		 *            stream containing the file data. This stream will be
		 *            closed by the caller when reading is complete.
		 */
		FileStream(final InputStream i) {
			in = i;
			length = -1;
		}

		/**
		 * Create a new stream of known length.
		 * 
		 * @param i
		 *            stream containing the file data. This stream will be
		 *            closed by the caller when reading is complete.
		 * @param n
		 *            total number of bytes available for reading through
		 *            <code>i</code>.
		 */
		FileStream(final InputStream i, final long n) {
			in = i;
			length = n;
		}

		byte[] toArray() throws IOException {
			try {
				if (length >= 0) {
					final byte[] r = new byte[(int) length];
					NB.readFully(in, r, 0, r.length);
					return r;
				}

				final ByteArrayOutputStream r = new ByteArrayOutputStream();
				final byte[] buf = new byte[2048];
				int n;
				while ((n = in.read(buf)) >= 0)
					r.write(buf, 0, n);
				return r.toByteArray();
			} finally {
				in.close();
			}
		}
	}
}
