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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

/**
 * Git style file locking and replacement.
 * <p>
 * To modify a ref file Git tries to use an atomic update approach: we write the
 * new data into a brand new file, then rename it in place over the old name.
 * This way we can just delete the temporary file if anything goes wrong, and
 * nothing has been damaged. To coordinate access from multiple processes at
 * once Git tries to atomically create the new temporary file under a well-known
 * name.
 */
public class LockFile {
	private final File ref;

	private final File lck;

	private FileLock fLck;

	private boolean haveLck;

	private FileOutputStream os;

	private boolean needStatInformation;

	private long commitLastModified;

	/**
	 * Create a new lock for any file.
	 * 
	 * @param f
	 *            the file that will be locked.
	 */
	public LockFile(final File f) {
		ref = f;
		lck = new File(ref.getParentFile(), ref.getName() + ".lock");
	}

	/**
	 * Try to establish the lock.
	 * 
	 * @return true if the lock is now held by the caller; false if it is held
	 *         by someone else.
	 * @throws IOException
	 *             the temporary output file could not be created. The caller
	 *             does not hold the lock.
	 */
	public boolean lock() throws IOException {
		lck.getParentFile().mkdirs();
		if (lck.createNewFile()) {
			haveLck = true;
			try {
				os = new FileOutputStream(lck);
				try {
					fLck = os.getChannel().tryLock();
					if (fLck == null)
						throw new OverlappingFileLockException();
				} catch (OverlappingFileLockException ofle) {
					// We cannot use unlock() here as this file is not
					// held by us, but we thought we created it. We must
					// not delete it, as it belongs to some other process.
					//
					haveLck = false;
					try {
						os.close();
					} catch (IOException ioe) {
						// Fail by returning haveLck = false.
					}
					os = null;
				}
			} catch (IOException ioe) {
				unlock();
				throw ioe;
			}
		}
		return haveLck;
	}

	/**
	 * Try to establish the lock for appending.
	 * 
	 * @return true if the lock is now held by the caller; false if it is held
	 *         by someone else.
	 * @throws IOException
	 *             the temporary output file could not be created. The caller
	 *             does not hold the lock.
	 */
	public boolean lockForAppend() throws IOException {
		if (!lock())
			return false;
		copyCurrentContent();
		return true;
	}

	/**
	 * Copy the current file content into the temporary file.
	 * <p>
	 * This method saves the current file content by inserting it into the
	 * temporary file, so that the caller can safely append rather than replace
	 * the primary file.
	 * <p>
	 * This method does nothing if the current file does not exist, or exists
	 * but is empty.
	 * 
	 * @throws IOException
	 *             the temporary file could not be written, or a read error
	 *             occurred while reading from the current file. The lock is
	 *             released before throwing the underlying IO exception to the
	 *             caller.
	 * @throws RuntimeException
	 *             the temporary file could not be written. The lock is released
	 *             before throwing the underlying exception to the caller.
	 */
	public void copyCurrentContent() throws IOException {
		requireLock();
		try {
			final FileInputStream fis = new FileInputStream(ref);
			try {
				final byte[] buf = new byte[2048];
				int r;
				while ((r = fis.read(buf)) >= 0)
					os.write(buf, 0, r);
			} finally {
				fis.close();
			}
		} catch (FileNotFoundException fnfe) {
			// Don't worry about a file that doesn't exist yet, it
			// conceptually has no current content to copy.
			//
		} catch (IOException ioe) {
			unlock();
			throw ioe;
		} catch (RuntimeException ioe) {
			unlock();
			throw ioe;
		} catch (Error ioe) {
			unlock();
			throw ioe;
		}
	}

