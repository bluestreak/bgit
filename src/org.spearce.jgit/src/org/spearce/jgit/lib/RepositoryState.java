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

package org.spearce.jgit.lib;

/**
 * Important state of the repository that affects what can and cannot bed
 * done. This is things like unhandled conflicted merges and unfinished rebase.
 *
 * The granularity and set of states are somewhat arbitrary. The methods
 * on the state are the only supported means of deciding what to do.
 */
public enum RepositoryState {
	/**
	 * A safe state for working normally
	 * */
	SAFE {
		public boolean canCheckout() { return true; }
		public boolean canResetHead() { return true; }
		public boolean canCommit() { return true; }
		public String getDescription() { return "Normal"; }
	},

	/** An unfinished merge. Must resole or reset before continuing normally
	 */
	MERGING {
		public boolean canCheckout() { return false; }
		public boolean canResetHead() { return false; }
		public boolean canCommit() { return false; }
		public String getDescription() { return "Conflicts"; }
	},

	/**
	 * An unfinished rebase or am. Must resolve, skip or abort before normal work can take place
	 */
	REBASING {
		public boolean canCheckout() { return false; }
		public boolean canResetHead() { return false; }
		public boolean canCommit() { return true; }
		public String getDescription() { return "Rebase/Apply mailbox"; }
	},

	/**
	 * An unfinished rebase. Must resolve, skip or abort before normal work can take place
	 */
	REBASING_REBASING {
		public boolean canCheckout() { return false; }
		public boolean canResetHead() { return false; }
		public boolean canCommit() { return true; }
		public String getDescription() { return "Rebase"; }
	},

	/**
	 * An unfinished apply. Must resolve, skip or abort before normal work can take place
	 */
	APPLY {
		public boolean canCheckout() { return false; }
		public boolean canResetHead() { return false; }
		public boolean canCommit() { return true; }
		public String getDescription() { return "Apply mailbox"; }
	},

	/**
	 * An unfinished rebase with merge. Must resolve, skip or abort before normal work can take place
	 */
	REBASING_MERGE {
		public boolean canCheckout() { return false; }
		public boolean canResetHead() { return false; }
		public boolean canCommit() { return true; }
		public String getDescription() { return "Rebase w/merge"; }
	},

	/**
	 * An unfinished interactive rebase. Must resolve, skip or abort before normal work can take place
	 */
	REBASING_INTERACTIVE {
		public boolean canCheckout() { return false; }
		public boolean canResetHead() { return false; }
		public boolean canCommit() { return true; }
		public String getDescription() { return "Rebase interactive"; }
	},

	/**
	 * Bisecting being done. Normal work may continue but is discouraged
	 */
	BISECTING {
		/* Changing head is a normal operation when bisecting */
		public boolean canCheckout() { return true; }

		/* Do not reset, checkout instead */
		public boolean canResetHead() { return false; }

		/* Actually it may make sense, but for now we err on the side of caution */
		public boolean canCommit() { return false; }

		public String getDescription() { return "Bisecting"; }
	};

	/**
	 * @return true if changing HEAD is sane.
	 */
	public abstract boolean canCheckout();

	/**
	 * @return true if we can commit
	 */
	public abstract boolean canCommit();

	/**
	 * @return true if reset to another HEAD is considered SAFE
	 */
	public abstract boolean canResetHead();

	/**
	 * @return a human readable description of the state.
	 */
	public abstract String getDescription();
}
