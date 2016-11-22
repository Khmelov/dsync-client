package com.yet.dsync.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import com.yet.dsync.dao.ConfigDao;
import com.yet.dsync.exception.DSyncClientException;
import com.yet.dsync.util.Config;

public class LocalFolderService {

    private ConfigDao configDao;
    
    private File localDir;
    
    public LocalFolderService(ConfigDao configDao) {
        this.configDao = configDao;
    }

    public void setupLocalFolder() {
        System.out.print("Input local folder to use for Dropbox: ");

        try {
            String folder = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();

            File dir = new File(folder);
            if (dir.exists()) {
                System.out.print("WARNING! The folder exists. All its contents will be deleted. Continue? [y/n] ");
                char answer = (char) new BufferedReader(new InputStreamReader(System.in)).read();
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
            System.out.println("Local folder does not exists + " + localDir.getAbsolutePath());
            setupLocalFolder();
        }
    }
    
    public void createFolder(String path) {
        File folder = new File(localDir.getAbsolutePath() + path);
        
        if ( ! folder.exists() ) {
            if ( ! folder.mkdirs() ) {
                throw new DSyncClientException("Failed in creating directories at " + folder.getAbsolutePath());
            }
        }
    }
    
    public void deleteFileOrFolder(String path) {
        File file = buildFileObject(path);
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
    
    public File buildFileObject(String path) {
        return new File(localDir.getAbsolutePath() + path);
    }

}
