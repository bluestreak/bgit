/*
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

/**
 * Result of push operation to the remote repository. Holding information of
 * {@link OperationResult} and remote refs updates status.
 *
 * @see Transport#push(org.spearce.jgit.lib.ProgressMonitor, Collection)
 */
public class PushResult extends OperationResult {
	private Map<String, RemoteRefUpdate> remoteUpdates = Collections.emptyMap();

	/**
	 * Get status of remote refs updates. Together with
	 * {@link #getAdvertisedRefs()} it provides full description/status of each
	 * ref update.
	 * <p>
	 * Returned collection is not sorted in any order.
	 * </p>
	 *
	 * @return collection of remote refs updates
	 */
	public Collection<RemoteRefUpdate> getRemoteUpdates() {
		return Collections.unmodifiableCollection(remoteUpdates.values());
	}

	/**
	 * Get status of specific remote ref update by remote ref name. Together
	 * with {@link #getAdvertisedRef(String)} it provide full description/status
	 * of this ref update.
	 *
	 * @param refName
	 *            remote ref name
	 * @return status of remote ref update
	 */
	public RemoteRefUpdate getRemoteUpdate(final String refName) {
		return remoteUpdates.get(refName);
	}

	void setRemoteUpdates(
			final Map<String, RemoteRefUpdate> remoteUpdates) {
		this.remoteUpdates = remoteUpdates;
	}
}
