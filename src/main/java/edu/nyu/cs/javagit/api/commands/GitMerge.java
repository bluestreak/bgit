package edu.nyu.cs.javagit.api.commands;

import edu.nyu.cs.javagit.api.JavaGitException;
import edu.nyu.cs.javagit.api.Ref;
import edu.nyu.cs.javagit.utilities.CheckUtilities;
import edu.nyu.cs.javagit.client.IClient;
import edu.nyu.cs.javagit.client.ClientManager;
import edu.nyu.cs.javagit.client.IGitInit;
import edu.nyu.cs.javagit.client.IGitMerge;

import java.io.File;
import java.io.IOException;

public class GitMerge
{

	public void merge(File repositoryPath, Ref branch) throws JavaGitException, IOException
    {
		CheckUtilities.checkNullArgument(repositoryPath, "repository");

	    IClient client = ClientManager.getInstance().getPreferredClient();
	    IGitMerge gitMerge = client.getGitMergeInstance();
	    gitMerge.merge(repositoryPath,branch);
	}
	
}
