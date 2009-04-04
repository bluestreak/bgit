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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.spearce.jgit.errors.CompoundException;
import org.spearce.jgit.errors.CorruptObjectException;
import org.spearce.jgit.errors.MissingObjectException;
import org.spearce.jgit.errors.ObjectWritingException;
import org.spearce.jgit.errors.TransportException;
import org.spearce.jgit.lib.AnyObjectId;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.FileMode;
import org.spearce.jgit.lib.MutableObjectId;
import org.spearce.jgit.lib.ObjectChecker;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.PackIndex;
import org.spearce.jgit.lib.ProgressMonitor;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.UnpackedObjectLoader;
import org.spearce.jgit.revwalk.DateRevQueue;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevFlag;
import org.spearce.jgit.revwalk.RevObject;
import org.spearce.jgit.revwalk.RevTag;
import org.spearce.jgit.revwalk.RevTree;
import org.spearce.jgit.revwalk.RevWalk;
import org.spearce.jgit.treewalk.TreeWalk;

/**
 * Generic fetch support for dumb transport protocols.
 * <p>
 * Since there are no Git-specific smarts on the remote side of the connection
 * the client side must determine which objects it needs to copy in order to
 * completely fetch the requested refs and their history. The generic walk
 * support in this class parses each individual object (once it has been copied
 * to the local repository) and examines the list of objects that must also be
 * copied to create a complete history. Objects which are already available
 * locally are retained (and not copied), saving bandwidth for incremental
 * fetches. Pack files are copied from the remote repository only as a last
 * resort, as the entire pack must be copied locally in order to access any
 * single object.
 * <p>
 * This fetch connection does not actually perform the object data transfer.
 * Instead it delegates the transfer to a {@link WalkRemoteObjectDatabase},
 * which knows how to read individual files from the remote repository and
 * supply the data as a standard Java InputStream.
 * 
 * @see WalkRemoteObjectDatabase
 */
class WalkFetchConnection extends BaseFetchConnection {
	/** The repository this transport fetches into, or pushes out of. */
	private final Repository local;

	/** If not null the validator for received objects. */
	private final ObjectChecker objCheck;

	/**
	 * List of all remote repositories we may need to get objects out of.
	 * <p>
	 * The first repository in the list is the one we were asked to fetch from;
	 * the remaining repositories point to the alternate locations we can fetch
	 * objects through.
	 */
	private final List<WalkRemoteObjectDatabase> remotes;

	/** Most recently used item in {@link #remotes}. */
	private int lastRemoteIdx;

	private final RevWalk revWalk;

	private final TreeWalk treeWalk;

	/** Objects whose direct dependents we know we have (or will have). */
	private final RevFlag COMPLETE;

	/** Objects that have already entered {@link #workQueue}. */
	private final RevFlag IN_WORK_QUEUE;

	/** Commits that have already entered {@link #localCommitQueue}. */
	private final RevFlag LOCALLY_SEEN;

	/** Commits already reachable from all local refs. */
	private final DateRevQueue localCommitQueue;

	/** Objects we need to copy from the remote repository. */
	private LinkedList<ObjectId> workQueue;

	/** Databases we have not yet obtained the list of packs from. */
	private final LinkedList<WalkRemoteObjectDatabase> noPacksYet;

	/** Databases we have not yet obtained the alternates from. */
	private final LinkedList<WalkRemoteObjectDatabase> noAlternatesYet;

	/** Packs we have discovered, but have not yet fetched locally. */
	private final LinkedList<RemotePack> unfetchedPacks;

	/**
	 * Packs whose indexes we have looked at in {@link #unfetchedPacks}.
	 * <p>
	 * We try to avoid getting duplicate copies of the same pack through
	 * multiple alternates by only looking at packs whose names are not yet in
	 * this collection.
	 */
	private final Set<String> packsConsidered;

	private final MutableObjectId idBuffer = new MutableObjectId();

	private final MessageDigest objectDigest = Constants.newMessageDigest();

	/**
	 * Errors received while trying to obtain an object.
	 * <p>
	 * If the fetch winds up failing because we cannot locate a specific object
	 * then we need to report all errors related to that object back to the
	 * caller as there may be cascading failures.
	 */
	private final HashMap<ObjectId, List<Throwable>> fetchErrors;

