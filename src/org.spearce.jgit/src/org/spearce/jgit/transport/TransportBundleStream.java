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

package org.spearce.jgit.transport;

import java.io.IOException;
import java.io.InputStream;

import org.spearce.jgit.errors.TransportException;
import org.spearce.jgit.lib.Repository;

/**
 * Single shot fetch from a streamed Git bundle.
 * <p>
 * The bundle is read from an unbuffered input stream, which limits the
 * transport to opening at most one FetchConnection before needing to recreate
 * the transport instance.
 */
public class TransportBundleStream extends TransportBundle {
	private InputStream src;

	/**
	 * Create a new transport to fetch objects from a streamed bundle.
	 * <p>
	 * The stream can be unbuffered (buffering is automatically provided
	 * internally to smooth out short reads) and unpositionable (the stream is
	 * read from only once, sequentially).
	 * <p>
	 * When the FetchConnection or the this instance is closed the supplied
	 * input stream is also automatically closed. This frees callers from
	 * needing to keep track of the supplied stream.
	 *
	 * @param db
	 *            repository the fetched objects will be loaded into.
	 * @param uri
	 *            symbolic name of the source of the stream. The URI can
	 *            reference a non-existent resource. It is used only for
	 *            exception reporting.
	 * @param in
	 *            the stream to read the bundle from.
	 */
	public TransportBundleStream(final Repository db, final URIish uri,
			final InputStream in) {
		super(db, uri);
		src = in;
	}

	@Override
	public FetchConnection openFetch() throws TransportException {
		if (src == null)
			throw new TransportException(uri, "Only one fetch supported");
		try {
			return new BundleFetchConnection(src);
		} finally {
			src = null;
		}
	}

	@Override
	public void close() {
		if (src != null) {
			try {
				src.close();
			} catch (IOException err) {
				// Ignore a close error.
			} finally {
				src = null;
			}
		}
	}
}
