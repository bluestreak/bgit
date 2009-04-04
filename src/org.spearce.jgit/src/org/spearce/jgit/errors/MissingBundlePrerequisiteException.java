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

package org.spearce.jgit.errors;

import java.util.Collection;

import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.transport.URIish;

/**
 * Indicates a base/common object was required, but is not found.
 */
public class MissingBundlePrerequisiteException extends TransportException {
	private static final long serialVersionUID = 1L;

	private static String format(final Collection<ObjectId> ids) {
		final StringBuilder r = new StringBuilder();
		r.append("missing prerequisite commits:");
		for (final ObjectId p : ids) {
			r.append("\n  ");
			r.append(p.name());
		}
		return r.toString();
	}

	/**
	 * Constructs a MissingBundlePrerequisiteException for a set of objects.
	 *
	 * @param uri
	 *            URI used for transport
	 * @param ids
	 *            the ids of the base/common object(s) we don't have.
	 */
	public MissingBundlePrerequisiteException(final URIish uri,
			final Collection<ObjectId> ids) {
		super(uri, format(ids));
	}
}
