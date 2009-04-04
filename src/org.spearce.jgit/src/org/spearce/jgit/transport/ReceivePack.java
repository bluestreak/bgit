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

package org.spearce.jgit.transport;

import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.errors.PackProtocolException;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.NullProgressMonitor;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.RefComparator;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.RepositoryConfig;
import org.spearce.jgit.revwalk.ObjectWalk;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevObject;
import org.spearce.jgit.revwalk.RevWalk;
import org.spearce.jgit.transport.ReceiveCommand.Result;

/**
 * Implements the server side of a push connection, receiving objects.
 */
public class ReceivePack {
	static final String CAPABILITY_REPORT_STATUS = BasePackPushConnection.CAPABILITY_REPORT_STATUS;

	static final String CAPABILITY_DELETE_REFS = BasePackPushConnection.CAPABILITY_DELETE_REFS;

	/** Database we write the stored objects into. */
	private final Repository db;

	/** Revision traversal support over {@link #db}. */
	private final RevWalk walk;

	/** Should an incoming transfer validate objects? */
	private boolean checkReceivedObjects;

	/** Should an incoming transfer permit create requests? */
	private boolean allowCreates;

	/** Should an incoming transfer permit delete requests? */
	private boolean allowDeletes;

	/** Should an incoming transfer permit non-fast-forward requests? */
	private boolean allowNonFastForwards;

	/** Identity to record action as within the reflog. */
	private PersonIdent refLogIdent;

	/** Hook to validate the update commands before execution. */
	private PreReceiveHook preReceive;

	/** Hook to report on the commands after execution. */
	private PostReceiveHook postReceive;

	private InputStream rawIn;

	private OutputStream rawOut;

	private PacketLineIn pckIn;

	private PacketLineOut pckOut;

	private PrintWriter msgs;

	/** The refs we advertised as existing at the start of the connection. */
	private Map<String, Ref> refs;

	/** Capabilities requested by the client. */
	private Set<String> enabledCapablities;

	/** Commands to execute, as received by the client. */
	private List<ReceiveCommand> commands;

	/** An exception caught while unpacking and fsck'ing the objects. */
	private Throwable unpackError;

	/** if {@link #enabledCapablities} has {@link #CAPABILITY_REPORT_STATUS} */
	private boolean reportStatus;

	/**
	 * Create a new pack receive for an open repository.
	 *
	 * @param into
	 *            the destination repository.
	 */
	public ReceivePack(final Repository into) {
		db = into;
		walk = new RevWalk(db);

		final RepositoryConfig cfg = db.getConfig();
		checkReceivedObjects = cfg.getBoolean("receive", "fsckobjects", false);
		allowCreates = true;
		allowDeletes = !cfg.getBoolean("receive", "denydeletes", false);
		allowNonFastForwards = !cfg.getBoolean("receive",
				"denynonfastforwards", false);
		preReceive = PreReceiveHook.NULL;
		postReceive = PostReceiveHook.NULL;
	}

	/** @return the repository this receive completes into. */
	public final Repository getRepository() {
		return db;
	}

	/** @return the RevWalk instance used by this connection. */
	public final RevWalk getRevWalk() {
		return walk;
	}

	/** @return all refs which were advertised to the client. */
	public final Map<String, Ref> getAdvertisedRefs() {
		return refs;
	}

	/**
	 * @return true if this instance will verify received objects are formatted
	 *         correctly. Validating objects requires more CPU time on this side
	 *         of the connection.
	 */
	public boolean isCheckReceivedObjects() {
		return checkReceivedObjects;
	}

	/**
	 * @param check
	 *            true to enable checking received objects; false to assume all
	 *            received objects are valid.
	 */
	public void setCheckReceivedObjects(final boolean check) {
		checkReceivedObjects = check;
	}

	/** @return true if the client can request refs to be created. */
	public boolean isAllowCreates() {
		return allowCreates;
	}

	/**
	 * @param canCreate
	 *            true to permit create ref commands to be processed.
	 */
	public void setAllowCreates(final boolean canCreate) {
		allowCreates = canCreate;
	}

	/** @return true if the client can request refs to be deleted. */
	public boolean isAllowDeletes() {
		return allowDeletes;
	}

	/**
	 * @param canDelete
	 *            true to permit delete ref commands to be processed.
	 */
	public void setAllowDeletes(final boolean canDelete) {
		allowDeletes = canDelete;
	}

	/**
	 * @return true if the client can request non-fast-forward updates of a ref,
	 *         possibly making objects unreachable.
	 */
	public boolean isAllowNonFastForwards() {
		return allowNonFastForwards;
	}

