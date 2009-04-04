/*
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2007, Shawn O. Pearce <spearce@spearce.org>
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

/**
 * A representation of a file (blob) object in a {@link Tree}.
 */
public class FileTreeEntry extends TreeEntry {
	private FileMode mode;

	/**
	 * Constructor for a File (blob) object.
	 *
	 * @param parent
	 *            The {@link Tree} holding this object (or null)
	 * @param id
	 *            the SHA-1 of the blob (or null for a yet unhashed file)
	 * @param nameUTF8
	 *            raw object name in the parent tree
	 * @param execute
	 *            true if the executable flag is set
	 */
	public FileTreeEntry(final Tree parent, final ObjectId id,
			final byte[] nameUTF8, final boolean execute) {
		super(parent, id, nameUTF8);
		setExecutable(execute);
	}

	public FileMode getMode() {
		return mode;
	}

	/**
	 * @return true if this file is executable
	 */
	public boolean isExecutable() {
		return getMode().equals(FileMode.EXECUTABLE_FILE);
	}

	/**
	 * @param execute set/reset the executable flag
	 */
	public void setExecutable(final boolean execute) {
		mode = execute ? FileMode.EXECUTABLE_FILE : FileMode.REGULAR_FILE;
	}

	/**
	 * @return an {@link ObjectLoader} that will return the data
	 * @throws IOException
	 */
	public ObjectLoader openReader() throws IOException {
		return getRepository().openBlob(getId());
	}

	public void accept(final TreeVisitor tv, final int flags)
			throws IOException {
		if ((MODIFIED_ONLY & flags) == MODIFIED_ONLY && !isModified()) {
			return;
		}

		tv.visitFile(this);
	}

	public String toString() {
		final StringBuffer r = new StringBuffer();
		r.append(ObjectId.toString(getId()));
		r.append(' ');
		r.append(isExecutable() ? 'X' : 'F');
		r.append(' ');
		r.append(getFullName());
		return r.toString();
	}
}
