/*
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
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

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Set;

import org.spearce.jgit.errors.TransportException;
import org.spearce.jgit.lib.AnyObjectId;
import org.spearce.jgit.lib.MutableObjectId;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ProgressMonitor;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevCommitList;
import org.spearce.jgit.revwalk.RevFlag;
import org.spearce.jgit.revwalk.RevObject;
import org.spearce.jgit.revwalk.RevSort;
import org.spearce.jgit.revwalk.RevWalk;
import org.spearce.jgit.revwalk.filter.CommitTimeRevFilter;
import org.spearce.jgit.revwalk.filter.RevFilter;

/**
 * Fetch implementation using the native Git pack transfer service.
 * <p>
 * This is the canonical implementation for transferring objects from the remote
 * repository to the local repository by talking to the 'git-upload-pack'
 * service. Objects are packed on the remote side into a pack file and then sent
 * down the pipe to us.
 * <p>
 * This connection requires only a bi-directional pipe or socket, and thus is
 * easily wrapped up into a local process pipe, anonymous TCP socket, or a
 * command executed through an SSH tunnel.
 * <p>
 * Concrete implementations should just call
 * {@link #init(java.io.InputStream, java.io.OutputStream)} and
 * {@link #readAdvertisedRefs()} methods in constructor or before any use. They
 * should also handle resources releasing in {@link #close()} method if needed.
 */