	/**
	 * @param canRewind
	 *            true to permit the client to ask for non-fast-forward updates
	 *            of an existing ref.
	 */
	public void setAllowNonFastForwards(final boolean canRewind) {
		allowNonFastForwards = canRewind;
	}

	/** @return identity of the user making the changes in the reflog. */
	public PersonIdent getRefLogIdent() {
		return refLogIdent;
	}

	/**
	 * Set the identity of the user appearing in the affected reflogs.
	 * <p>
	 * The timestamp portion of the identity is ignored. A new identity with the
	 * current timestamp will be created automatically when the updates occur
	 * and the log records are written.
	 *
	 * @param pi
	 *            identity of the user. If null the identity will be
	 *            automatically determined based on the repository
	 *            configuration.
	 */
	public void setRefLogIdent(final PersonIdent pi) {
		refLogIdent = pi;
	}

	/** @return get the hook invoked before updates occur. */
	public PreReceiveHook getPreReceiveHook() {
		return preReceive;
	}

	/**
	 * Set the hook which is invoked prior to commands being executed.
	 * <p>
	 * Only valid commands (those which have no obvious errors according to the
	 * received input and this instance's configuration) are passed into the
	 * hook. The hook may mark a command with a result of any value other than
	 * {@link Result#NOT_ATTEMPTED} to block its execution.
	 * <p>
	 * The hook may be called with an empty command collection if the current
	 * set is completely invalid.
	 *
	 * @param h
	 *            the hook instance; may be null to disable the hook.
	 */
	public void setPreReceiveHook(final PreReceiveHook h) {
		preReceive = h != null ? h : PreReceiveHook.NULL;
	}

	/** @return get the hook invoked after updates occur. */
	public PostReceiveHook getPostReceiveHook() {
		return postReceive;
	}

	/**
	 * Set the hook which is invoked after commands are executed.
	 * <p>
	 * Only successful commands (type is {@link Result#OK}) are passed into the
	 * hook. The hook may be called with an empty command collection if the
	 * current set all resulted in an error.
	 *
	 * @param h
	 *            the hook instance; may be null to disable the hook.
	 */
	public void setPostReceiveHook(final PostReceiveHook h) {
		postReceive = h != null ? h : PostReceiveHook.NULL;
	}

	/** @return all of the command received by the current request. */
	public List<ReceiveCommand> getAllCommands() {
		return Collections.unmodifiableList(commands);
	}

	/**
	 * Send an error message to the client, if it supports receiving them.
	 * <p>
	 * If the client doesn't support receiving messages, the message will be
	 * discarded, with no other indication to the caller or to the client.
	 * <p>
	 * {@link PreReceiveHook}s should always try to use
	 * {@link ReceiveCommand#setResult(Result, String)} with a result status of
	 * {@link Result#REJECTED_OTHER_REASON} to indicate any reasons for
	 * rejecting an update. Messages attached to a command are much more likely
	 * to be returned to the client.
	 *
	 * @param what
	 *            string describing the problem identified by the hook. The
	 *            string must not end with an LF, and must not contain an LF.
	 */
	public void sendError(final String what) {
		sendMessage("error", what);
	}

	/**
	 * Send a message to the client, if it supports receiving them.
	 * <p>
	 * If the client doesn't support receiving messages, the message will be
	 * discarded, with no other indication to the caller or to the client.
	 *
	 * @param what
	 *            string describing the problem identified by the hook. The
	 *            string must not end with an LF, and must not contain an LF.
	 */
	public void sendMessage(final String what) {
		sendMessage("remote", what);
	}

	private void sendMessage(final String type, final String what) {
		if (msgs != null)
			msgs.println(type + ": " + what);
	}

	/**
	 * Execute the receive task on the socket.
	 *
	 * @param input
	 *            raw input to read client commands and pack data from. Caller
	 *            must ensure the input is buffered, otherwise read performance
	 *            may suffer.
	 * @param output
	 *            response back to the Git network client. Caller must ensure
	 *            the output is buffered, otherwise write performance may
	 *            suffer.
	 * @param messages
	 *            secondary "notice" channel to send additional messages out
	 *            through. When run over SSH this should be tied back to the
	 *            standard error channel of the command execution. For most
	 *            other network connections this should be null.
	 * @throws IOException
	 */
	public void receive(final InputStream input, final OutputStream output,
			final OutputStream messages) throws IOException {
		try {
			rawIn = input;
			rawOut = output;

			pckIn = new PacketLineIn(rawIn);
			pckOut = new PacketLineOut(rawOut);
			if (messages != null) {
				msgs = new PrintWriter(new BufferedWriter(
						new OutputStreamWriter(messages, Constants.CHARSET),
						8192)) {
					@Override
					public void println() {
						print('\n');
					}
				};
			}

			enabledCapablities = new HashSet<String>();
			commands = new ArrayList<ReceiveCommand>();

			service();
		} finally {
			try {
				if (msgs != null) {
					msgs.flush();
				}
			} finally {
				rawIn = null;
				rawOut = null;
				pckIn = null;
				pckOut = null;
				msgs = null;
				refs = null;
				enabledCapablities = null;
				commands = null;
			}
		}
	}

