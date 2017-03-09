package org.conservify.builds.gitdeps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class BuildDependencies {
    private static final Logger logger = LoggerFactory.getLogger(BuildDependencies.class);
    private final GitDependencies gitDependencies = new GitDependencies();

    public File locate(File[] searchPaths, String name) {
        logger.info("Searching for {}...", name);

        try {
            URL url = new URL(name);
            return gitDependencies.locate(url);
        } catch (MalformedURLException e) {
            for (File searchPath : searchPaths) {
                logger.info("Checking {}...", searchPath);
                File libraryDirectory = new File(searchPath, name);
                if (libraryDirectory.exists() && libraryDirectory.isDirectory()) {
                    logger.info("Found {}", libraryDirectory);
                    return libraryDirectory;
                }
            }

            return null;
        }
    }
}
