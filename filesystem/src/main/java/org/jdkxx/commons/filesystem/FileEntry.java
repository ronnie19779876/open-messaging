package org.jdkxx.commons.filesystem;

import lombok.Data;
import org.jdkxx.commons.filesystem.api.FileAttributes;

@Data
public abstract class FileEntry implements Comparable<FileEntry> {
    protected String fileName;
    protected String longName;

    public FileEntry(String path) {
        this.fileName = getFilename(path);
        this.longName = path;
    }

    private String getFilename(String path) {
        if (path == null) {
            return null;
        }
        final int index = path.lastIndexOf("/");
        return path.substring(index + 1);
    }

    @Override
    public int compareTo(FileEntry other) {
        return fileName.compareTo(other.fileName);
    }

    public abstract FileAttributes attributes();
}