	private void service() throws IOException {
		sendAdvertisedRefs();
		recvCommands();
		if (!commands.isEmpty()) {
			enableCapabilities();

			if (needPack()) {
				try {
					receivePack();
					if (isCheckReceivedObjects())
						checkConnectivity();
					unpackError = null;
				} catch (IOException err) {
					unpackError = err;
				} catch (RuntimeException err) {
					unpackError = err;
				} catch (Error err) {
					unpackError = err;
				}
			}

			if (unpackError == null) {
				validateCommands();
				executeCommands();
			}

			if (reportStatus) {
				sendStatusReport(true, new Reporter() {
					void sendString(final String s) throws IOException {
						pckOut.writeString(s + "\n");
					}
				});
				pckOut.end();
			} else if (msgs != null) {
				sendStatusReport(false, new Reporter() {
					void sendString(final String s) throws IOException {
						msgs.println(s);
					}
				});
				msgs.flush();
			}

			postReceive.onPostReceive(this, filterCommands(Result.OK));
		}
	}

	private void sendAdvertisedRefs() throws IOException {
		refs = db.getAllRefs();

		final StringBuilder m = new StringBuilder(100);
		final char[] idtmp = new char[2 * Constants.OBJECT_ID_LENGTH];
		final Iterator<Ref> i = RefComparator.sort(refs.values()).iterator();
		{
			if (i.hasNext()) {
				final Ref r = i.next();
				format(m, idtmp, r.getObjectId(), r.getOrigName());
			} else {
				format(m, idtmp, ObjectId.zeroId(), "capabilities^{}");
			}
			m.append('\0');
			m.append(' ');
			m.append(CAPABILITY_DELETE_REFS);
			m.append(' ');
			m.append(CAPABILITY_REPORT_STATUS);
			m.append(' ');
			writeAdvertisedRef(m);
		}

		while (i.hasNext()) {
			final Ref r = i.next();
			format(m, idtmp, r.getObjectId(), r.getOrigName());
			writeAdvertisedRef(m);
		}
		pckOut.end();
	}

	private void format(final StringBuilder m, final char[] idtmp,
			final ObjectId id, final String name) {
		m.setLength(0);
		id.copyTo(idtmp, m);
		m.append(' ');
		m.append(name);
	}

	private void writeAdvertisedRef(final StringBuilder m) throws IOException {
		m.append('\n');
		pckOut.writeString(m.toString());
	}

	private void recvCommands() throws IOException {
		for (;;) {
			String line;
			try {
				line = pckIn.readStringNoLF();
			} catch (EOFException eof) {
				if (commands.isEmpty())
					return;
				throw eof;
			}

			if (commands.isEmpty()) {
				final int nul = line.indexOf('\0');
				if (nul >= 0) {
					for (String c : line.substring(nul + 1).split(" "))
						enabledCapablities.add(c);
					line = line.substring(0, nul);
				}
			}

			if (line.length() == 0)
				break;
			if (line.length() < 83) {
				final String m = "error: invalid protocol: wanted 'old new ref'";
				sendError(m);
				throw new PackProtocolException(m);
			}

			final ObjectId oldId = ObjectId.fromString(line.substring(0, 40));
			final ObjectId newId = ObjectId.fromString(line.substring(41, 81));
			final String name = line.substring(82);
			final ReceiveCommand cmd = new ReceiveCommand(oldId, newId, name);
			cmd.setRef(refs.get(cmd.getRefName()));
			commands.add(cmd);
		}
	}

	private void enableCapabilities() {
		reportStatus = enabledCapablities.contains(CAPABILITY_REPORT_STATUS);
	}

	private boolean needPack() {
		for (final ReceiveCommand cmd : commands) {
			if (cmd.getType() != ReceiveCommand.Type.DELETE)
				return true;
		}
		return false;
	}