abstract class BasePackFetchConnection extends BasePackConnection implements
		FetchConnection {
	/**
	 * Maximum number of 'have' lines to send before giving up.
	 * <p>
	 * During {@link #negotiate(ProgressMonitor)} we send at most this many
	 * commits to the remote peer as 'have' lines without an ACK response before
	 * we give up.
	 */
	private static final int MAX_HAVES = 256;

	protected static final int MAX_CLIENT_BUFFER = MAX_HAVES * 46 + 1024;

	static final String OPTION_INCLUDE_TAG = "include-tag";

	static final String OPTION_MULTI_ACK = "multi_ack";

	static final String OPTION_THIN_PACK = "thin-pack";

	static final String OPTION_SIDE_BAND = "side-band";

	static final String OPTION_SIDE_BAND_64K = "side-band-64k";

	static final String OPTION_OFS_DELTA = "ofs-delta";

	static final String OPTION_SHALLOW = "shallow";

	static final String OPTION_NO_PROGRESS = "no-progress";

	private final RevWalk walk;

	/** All commits that are immediately reachable by a local ref. */
	private RevCommitList<RevCommit> reachableCommits;

	/** Marks an object as having all its dependencies. */
	final RevFlag REACHABLE;

	/** Marks a commit known to both sides of the connection. */
	final RevFlag COMMON;

	/** Marks a commit listed in the advertised refs. */
	final RevFlag ADVERTISED;

	private boolean multiAck;

	private boolean thinPack;

	private boolean sideband;

	private boolean includeTags;

	BasePackFetchConnection(final PackTransport packTransport) {
		super(packTransport);
		includeTags = packTransport.getTagOpt() != TagOpt.NO_TAGS;
		thinPack = packTransport.isFetchThin();

		walk = new RevWalk(local);
		reachableCommits = new RevCommitList<RevCommit>();
		REACHABLE = walk.newFlag("REACHABLE");
		COMMON = walk.newFlag("COMMON");
		ADVERTISED = walk.newFlag("ADVERTISED");

		walk.carry(COMMON);
		walk.carry(REACHABLE);
		walk.carry(ADVERTISED);
	}

	public final void fetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have)
			throws TransportException {
		markStartedOperation();
		doFetch(monitor, want, have);
	}

	public boolean didFetchIncludeTags() {
		return false;
	}

	public boolean didFetchTestConnectivity() {
		return false;
	}

	protected void doFetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have)
			throws TransportException {
		try {
			markRefsAdvertised();
			markReachable(have, maxTimeWanted(want));

			if (sendWants(want)) {
				negotiate(monitor);

				walk.dispose();
				reachableCommits = null;

				receivePack(monitor);
			}
		} catch (CancelledException ce) {
			close();
			return; // Caller should test (or just know) this themselves.
		} catch (IOException err) {
			close();
			throw new TransportException(err.getMessage(), err);
		} catch (RuntimeException err) {
			close();
			throw new TransportException(err.getMessage(), err);
		}
	}

	private int maxTimeWanted(final Collection<Ref> wants) {
		int maxTime = 0;
		for (final Ref r : wants) {
			try {
				final RevObject obj = walk.parseAny(r.getObjectId());
				if (obj instanceof RevCommit) {
					final int cTime = ((RevCommit) obj).getCommitTime();
					if (maxTime < cTime)
						maxTime = cTime;
				}
			} catch (IOException error) {
				// We don't have it, but we want to fetch (thus fixing error).
			}
		}
		return maxTime;
	}

	private void markReachable(final Set<ObjectId> have, final int maxTime)
			throws IOException {
		for (final Ref r : local.getAllRefs().values()) {
			try {
				final RevCommit o = walk.parseCommit(r.getObjectId());
				o.add(REACHABLE);
				reachableCommits.add(o);
			} catch (IOException readError) {
				// If we cannot read the value of the ref skip it.
			}
		}

		for (final ObjectId id : have) {
			try {
				final RevCommit o = walk.parseCommit(id);
				o.add(REACHABLE);
				reachableCommits.add(o);
			} catch (IOException readError) {
				// If we cannot read the value of the ref skip it.
			}
		}

		if (maxTime > 0) {
			// Mark reachable commits until we reach maxTime. These may
			// wind up later matching up against things we want and we
			// can avoid asking for something we already happen to have.
			//
			final Date maxWhen = new Date(maxTime * 1000L);
			walk.sort(RevSort.COMMIT_TIME_DESC);
			walk.markStart(reachableCommits);
			walk.setRevFilter(CommitTimeRevFilter.after(maxWhen));
			for (;;) {
				final RevCommit c = walk.next();
				if (c == null)
					break;
				if (c.has(ADVERTISED) && !c.has(COMMON)) {
					// This is actually going to be a common commit, but
					// our peer doesn't know that fact yet.
					//
					c.add(COMMON);
					c.carry(COMMON);
					reachableCommits.add(c);
				}
			}
		}
	}

	private boolean sendWants(final Collection<Ref> want) throws IOException {
		boolean first = true;
		for (final Ref r : want) {
			try {
				if (walk.parseAny(r.getObjectId()).has(REACHABLE)) {
					// We already have this object. Asking for it is
					// not a very good idea.
					//
					continue;
				}
			} catch (IOException err) {
				// Its OK, we don't have it, but we want to fix that
				// by fetching the object from the other side.
			}

			final StringBuilder line = new StringBuilder(46);
			line.append("want ");
			line.append(r.getObjectId().name());
			if (first) {
				line.append(enableCapabilities());
				first = false;
			}
			line.append('\n');
			pckOut.writeString(line.toString());
		}
		pckOut.end();
		outNeedsEnd = false;
		return !first;
	}

	private String enableCapabilities() {
		final StringBuilder line = new StringBuilder();
		if (includeTags)
			includeTags = wantCapability(line, OPTION_INCLUDE_TAG);
		wantCapability(line, OPTION_OFS_DELTA);
		multiAck = wantCapability(line, OPTION_MULTI_ACK);
		if (thinPack)
			thinPack = wantCapability(line, OPTION_THIN_PACK);
		if (wantCapability(line, OPTION_SIDE_BAND_64K))
			sideband = true;
		else if (wantCapability(line, OPTION_SIDE_BAND))
			sideband = true;
		return line.toString();
	}

	private void negotiate(final ProgressMonitor monitor) throws IOException,
			CancelledException {
		final MutableObjectId ackId = new MutableObjectId();
		int resultsPending = 0;
		int havesSent = 0;
		int havesSinceLastContinue = 0;
		boolean receivedContinue = false;
		boolean receivedAck = false;
		boolean sendHaves = true;

		negotiateBegin();
		while (sendHaves) {
			final RevCommit c = walk.next();
			if (c == null)
				break;

			pckOut.writeString("have " + c.getId().name() + "\n");
			havesSent++;
			havesSinceLastContinue++;

			if ((31 & havesSent) != 0) {
				// We group the have lines into blocks of 32, each marked
				// with a flush (aka end). This one is within a block so
				// continue with another have line.
				//
				continue;
			}

			if (monitor.isCancelled())
				throw new CancelledException();

			pckOut.end();
			resultsPending++; // Each end will cause a result to come back.

			if (havesSent == 32) {
				// On the first block we race ahead and try to send
				// more of the second block while waiting for the
				// remote to respond to our first block request.
				// This keeps us one block ahead of the peer.
				//
				continue;
			}

			while (resultsPending > 0) {
				final PacketLineIn.AckNackResult anr;

				anr = pckIn.readACK(ackId);
				resultsPending--;
				if (anr == PacketLineIn.AckNackResult.NAK) {
					// More have lines are necessary to compute the
					// pack on the remote side. Keep doing that.
					//
					break;
				}

				if (anr == PacketLineIn.AckNackResult.ACK) {
					// The remote side is happy and knows exactly what
					// to send us. There is no further negotiation and
					// we can break out immediately.
					//
					multiAck = false;
					resultsPending = 0;
					receivedAck = true;
					sendHaves = false;
					break;
				}

				if (anr == PacketLineIn.AckNackResult.ACK_CONTINUE) {
					// The server knows this commit (ackId). We don't
					// need to send any further along its ancestry, but
					// we need to continue to talk about other parts of
					// our local history.
					//
					markCommon(walk.parseAny(ackId));
					receivedAck = true;
					receivedContinue = true;
					havesSinceLastContinue = 0;
				}

				if (monitor.isCancelled())
					throw new CancelledException();
			}

			if (receivedContinue && havesSinceLastContinue > MAX_HAVES) {
				// Our history must be really different from the remote's.
				// We just sent a whole slew of have lines, and it did not
				// recognize any of them. Avoid sending our entire history
				// to them by giving up early.
				//
				break;
			}
		}

		// Tell the remote side we have run out of things to talk about.
		//
		if (monitor.isCancelled())
			throw new CancelledException();
		pckOut.writeString("done\n");
		pckOut.flush();

		if (!receivedAck) {
			// Apparently if we have never received an ACK earlier
			// there is one more result expected from the done we
			// just sent to the remote.
			//
			multiAck = false;
			resultsPending++;
		}

		while (resultsPending > 0 || multiAck) {
			final PacketLineIn.AckNackResult anr;

			anr = pckIn.readACK(ackId);
			resultsPending--;

			if (anr == PacketLineIn.AckNackResult.ACK)
				break; // commit negotiation is finished.

			if (anr == PacketLineIn.AckNackResult.ACK_CONTINUE) {
				// There must be a normal ACK following this.
				//
				multiAck = true;
			}

			if (monitor.isCancelled())
				throw new CancelledException();
		}
	}

	private void negotiateBegin() throws IOException {
		walk.resetRetain(REACHABLE, ADVERTISED);
		walk.markStart(reachableCommits);
		walk.sort(RevSort.COMMIT_TIME_DESC);
		walk.setRevFilter(new RevFilter() {
			@Override
			public RevFilter clone() {
				return this;
			}

			@Override
			public boolean include(final RevWalk walker, final RevCommit c) {
				final boolean remoteKnowsIsCommon = c.has(COMMON);
				if (c.has(ADVERTISED)) {
					// Remote advertised this, and we have it, hence common.
					// Whether or not the remote knows that fact is tested
					// before we added the flag. If the remote doesn't know
					// we have to still send them this object.
					//
					c.add(COMMON);
				}
				return !remoteKnowsIsCommon;
			}
		});
	}

	private void markRefsAdvertised() {
		for (final Ref r : getRefs()) {
			markAdvertised(r.getObjectId());
			if (r.getPeeledObjectId() != null)
				markAdvertised(r.getPeeledObjectId());
		}
	}

	private void markAdvertised(final AnyObjectId id) {
		try {
			walk.parseAny(id).add(ADVERTISED);
		} catch (IOException readError) {
			// We probably just do not have this object locally.
		}
	}

	private void markCommon(final RevObject obj) {
		obj.add(COMMON);
		if (obj instanceof RevCommit)
			((RevCommit) obj).carry(COMMON);
	}

	private void receivePack(final ProgressMonitor monitor) throws IOException {
		final IndexPack ip;

		ip = IndexPack.create(local, sideband ? pckIn.sideband(monitor) : in);
		ip.setFixThin(thinPack);
		ip.setObjectChecking(transport.isCheckFetchedObjects());
		ip.index(monitor);
		ip.renameAndOpenPack();
	}

	private static class CancelledException extends Exception {
		private static final long serialVersionUID = 1L;
	}
}