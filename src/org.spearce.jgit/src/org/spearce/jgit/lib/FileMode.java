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
import java.io.OutputStream;

/**
 * Constants describing various file modes recognized by GIT.
 * <p>
 * GIT uses a subset of the available UNIX file permission bits. The
 * <code>FileMode</code> class provides access to constants defining the modes
 * actually used by GIT.
 * </p>
 */
public abstract class FileMode {
	/** Mode indicating an entry is a {@link Tree}. */
	@SuppressWarnings("synthetic-access")
	public static final FileMode TREE = new FileMode(0040000,
			Constants.OBJ_TREE) {
		public boolean equals(final int modeBits) {
			return (modeBits & 0170000) == 0040000;
		}
	};

	/** Mode indicating an entry is a {@link SymlinkTreeEntry}. */
	@SuppressWarnings("synthetic-access")
	public static final FileMode SYMLINK = new FileMode(0120000,
			Constants.OBJ_BLOB) {
		public boolean equals(final int modeBits) {
			return (modeBits & 0170000) == 0120000;
		}
	};

	/** Mode indicating an entry is a non-executable {@link FileTreeEntry}. */
	@SuppressWarnings("synthetic-access")
	public static final FileMode REGULAR_FILE = new FileMode(0100644,
			Constants.OBJ_BLOB) {
		public boolean equals(final int modeBits) {
			return (modeBits & 0170000) == 0100000 && (modeBits & 0111) == 0;
		}
	};

	/** Mode indicating an entry is an executable {@link FileTreeEntry}. */
	@SuppressWarnings("synthetic-access")
	public static final FileMode EXECUTABLE_FILE = new FileMode(0100755,
			Constants.OBJ_BLOB) {
		public boolean equals(final int modeBits) {
			return (modeBits & 0170000) == 0100000 && (modeBits & 0111) != 0;
		}
	};

	/** Mode indicating an entry is a submodule commit in another repository. */
	@SuppressWarnings("synthetic-access")
	public static final FileMode GITLINK = new FileMode(0160000,
			Constants.OBJ_COMMIT) {
		public boolean equals(final int modeBits) {
			return (modeBits & 0170000) == 0160000;
		}
	};

	/** Mode indicating an entry is missing during parallel walks. */
	@SuppressWarnings("synthetic-access")
	public static final FileMode MISSING = new FileMode(0000000,
			Constants.OBJ_BAD) {
		public boolean equals(final int modeBits) {
			return modeBits == 0;
		}
	};

	/**
	 * Convert a set of mode bits into a FileMode enumerated value.
	 * 
	 * @param bits
	 *            the mode bits the caller has somehow obtained.
	 * @return the FileMode instance that represents the given bits.
	 */
	public static final FileMode fromBits(final int bits) {
		switch (bits & 0170000) {
		case 0000000:
			if (bits == 0)
				return MISSING;
			break;
		case 0040000:
			return TREE;
		case 0100000:
			if ((bits & 0111) != 0)
				return EXECUTABLE_FILE;
			return REGULAR_FILE;
		case 0120000:
			return SYMLINK;
		case 0160000:
			return GITLINK;
		}

		return new FileMode(bits, Constants.OBJ_BAD) {
			@Override
			public boolean equals(final int a) {
				return bits == a;
			}
		};
	}

	private final byte[] octalBytes;

	private final int modeBits;

	private final int objectType;

	private FileMode(int mode, final int expType) {
		modeBits = mode;
		objectType = expType;
		if (mode != 0) {
			final byte[] tmp = new byte[10];
			int p = tmp.length;

			while (mode != 0) {
				tmp[--p] = (byte) ('0' + (mode & 07));
				mode >>= 3;
			}

			octalBytes = new byte[tmp.length - p];
			for (int k = 0; k < octalBytes.length; k++) {
				octalBytes[k] = tmp[p + k];
			}
		} else {
			octalBytes = new byte[] { '0' };
		}
	}

	/**
	 * Test a file mode for equality with this {@link FileMode} object.
	 * 
	 * @param modebits
	 * @return true if the mode bits represent the same mode as this object
	 */
	public abstract boolean equals(final int modebits);

	/**
	 * Copy this mode as a sequence of octal US-ASCII bytes.
	 * <p>
	 * The mode is copied as a sequence of octal digits using the US-ASCII
	 * character encoding. The sequence does not use a leading '0' prefix to
	 * indicate octal notation. This method is suitable for generation of a mode
	 * string within a GIT tree object.
	 * </p>
	 * 
	 * @param os
	 *            stream to copy the mode to.
	 * @throws IOException
	 *             the stream encountered an error during the copy.
	 */
	public void copyTo(final OutputStream os) throws IOException {
		os.write(octalBytes);
	}

	/**
	 * @return the number of bytes written by {@link #copyTo(OutputStream)}.
	 */
	public int copyToLength() {
		return octalBytes.length;
	}

	/**
	 * Get the object type that should appear for this type of mode.
	 * <p>
	 * See the object type constants in {@link Constants}.
	 * 
	 * @return one of the well known object type constants.
	 */
	public int getObjectType() {
		return objectType;
	}

	/** Format this mode as an octal string (for debugging only). */
	public String toString() {
		return Integer.toOctalString(modeBits);
	}

	/**
	 * @return The mode bits as an integer.
	 */
	public int getBits() {
		return modeBits;
	}
}
