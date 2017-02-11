/*
 * Copyright (c) 2017 Yuriy Tkach
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.yet.dsync.service;

import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.util.Config;
import com.yet.dsync.util.PathUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

public class LocalFolderService {

    private static final Logger LOG = LogManager.getLogger(LocalFolderService.class);

    private final ConfigDao configDao;
    private final GlobalOperationsTracker globalOperationsTracker;

    private File localDir;

    public LocalFolderService(final ConfigDao configDao,
                              final GlobalOperationsTracker globalOperationsTracker) {
        this.configDao = configDao;
        this.globalOperationsTracker = globalOperationsTracker;
    }

    public void setupLocalFolder() {
        System.out.print("Input local folder to use for Dropbox: ");

        try {
            final String folder = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();

            final File dir = new File(folder);
            if (dir.exists()) {
                System.out.print("WARNING! The folder exists. All its contents will be deleted. Continue? [y/n] ");
                final char answer = (char) new BufferedReader(new InputStreamReader(System.in)).read();
                System.out.println();
                if (answer == 'y') {
                    FileUtils.deleteDirectory(dir);
                } else {
                    throw new DSyncClientException("Cancelled");
                }
            }
            if (dir.mkdirs()) {
                System.out.println("Created directory " + dir.getAbsolutePath());

                configDao.write(Config.LOCAL_DIR, dir.getAbsolutePath());
            } else {
                System.out.println("FAILED to create directory");
            }
        } catch (IOException e) {
            throw new DSyncClientException(e);
        }
    }

    public void checkOrSetupLocalDir() {
        String localDirPath = configDao.read(Config.LOCAL_DIR);
        if (StringUtils.isBlank(localDirPath)) {
            setupLocalFolder();
            localDirPath = configDao.read(Config.LOCAL_DIR);
        }

        localDir = new File(localDirPath);
        if ( ! localDir.exists() ) {
            LOG.info("Local folder does not exist: {}", ()->localDir.getAbsolutePath());
            setupLocalFolder();
            localDirPath = configDao.read(Config.LOCAL_DIR);
            localDir = new File(localDirPath);
        }

        LOG.debug("Local folder: {}", ()->localDir.getAbsolutePath());
    }

    public void createFolder(final String path) {
        final File folder = new File(localDir.getAbsolutePath() + path);

        if ( ! folder.exists() ) {
            if ( ! folder.mkdirs() ) {
                throw new DSyncClientException("Failed in creating directories at " + folder.getAbsolutePath());
            }
        }
    }

    public synchronized void deleteFileOrFolder(final String path) {
        final File file = buildFileObject(path);
        if (file.exists()) {
            if (file.isDirectory()) {
                try {
                    FileUtils.deleteDirectory(file);
                } catch (Exception e) {
                    throw new DSyncClientException("Failed to delete directory: " + file.getAbsolutePath(), e);
                }
            } else {
                if ( !file.delete() ) {
                    throw new DSyncClientException("Failed to delete file: " + file.getAbsolutePath());
                }
            }
        }
    }

    public File buildFileObject(final String path) {
        return new File(localDir.getAbsolutePath() + path);
    }

    public String extractDropboxPath(final Path path) {
        return PathUtil.extractDropboxPath(localDir, path);
    }

    public Runnable createFolderWatchingThread(final LocalFolderChange changeListener) {
        return new LocalFolderWatching(localDir.getAbsolutePath(), changeListener, globalOperationsTracker);
    }

}
