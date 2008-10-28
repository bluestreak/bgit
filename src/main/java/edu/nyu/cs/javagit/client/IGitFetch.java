package edu.nyu.cs.javagit.client;

import edu.nyu.cs.javagit.api.Ref;
import edu.nyu.cs.javagit.api.JavaGitException;

import java.io.File;
import java.io.IOException;

public interface IGitFetch
{
    void fetch(File repoDirectory)
            throws JavaGitException, IOException;
}
