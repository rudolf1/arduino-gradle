package org.conservify.builds.gitdeps;

import org.apache.commons.io.FilenameUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.FetchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class GitDependencies {
    private static final Logger logger = LoggerFactory.getLogger(GitDependencies.class);

    public File locate(URL url) {
        try {
            File pathAsFileName = new File(url.getPath());
            File rootDir = new File("gitdeps").getAbsoluteFile();
            File workingCopy = new File(rootDir, FilenameUtils.removeExtension(pathAsFileName.getName()));
            File gitDir = new File(workingCopy, ".git");

            if (!workingCopy.exists() || !gitDir.exists()) {
                logger.info("Cloning {}", url);
                Git git = Git.cloneRepository()
                        .setURI(url.toString())
                        .setDirectory(workingCopy)
                        .call();
                git.close();
            }

            logger.info("Fetching {}", url);
            Git git = Git.open(workingCopy);
            FetchResult fetch = git.fetch().setCheckFetchedObjects(true).call();
            git.close();

            return workingCopy;
        } catch (GitAPIException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
