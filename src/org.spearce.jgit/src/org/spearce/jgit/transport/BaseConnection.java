/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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
import java.util.Collections;
import java.util.Map;

import org.spearce.jgit.errors.TransportException;
import org.spearce.jgit.lib.Ref;

/**
 * Base helper class for implementing operations connections.
 *
 * @see BasePackConnection
 * @see BaseFetchConnection
 */
abstract class BaseConnection implements Connection {

	private Map<String, Ref> advertisedRefs = Collections.emptyMap();

	private boolean startedOperation;

	public Map<String, Ref> getRefsMap() {
		return advertisedRefs;
	}

	public final Collection<Ref> getRefs() {
		return advertisedRefs.values();
	}

	public final Ref getRef(final String name) {
		return advertisedRefs.get(name);
	}

	public abstract void close();

	/**
	 * Denote the list of refs available on the remote repository.
	 * <p>
	 * Implementors should invoke this method once they have obtained the refs
	 * that are available from the remote repository.
	 *
	 * @param all
	 *            the complete list of refs the remote has to offer. This map
	 *            will be wrapped in an unmodifiable way to protect it, but it
	 *            does not get copied.
	 */
	protected void available(final Map<String, Ref> all) {
		advertisedRefs = Collections.unmodifiableMap(all);
	}

	/**
	 * Helper method for ensuring one-operation per connection. Check whether
	 * operation was already marked as started, and mark it as started.
	 *
	 * @throws TransportException
	 *             if operation was already marked as started.
	 */
	protected void markStartedOperation() throws TransportException {
		if (startedOperation)
			throw new TransportException(
					"Only one operation call per connection is supported.");
		startedOperation = true;
	}
}
