package edu.nyu.cs.javagit.client;

import edu.nyu.cs.javagit.api.Ref;
import edu.nyu.cs.javagit.api.JavaGitException;

import java.io.File;
import java.io.IOException;

public interface IGitRemote
{
    void remote(File repoDirectory, Ref remoteBranch, String remoteUrl)
            throws JavaGitException, IOException;
}