	/**
	 * Write an ObjectId and LF to the temporary file.
	 * 
	 * @param id
	 *            the id to store in the file. The id will be written in hex,
	 *            followed by a sole LF.
	 * @throws IOException
	 *             the temporary file could not be written. The lock is released
	 *             before throwing the underlying IO exception to the caller.
	 * @throws RuntimeException
	 *             the temporary file could not be written. The lock is released
	 *             before throwing the underlying exception to the caller.
	 */
	public void write(final ObjectId id) throws IOException {
		requireLock();
		try {
			final BufferedOutputStream b;
			b = new BufferedOutputStream(os, Constants.OBJECT_ID_LENGTH * 2 + 1);
			id.copyTo(b);
			b.write('\n');
			b.flush();
			fLck.release();
			b.close();
			os = null;
		} catch (IOException ioe) {
			unlock();
			throw ioe;
		} catch (RuntimeException ioe) {
			unlock();
			throw ioe;
		} catch (Error ioe) {
			unlock();
			throw ioe;
		}
	}

	/**
	 * Write arbitrary data to the temporary file.
	 * 
	 * @param content
	 *            the bytes to store in the temporary file. No additional bytes
	 *            are added, so if the file must end with an LF it must appear
	 *            at the end of the byte array.
	 * @throws IOException
	 *             the temporary file could not be written. The lock is released
	 *             before throwing the underlying IO exception to the caller.
	 * @throws RuntimeException
	 *             the temporary file could not be written. The lock is released
	 *             before throwing the underlying exception to the caller.
	 */
	public void write(final byte[] content) throws IOException {
		requireLock();
		try {
			os.write(content);
			os.flush();
			fLck.release();
			os.close();
			os = null;
		} catch (IOException ioe) {
			unlock();
			throw ioe;
		} catch (RuntimeException ioe) {
			unlock();
			throw ioe;
		} catch (Error ioe) {
			unlock();
			throw ioe;
		}
	}

	/**
	 * Obtain the direct output stream for this lock.
	 * <p>
	 * The stream may only be accessed once, and only after {@link #lock()} has
	 * been successfully invoked and returned true. Callers must close the
	 * stream prior to calling {@link #commit()} to commit the change.
	 * 
	 * @return a stream to write to the new file. The stream is unbuffered.
	 */
	public OutputStream getOutputStream() {
		requireLock();
		return new OutputStream() {
			@Override
			public void write(final byte[] b, final int o, final int n)
					throws IOException {
				os.write(b, o, n);
			}

			@Override
			public void write(final byte[] b) throws IOException {
				os.write(b);
			}

			@Override
			public void write(final int b) throws IOException {
				os.write(b);
			}

			@Override
			public void flush() throws IOException {
				os.flush();
			}

			@Override
			public void close() throws IOException {
				try {
					os.flush();
					fLck.release();
					os.close();
					os = null;
				} catch (IOException ioe) {
					unlock();
					throw ioe;
				} catch (RuntimeException ioe) {
					unlock();
					throw ioe;
				} catch (Error ioe) {
					unlock();
					throw ioe;
				}
			}
		};
	}

	private void requireLock() {
		if (os == null) {
			unlock();
			throw new IllegalStateException("Lock on " + ref + " not held.");
		}
	}

	/**
	 * Request that {@link #commit()} remember modification time.
	 * 
	 * @param on
	 *            true if the commit method must remember the modification time.
	 */
	public void setNeedStatInformation(final boolean on) {
		needStatInformation = on;
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
		if (os != null) {
			unlock();
			throw new IllegalStateException("Lock on " + ref + " not closed.");
		}

		saveStatInformation();
		if (lck.renameTo(ref))
			return true;
		if (!ref.exists() || ref.delete())
			if (lck.renameTo(ref))
				return true;
		unlock();
		return false;
	}

	private void saveStatInformation() {
		if (needStatInformation)
			commitLastModified = lck.lastModified();
	}

	/**
	 * Get the modification time of the output file when it was committed.
	 * 
	 * @return modification time of the lock file right before we committed it.
	 */
	public long getCommitLastModified() {
		return commitLastModified;
	}

	/**
	 * Unlock this file and abort this change.
	 * <p>
	 * The temporary file (if created) is deleted before returning.
	 */
	public void unlock() {
		if (os != null) {
			if (fLck != null) {
				try {
					fLck.release();
				} catch (IOException ioe) {
					// Huh?
				}
				fLck = null;
			}
			try {
				os.close();
			} catch (IOException ioe) {
				// Ignore this
			}
			os = null;
		}

		if (haveLck) {
			haveLck = false;
			lck.delete();
		}
	}
}