	private void receivePack() throws IOException {
		final IndexPack ip = IndexPack.create(db, rawIn);
		ip.setFixThin(true);
		ip.setObjectChecking(isCheckReceivedObjects());
		ip.index(NullProgressMonitor.INSTANCE);
		ip.renameAndOpenPack();
	}

	private void checkConnectivity() throws IOException {
		final ObjectWalk ow = new ObjectWalk(db);
		for (final ReceiveCommand cmd : commands) {
			if (cmd.getResult() != Result.NOT_ATTEMPTED)
				continue;
			if (cmd.getType() == ReceiveCommand.Type.DELETE)
				continue;
			ow.markStart(ow.parseAny(cmd.getNewId()));
		}
		for (final Ref ref : refs.values())
			ow.markUninteresting(ow.parseAny(ref.getObjectId()));
		ow.checkConnectivity();
	}

	private void validateCommands() {
		for (final ReceiveCommand cmd : commands) {
			final Ref ref = cmd.getRef();
			if (cmd.getResult() != Result.NOT_ATTEMPTED)
				continue;

			if (cmd.getType() == ReceiveCommand.Type.DELETE
					&& !isAllowDeletes()) {
				// Deletes are not supported on this repository.
				//
				cmd.setResult(Result.REJECTED_NODELETE);
				continue;
			}

			if (cmd.getType() == ReceiveCommand.Type.CREATE) {
				if (!isAllowCreates()) {
					cmd.setResult(Result.REJECTED_NOCREATE);
					continue;
				}

				if (ref != null && !isAllowNonFastForwards()) {
					// Creation over an existing ref is certainly not going
					// to be a fast-forward update. We can reject it early.
					//
					cmd.setResult(Result.REJECTED_NONFASTFORWARD);
					continue;
				}

				if (ref != null) {
					// A well behaved client shouldn't have sent us an
					// update command for a ref we advertised to it.
					//
					cmd.setResult(Result.REJECTED_OTHER_REASON, "ref exists");
					continue;
				}
			}

			if (cmd.getType() == ReceiveCommand.Type.DELETE && ref != null
					&& !ObjectId.zeroId().equals(cmd.getOldId())
					&& !ref.getObjectId().equals(cmd.getOldId())) {
				// Delete commands can be sent with the old id matching our
				// advertised value, *OR* with the old id being 0{40}. Any
				// other requested old id is invalid.
				//
				cmd.setResult(Result.REJECTED_OTHER_REASON,
						"invalid old id sent");
				continue;
			}

			if (cmd.getType() == ReceiveCommand.Type.UPDATE) {
				if (ref == null) {
					// The ref must have been advertised in order to be updated.
					//
					cmd.setResult(Result.REJECTED_OTHER_REASON, "no such ref");
					continue;
				}

				if (!ref.getObjectId().equals(cmd.getOldId())) {
					// A properly functioning client will send the same
					// object id we advertised.
					//
					cmd.setResult(Result.REJECTED_OTHER_REASON,
							"invalid old id sent");
					continue;
				}

				// Is this possibly a non-fast-forward style update?
				//
				RevObject oldObj, newObj;
				try {
					oldObj = walk.parseAny(cmd.getOldId());
				} catch (IOException e) {
					cmd.setResult(Result.REJECTED_MISSING_OBJECT, cmd
							.getOldId().name());
					continue;
				}

				try {
					newObj = walk.parseAny(cmd.getNewId());
				} catch (IOException e) {
					cmd.setResult(Result.REJECTED_MISSING_OBJECT, cmd
							.getNewId().name());
					continue;
				}

				if (oldObj instanceof RevCommit && newObj instanceof RevCommit) {
					try {
						if (!walk.isMergedInto((RevCommit) oldObj,
								(RevCommit) newObj)) {
							cmd
									.setType(ReceiveCommand.Type.UPDATE_NONFASTFORWARD);
						}
					} catch (MissingObjectException e) {
						cmd.setResult(Result.REJECTED_MISSING_OBJECT, e
								.getMessage());
					} catch (IOException e) {
						cmd.setResult(Result.REJECTED_OTHER_REASON);
					}
				} else {
					cmd.setType(ReceiveCommand.Type.UPDATE_NONFASTFORWARD);
				}
			}

			if (!cmd.getRefName().startsWith(Constants.R_REFS)
					|| !Repository.isValidRefName(cmd.getRefName())) {
				cmd.setResult(Result.REJECTED_OTHER_REASON, "funny refname");
			}
		}
	}

