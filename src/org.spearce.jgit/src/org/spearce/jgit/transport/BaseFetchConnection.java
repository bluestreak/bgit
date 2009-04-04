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

import java.util.Collection;
import java.util.Set;

import org.spearce.jgit.errors.TransportException;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ProgressMonitor;
import org.spearce.jgit.lib.Ref;

/**
 * Base helper class for fetch connection implementations. Provides some common
 * typical structures and methods used during fetch connection.
 * <p>
 * Implementors of fetch over pack-based protocols should consider using
 * {@link BasePackFetchConnection} instead.
 * </p>
 */
abstract class BaseFetchConnection extends BaseConnection implements
		FetchConnection {
	public final void fetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have)
			throws TransportException {
		markStartedOperation();
		doFetch(monitor, want, have);
	}

	/**
	 * Default implementation of {@link FetchConnection#didFetchIncludeTags()} -
	 * returning false.
	 */
	public boolean didFetchIncludeTags() {
		return false;
	}

	/**
	 * Implementation of {@link #fetch(ProgressMonitor, Collection, Set)}
	 * without checking for multiple fetch.
	 *
	 * @param monitor
	 *            as in {@link #fetch(ProgressMonitor, Collection, Set)}
	 * @param want
	 *            as in {@link #fetch(ProgressMonitor, Collection, Set)}
	 * @param have
	 *            as in {@link #fetch(ProgressMonitor, Collection, Set)}
	 * @throws TransportException
	 *             as in {@link #fetch(ProgressMonitor, Collection, Set)}, but
	 *             implementation doesn't have to care about multiple
	 *             {@link #fetch(ProgressMonitor, Collection, Set)} calls, as it
	 *             is checked in this class.
	 */
	protected abstract void doFetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have)
			throws TransportException;
}