	WalkFetchConnection(final WalkTransport wt, final WalkRemoteObjectDatabase w) {
		local = wt.local;
		objCheck = wt.isCheckFetchedObjects() ? new ObjectChecker() : null;

		remotes = new ArrayList<WalkRemoteObjectDatabase>();
		remotes.add(w);

		unfetchedPacks = new LinkedList<RemotePack>();
		packsConsidered = new HashSet<String>();

		noPacksYet = new LinkedList<WalkRemoteObjectDatabase>();
		noPacksYet.add(w);

		noAlternatesYet = new LinkedList<WalkRemoteObjectDatabase>();
		noAlternatesYet.add(w);

		fetchErrors = new HashMap<ObjectId, List<Throwable>>();

		revWalk = new RevWalk(local);
		treeWalk = new TreeWalk(local);
		COMPLETE = revWalk.newFlag("COMPLETE");
		IN_WORK_QUEUE = revWalk.newFlag("IN_WORK_QUEUE");
		LOCALLY_SEEN = revWalk.newFlag("LOCALLY_SEEN");

		localCommitQueue = new DateRevQueue();
		workQueue = new LinkedList<ObjectId>();
	}

	public boolean didFetchTestConnectivity() {
		return true;
	}

	@Override
	protected void doFetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have)
			throws TransportException {
		markLocalRefsComplete(have);
		queueWants(want);

		while (!monitor.isCancelled() && !workQueue.isEmpty()) {
			final ObjectId id = workQueue.removeFirst();
			if (!(id instanceof RevObject) || !((RevObject) id).has(COMPLETE))
				downloadObject(monitor, id);
			process(id);
		}
	}

	@Override
	public void close() {
		for (final RemotePack p : unfetchedPacks)
			p.tmpIdx.delete();
		for (final WalkRemoteObjectDatabase r : remotes)
			r.close();
	}

	private void queueWants(final Collection<Ref> want)
			throws TransportException {
		final HashSet<ObjectId> inWorkQueue = new HashSet<ObjectId>();
		for (final Ref r : want) {
			final ObjectId id = r.getObjectId();
			try {
				final RevObject obj = revWalk.parseAny(id);
				if (obj.has(COMPLETE))
					continue;
				if (inWorkQueue.add(id)) {
					obj.add(IN_WORK_QUEUE);
					workQueue.add(obj);
				}
			} catch (MissingObjectException e) {
				if (inWorkQueue.add(id))
					workQueue.add(id);
			} catch (IOException e) {
				throw new TransportException("Cannot read " + id.name(), e);
			}
		}
	}

	private void process(final ObjectId id) throws TransportException {
		final RevObject obj;
		try {
			if (id instanceof RevObject) {
				obj = (RevObject) id;
				if (obj.has(COMPLETE))
					return;
				revWalk.parse(obj);
			} else {
				obj = revWalk.parseAny(id);
				if (obj.has(COMPLETE))
					return;
			}
		} catch (IOException e) {
			throw new TransportException("Cannot read " + id.name(), e);
		}

		// We only care about traversal; disposing an object throws its
		// message buffer (if any) away but retains the links so we can
		// continue to navigate, but use less memory long-term.
		//
		obj.dispose();

		switch (obj.getType()) {
		case Constants.OBJ_BLOB:
			processBlob(obj);	
			break;
		case Constants.OBJ_TREE:
			processTree(obj);
			break;
		case Constants.OBJ_COMMIT:
			processCommit(obj);
			break;
		case Constants.OBJ_TAG:
			processTag(obj);
			break;
		default:
			throw new TransportException("Unknown object type " + id.name());
		}

		// If we had any prior errors fetching this object they are
		// now resolved, as the object was parsed successfully.
		//
		fetchErrors.remove(id.copy());
	}

	private void processBlob(final RevObject obj) throws TransportException {
		if (!local.hasObject(obj))
			throw new TransportException("Cannot read blob " + obj.name(),
					new MissingObjectException(obj, Constants.TYPE_BLOB));
		obj.add(COMPLETE);
	}

	private void processTree(final RevObject obj) throws TransportException {
		try {
			treeWalk.reset(obj);
			while (treeWalk.next()) {
				final FileMode mode = treeWalk.getFileMode(0);
				final int sType = mode.getObjectType();

				switch (sType) {
				case Constants.OBJ_BLOB:
				case Constants.OBJ_TREE:
					treeWalk.getObjectId(idBuffer, 0);
					needs(revWalk.lookupAny(idBuffer, sType));
					continue;

				default:
					if (FileMode.GITLINK.equals(mode))
						continue;
					treeWalk.getObjectId(idBuffer, 0);
					throw new CorruptObjectException("Invalid mode " + mode
							+ " for " + idBuffer.name() + " "
							+ treeWalk.getPathString() + " in "
							+ obj.getId().name() + ".");
				}
			}
		} catch (IOException ioe) {
			throw new TransportException("Cannot read tree " + obj.name(), ioe);
		}
		obj.add(COMPLETE);
	}

	private void processCommit(final RevObject obj) throws TransportException {
		final RevCommit commit = (RevCommit) obj;
		markLocalCommitsComplete(commit.getCommitTime());
		needs(commit.getTree());
		for (final RevCommit p : commit.getParents())
			needs(p);
		obj.add(COMPLETE);
	}

	private void processTag(final RevObject obj) {
		final RevTag tag = (RevTag) obj;
		needs(tag.getObject());
		obj.add(COMPLETE);
	}

	private void needs(final RevObject obj) {
		if (obj.has(COMPLETE))
			return;
		if (!obj.has(IN_WORK_QUEUE)) {
			obj.add(IN_WORK_QUEUE);
			workQueue.add(obj);
		}
	}

	private void downloadObject(final ProgressMonitor pm, final AnyObjectId id)
			throws TransportException {
		if (local.hasObject(id))
			return;

		for (;;) {
			// Try a pack file we know about, but don't have yet. Odds are
			// that if it has this object, it has others related to it so
			// getting the pack is a good bet.
			//
			if (downloadPackedObject(pm, id))
				return;

			// Search for a loose object over all alternates, starting
			// from the one we last successfully located an object through.
			//
			final String idStr = id.name();
			final String subdir = idStr.substring(0, 2);
			final String file = idStr.substring(2);
			final String looseName = subdir + "/" + file;

			for (int i = lastRemoteIdx; i < remotes.size(); i++) {
				if (downloadLooseObject(id, looseName, remotes.get(i))) {
					lastRemoteIdx = i;
					return;
				}
			}
			for (int i = 0; i < lastRemoteIdx; i++) {
				if (downloadLooseObject(id, looseName, remotes.get(i))) {
					lastRemoteIdx = i;
					return;
				}
			}

			// Try to obtain more pack information and search those.
			//
			while (!noPacksYet.isEmpty()) {
				final WalkRemoteObjectDatabase wrr = noPacksYet.removeFirst();
				final Collection<String> packNameList;
				try {
					pm.beginTask("Listing packs", ProgressMonitor.UNKNOWN);
					packNameList = wrr.getPackNames();
				} catch (IOException e) {
					// Try another repository.
					//
					recordError(id, e);
					continue;
				} finally {
					pm.endTask();
				}

				if (packNameList == null || packNameList.isEmpty())
					continue;
				for (final String packName : packNameList) {
					if (packsConsidered.add(packName))
						unfetchedPacks.add(new RemotePack(wrr, packName));
				}
				if (downloadPackedObject(pm, id))
					return;
			}

			// Try to expand the first alternate we haven't expanded yet.
			//
			Collection<WalkRemoteObjectDatabase> al = expandOneAlternate(id, pm);
			if (al != null && !al.isEmpty()) {
				for (final WalkRemoteObjectDatabase alt : al) {
					remotes.add(alt);
					noPacksYet.add(alt);
					noAlternatesYet.add(alt);
				}
				continue;
			}

			// We could not obtain the object. There may be reasons why.
			//
			List<Throwable> failures = fetchErrors.get(id.copy());
			final TransportException te;

			te = new TransportException("Cannot get " + id.name() + ".");
			if (failures != null && !failures.isEmpty()) {
				if (failures.size() == 1)
					te.initCause(failures.get(0));
				else
					te.initCause(new CompoundException(failures));
			}
			throw te;
		}
	}

	private boolean downloadPackedObject(final ProgressMonitor monitor,
			final AnyObjectId id) throws TransportException {
		// Search for the object in a remote pack whose index we have,
		// but whose pack we do not yet have.
		//
		final Iterator<RemotePack> packItr = unfetchedPacks.iterator();
		while (packItr.hasNext() && !monitor.isCancelled()) {
			final RemotePack pack = packItr.next();
			try {
				pack.openIndex(monitor);
			} catch (IOException err) {
				// If the index won't open its either not found or
				// its a format we don't recognize. In either case
				// we may still be able to obtain the object from
				// another source, so don't consider it a failure.
				//
				recordError(id, err);
				packItr.remove();
				continue;
			}

			if (monitor.isCancelled()) {
				// If we were cancelled while the index was opening
				// the open may have aborted. We can't search an
				// unopen index.
				//
				return false;
			}

			if (!pack.index.hasObject(id)) {
				// Not in this pack? Try another.
				//
				continue;
			}

			// It should be in the associated pack. Download that
			// and attach it to the local repository so we can use
			// all of the contained objects.
			//
			try {
				pack.downloadPack(monitor);
			} catch (IOException err) {
				// If the pack failed to download, index correctly,
				// or open in the local repository we may still be
				// able to obtain this object from another pack or
				// an alternate.
				//
				recordError(id, err);
				continue;
			} finally {
				// If the pack was good its in the local repository
				// and Repository.hasObject(id) will succeed in the
				// future, so we do not need this data anymore. If
				// it failed the index and pack are unusable and we
				// shouldn't consult them again.
				//
				pack.tmpIdx.delete();
				packItr.remove();
			}

			if (!local.hasObject(id)) {
				// What the hell? This pack claimed to have
				// the object, but after indexing we didn't
				// actually find it in the pack.
				//
				recordError(id, new FileNotFoundException("Object " + id.name()
						+ " not found in " + pack.packName + "."));
				continue;
			}

			// Complete any other objects that we can.
			//
			final Iterator<ObjectId> pending = swapFetchQueue();
			while (pending.hasNext()) {
				final ObjectId p = pending.next();
				if (pack.index.hasObject(p)) {
					pending.remove();
					process(p);
				} else {
					workQueue.add(p);
				}
			}
			return true;

		}
		return false;
	}

	private Iterator<ObjectId> swapFetchQueue() {
		final Iterator<ObjectId> r = workQueue.iterator();
		workQueue = new LinkedList<ObjectId>();
		return r;
	}

	private boolean downloadLooseObject(final AnyObjectId id,
			final String looseName, final WalkRemoteObjectDatabase remote)
			throws TransportException {
		try {
			final byte[] compressed = remote.open(looseName).toArray();
			verifyLooseObject(id, compressed);
			saveLooseObject(id, compressed);
			return true;
		} catch (FileNotFoundException e) {
			// Not available in a loose format from this alternate?
			// Try another strategy to get the object.
			//
			recordError(id, e);
			return false;
		} catch (IOException e) {
			throw new TransportException("Cannot download " + id.name(), e);
		}
	}

	private void verifyLooseObject(final AnyObjectId id, final byte[] compressed)
			throws IOException {
		final UnpackedObjectLoader uol;
		try {
			uol = new UnpackedObjectLoader(compressed);
		} catch (CorruptObjectException parsingError) {
			// Some HTTP servers send back a "200 OK" status with an HTML
			// page that explains the requested file could not be found.
			// These servers are most certainly misconfigured, but many
			// of them exist in the world, and many of those are hosting
			// Git repositories.
			//
			// Since an HTML page is unlikely to hash to one of our loose
			// objects we treat this condition as a FileNotFoundException
			// and attempt to recover by getting the object from another
			// source.
			//
			final FileNotFoundException e;
			e = new FileNotFoundException(id.name());
			e.initCause(parsingError);
			throw e;
		}

		objectDigest.reset();
		objectDigest.update(Constants.encodedTypeString(uol.getType()));
		objectDigest.update((byte) ' ');
		objectDigest.update(Constants.encodeASCII(uol.getSize()));
		objectDigest.update((byte) 0);
		objectDigest.update(uol.getCachedBytes());
		idBuffer.fromRaw(objectDigest.digest(), 0);

		if (!AnyObjectId.equals(id, idBuffer)) {
			throw new TransportException("Incorrect hash for " + id.name()
					+ "; computed " + idBuffer.name() + " as a "
					+ Constants.encodedTypeString(uol.getType()) + " from "
					+ compressed.length + " bytes.");
		}
		if (objCheck != null) {
			try {
				objCheck.check(uol.getType(), uol.getCachedBytes());
			} catch (CorruptObjectException e) {
				throw new TransportException("Invalid "
						+ Constants.encodedTypeString(uol.getType()) + " "
						+ id.name() + ":" + e.getMessage());
			}
		}
	}

	private void saveLooseObject(final AnyObjectId id, final byte[] compressed)
			throws IOException, ObjectWritingException {
		final File tmp;

		tmp = File.createTempFile("noz", null, local.getObjectsDirectory());
		try {
			final FileOutputStream out = new FileOutputStream(tmp);
			try {
				out.write(compressed);
			} finally {
				out.close();
			}
			tmp.setReadOnly();
		} catch (IOException e) {
			tmp.delete();
			throw e;
		}

		final File o = local.toFile(id);
		if (tmp.renameTo(o))
			return;

		// Maybe the directory doesn't exist yet as the object
		// directories are always lazily created. Note that we
		// try the rename first as the directory likely does exist.
		//
		o.getParentFile().mkdir();
		if (tmp.renameTo(o))
			return;

		tmp.delete();
		if (local.hasObject(id))
			return;
		throw new ObjectWritingException("Unable to store " + id.name() + ".");
	}

	private Collection<WalkRemoteObjectDatabase> expandOneAlternate(
			final AnyObjectId id, final ProgressMonitor pm) {
		while (!noAlternatesYet.isEmpty()) {
			final WalkRemoteObjectDatabase wrr = noAlternatesYet.removeFirst();
			try {
				pm.beginTask("Listing alternates", ProgressMonitor.UNKNOWN);
				Collection<WalkRemoteObjectDatabase> altList = wrr
						.getAlternates();
				if (altList != null && !altList.isEmpty())
					return altList;
			} catch (IOException e) {
				// Try another repository.
				//
				recordError(id, e);
			} finally {
				pm.endTask();
			}
		}
		return null;
	}

	private void markLocalRefsComplete(final Set<ObjectId> have) throws TransportException {
		for (final Ref r : local.getAllRefs().values()) {
			try {
				markLocalObjComplete(revWalk.parseAny(r.getObjectId()));
			} catch (IOException readError) {
				throw new TransportException("Local ref " + r.getName()
						+ " is missing object(s).", readError);
			}
		}
		for (final ObjectId id : have) {
			try {
				markLocalObjComplete(revWalk.parseAny(id));
			} catch (IOException readError) {
				throw new TransportException("Missing assumed "+id.name(), readError);
			}
		}
	}

	private void markLocalObjComplete(RevObject obj) throws IOException {
		while (obj.getType() == Constants.OBJ_TAG) {
			obj.add(COMPLETE);
			obj.dispose();
			obj = ((RevTag) obj).getObject();
			revWalk.parse(obj);
		}

		switch (obj.getType()) {
		case Constants.OBJ_BLOB:
			obj.add(COMPLETE);
			break;
		case Constants.OBJ_COMMIT:			
			pushLocalCommit((RevCommit) obj);
			break;
		case Constants.OBJ_TREE:
			markTreeComplete((RevTree) obj);
			break;
		}
	}

	private void markLocalCommitsComplete(final int until)
			throws TransportException {
		try {
			for (;;) {
				final RevCommit c = localCommitQueue.peek();
				if (c == null || c.getCommitTime() < until)
					return;
				localCommitQueue.next();

				markTreeComplete(c.getTree());
				for (final RevCommit p : c.getParents())
					pushLocalCommit(p);
			}
		} catch (IOException err) {
			throw new TransportException("Local objects incomplete.", err);
		}
	}

	private void pushLocalCommit(final RevCommit p)
			throws MissingObjectException, IOException {
		if (p.has(LOCALLY_SEEN))
			return;
		revWalk.parse(p);
		p.add(LOCALLY_SEEN);
		p.add(COMPLETE);
		p.carry(COMPLETE);
		p.dispose();
		localCommitQueue.add(p);
	}

	private void markTreeComplete(final RevTree tree) throws IOException {
		if (tree.has(COMPLETE))
			return;
		tree.add(COMPLETE);
		treeWalk.reset(tree);
		while (treeWalk.next()) {
			final FileMode mode = treeWalk.getFileMode(0);
			final int sType = mode.getObjectType();

			switch (sType) {
			case Constants.OBJ_BLOB:
				treeWalk.getObjectId(idBuffer, 0);
				revWalk.lookupAny(idBuffer, sType).add(COMPLETE);
				continue;

			case Constants.OBJ_TREE: {
				treeWalk.getObjectId(idBuffer, 0);
				final RevObject o = revWalk.lookupAny(idBuffer, sType);
				if (!o.has(COMPLETE)) {
					o.add(COMPLETE);
					treeWalk.enterSubtree();
				}
				continue;
			}
			default:
				if (FileMode.GITLINK.equals(mode))
					continue;
				treeWalk.getObjectId(idBuffer, 0);
				throw new CorruptObjectException("Invalid mode " + mode
						+ " for " + idBuffer.name() + " "
						+ treeWalk.getPathString() + " in " + tree.name() + ".");
			}
		}
	}

	private void recordError(final AnyObjectId id, final Throwable what) {
		final ObjectId objId = id.copy();
		List<Throwable> errors = fetchErrors.get(objId);
		if (errors == null) {
			errors = new ArrayList<Throwable>(2);
			fetchErrors.put(objId, errors);
		}
		errors.add(what);
	}

	private class RemotePack {
		final WalkRemoteObjectDatabase connection;

		final String packName;

		final String idxName;

		final File tmpIdx;

		PackIndex index;

		RemotePack(final WalkRemoteObjectDatabase c, final String pn) {
			final File objdir = local.getObjectsDirectory();
			connection = c;
			packName = pn;
			idxName = packName.substring(0, packName.length() - 5) + ".idx";

			String tn = idxName;
			if (tn.startsWith("pack-"))
				tn = tn.substring(5);
			if (tn.endsWith(".idx"))
				tn = tn.substring(0, tn.length() - 4);
			tmpIdx = new File(objdir, "walk-" + tn + ".walkidx");
		}

		void openIndex(final ProgressMonitor pm) throws IOException {
			if (index != null)
				return;
			if (tmpIdx.isFile()) {
				try {
					index = PackIndex.open(tmpIdx);
					return;
				} catch (FileNotFoundException err) {
					// Fall through and get the file.
				}
			}

			final WalkRemoteObjectDatabase.FileStream s;
			s = connection.open("pack/" + idxName);
			pm.beginTask("Get " + idxName.substring(0, 12) + "..idx",
					s.length < 0 ? ProgressMonitor.UNKNOWN
							: (int) (s.length / 1024));
			try {
				final FileOutputStream fos = new FileOutputStream(tmpIdx);
				try {
					final byte[] buf = new byte[2048];
					int cnt;
					while (!pm.isCancelled() && (cnt = s.in.read(buf)) >= 0) {
						fos.write(buf, 0, cnt);
						pm.update(cnt / 1024);
					}
				} finally {
					fos.close();
				}
			} catch (IOException err) {
				tmpIdx.delete();
				throw err;
			} finally {
				s.in.close();
			}
			pm.endTask();

			if (pm.isCancelled()) {
				tmpIdx.delete();
				return;
			}

			try {
				index = PackIndex.open(tmpIdx);
			} catch (IOException e) {
				tmpIdx.delete();
				throw e;
			}
		}

		void downloadPack(final ProgressMonitor monitor) throws IOException {
			final WalkRemoteObjectDatabase.FileStream s;
			final IndexPack ip;

			s = connection.open("pack/" + packName);
			ip = IndexPack.create(local, s.in);
			ip.setFixThin(false);
			ip.setObjectChecker(objCheck);
			ip.index(monitor);
			ip.renameAndOpenPack();
		}
	}
}
