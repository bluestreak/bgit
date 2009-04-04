/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Charles O'Farrell <charleso@charleso.org>
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
import java.io.StringWriter;
import java.util.Collection;

/**
 * Writes out refs to the {@link Constants#INFO_REFS} and
 * {@link Constants#PACKED_REFS} files.
 * 
 * This class is abstract as the writing of the files must be handled by the
 * caller. This is because it is used by transport classes as well.
 */
public abstract class RefWriter {

	private final Collection<Ref> refs;

	/**
	 * @param refs
	 *            the complete set of references. This should have been computed
	 *            by applying updates to the advertised refs already discovered.
	 */
	public RefWriter(Collection<Ref> refs) {
		this.refs = RefComparator.sort(refs);
	}

	/**
	 * Rebuild the {@link Constants#INFO_REFS}.
	 * <p>
	 * This method rebuilds the contents of the {@link Constants#INFO_REFS} file
	 * to match the passed list of references.
	 * 
	 * 
	 * @throws IOException
	 *             writing is not supported, or attempting to write the file
	 *             failed, possibly due to permissions or remote disk full, etc.
	 */
	public void writeInfoRefs() throws IOException {
		final StringWriter w = new StringWriter();
		final char[] tmp = new char[Constants.OBJECT_ID_LENGTH * 2];
		for (final Ref r : refs) {
			if (Constants.HEAD.equals(r.getName())) {
				// Historically HEAD has never been published through
				// the INFO_REFS file. This is a mistake, but its the
				// way things are.
				//
				continue;
			}

			r.getObjectId().copyTo(tmp, w);
			w.write('\t');
			w.write(r.getName());
			w.write('\n');

			if (r.getPeeledObjectId() != null) {
				r.getPeeledObjectId().copyTo(tmp, w);
				w.write('\t');
				w.write(r.getName());
				w.write("^{}\n");
			}
		}
		writeFile(Constants.INFO_REFS, Constants.encodeASCII(w.toString()));
	}

	/**
	 * Rebuild the {@link Constants#PACKED_REFS} file.
	 * <p>
	 * This method rebuilds the contents of the {@link Constants#PACKED_REFS}
	 * file to match the passed list of references, including only those refs
	 * that have a storage type of {@link Ref.Storage#PACKED}.
	 * 
	 * @throws IOException
	 *             writing is not supported, or attempting to write the file
	 *             failed, possibly due to permissions or remote disk full, etc.
	 */
	public void writePackedRefs() throws IOException {
		boolean peeled = false;

		for (final Ref r : refs) {
			if (r.getStorage() != Ref.Storage.PACKED)
				continue;
			if (r.getPeeledObjectId() != null)
				peeled = true;
		}

		final StringWriter w = new StringWriter();
		if (peeled) {
			w.write("# pack-refs with:");
			if (peeled)
				w.write(" peeled");
			w.write('\n');
		}

		final char[] tmp = new char[Constants.OBJECT_ID_LENGTH * 2];
		for (final Ref r : refs) {
			if (r.getStorage() != Ref.Storage.PACKED)
				continue;

			r.getObjectId().copyTo(tmp, w);
			w.write(' ');
			w.write(r.getName());
			w.write('\n');

			if (r.getPeeledObjectId() != null) {
				w.write('^');
				r.getPeeledObjectId().copyTo(tmp, w);
				w.write('\n');
			}
		}
		writeFile(Constants.PACKED_REFS, Constants.encodeASCII(w.toString()));
	}

	/**
	 * Handles actual writing of ref files to the git repository, which may
	 * differ slightly depending on the destination and transport.
	 * 
	 * @param file
	 *            path to ref file.
	 * @param content
	 *            byte content of file to be written.
	 * @throws IOException
	 */
	protected abstract void writeFile(String file, byte[] content)
			throws IOException;
}
