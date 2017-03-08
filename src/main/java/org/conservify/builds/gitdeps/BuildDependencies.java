package org.conservify.builds.gitdeps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class BuildDependencies {
    private static final Logger logger = LoggerFactory.getLogger(BuildDependencies.class);
    private List<String> searchPaths;

    public BuildDependencies(List<String> searchPaths) {
        this.searchPaths = searchPaths;
    }

    public File locate(String name) {
        logger.info("Searching for {}...", name);

        for (String searchPath : searchPaths) {
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