	private void executeCommands() {
		preReceive.onPreReceive(this, filterCommands(Result.NOT_ATTEMPTED));
		for (final ReceiveCommand cmd : filterCommands(Result.NOT_ATTEMPTED))
			execute(cmd);
	}

	private void execute(final ReceiveCommand cmd) {
		try {
			final RefUpdate ru = db.updateRef(cmd.getRefName());
			ru.setRefLogIdent(getRefLogIdent());
			switch (cmd.getType()) {
			case DELETE:
				if (!ObjectId.zeroId().equals(cmd.getOldId())) {
					// We can only do a CAS style delete if the client
					// didn't bork its delete request by sending the
					// wrong zero id rather than the advertised one.
					//
					ru.setExpectedOldObjectId(cmd.getOldId());
				}
				ru.setForceUpdate(true);
				status(cmd, ru.delete(walk));
				break;

			case CREATE:
			case UPDATE:
			case UPDATE_NONFASTFORWARD:
				ru.setForceUpdate(isAllowNonFastForwards());
				ru.setExpectedOldObjectId(cmd.getOldId());
				ru.setNewObjectId(cmd.getNewId());
				ru.setRefLogMessage("push", true);
				status(cmd, ru.update(walk));
				break;
			}
		} catch (IOException err) {
			cmd.setResult(Result.REJECTED_OTHER_REASON, "lock error: "
					+ err.getMessage());
		}
	}

	private void status(final ReceiveCommand cmd, final RefUpdate.Result result) {
		switch (result) {
		case NOT_ATTEMPTED:
			cmd.setResult(Result.NOT_ATTEMPTED);
			break;

		case LOCK_FAILURE:
		case IO_FAILURE:
			cmd.setResult(Result.LOCK_FAILURE);
			break;

		case NO_CHANGE:
		case NEW:
		case FORCED:
		case FAST_FORWARD:
			cmd.setResult(Result.OK);
			break;

		case REJECTED:
			cmd.setResult(Result.REJECTED_NONFASTFORWARD);
			break;

		case REJECTED_CURRENT_BRANCH:
			cmd.setResult(Result.REJECTED_CURRENT_BRANCH);
			break;

		default:
			cmd.setResult(Result.REJECTED_OTHER_REASON, result.name());
			break;
		}
	}

	private List<ReceiveCommand> filterCommands(final Result want) {
		final List<ReceiveCommand> r = new ArrayList<ReceiveCommand>(commands
				.size());
		for (final ReceiveCommand cmd : commands) {
			if (cmd.getResult() == want)
				r.add(cmd);
		}
		return r;
	}

	private void sendStatusReport(final boolean forClient, final Reporter out)
			throws IOException {
		if (unpackError != null) {
			out.sendString("unpack error " + unpackError.getMessage());
			if (forClient) {
				for (final ReceiveCommand cmd : commands) {
					out.sendString("ng " + cmd.getRefName()
							+ " n/a (unpacker error)");
				}
			}
			return;
		}

		if (forClient)
			out.sendString("unpack ok");
		for (final ReceiveCommand cmd : commands) {
			if (cmd.getResult() == Result.OK) {
				if (forClient)
					out.sendString("ok " + cmd.getRefName());
				continue;
			}

			final StringBuilder r = new StringBuilder();
			r.append("ng ");
			r.append(cmd.getRefName());
			r.append(" ");

			switch (cmd.getResult()) {
			case NOT_ATTEMPTED:
				r.append("server bug; ref not processed");
				break;

			case REJECTED_NOCREATE:
				r.append("creation prohibited");
				break;

			case REJECTED_NODELETE:
				r.append("deletion prohibited");
				break;

			case REJECTED_NONFASTFORWARD:
				r.append("non-fast forward");
				break;

			case REJECTED_CURRENT_BRANCH:
				r.append("branch is currently checked out");
				break;

			case REJECTED_MISSING_OBJECT:
				if (cmd.getMessage() == null)
					r.append("missing object(s)");
				else if (cmd.getMessage().length() == 2 * Constants.OBJECT_ID_LENGTH)
					r.append("object " + cmd.getMessage() + " missing");
				else
					r.append(cmd.getMessage());
				break;

			case REJECTED_OTHER_REASON:
				if (cmd.getMessage() == null)
					r.append("unspecified reason");
				else
					r.append(cmd.getMessage());
				break;

			case LOCK_FAILURE:
				r.append("failed to lock");
				break;

			case OK:
				// We shouldn't have reached this case (see 'ok' case above).
				continue;
			}
			out.sendString(r.toString());
		}
	}

	static abstract class Reporter {
		abstract void sendString(String s) throws IOException;
	}
}
