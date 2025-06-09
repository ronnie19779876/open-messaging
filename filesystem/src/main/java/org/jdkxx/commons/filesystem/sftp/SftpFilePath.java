package org.jdkxx.commons.filesystem.sftp;

import lombok.extern.slf4j.Slf4j;
import org.jdkxx.commons.filesystem.AbstractFilePath;
import org.jdkxx.commons.filesystem.config.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
public class SftpFilePath extends AbstractFilePath {
    private final SftpFileSystem fs;

    SftpFilePath(SftpFileSystem fs, String path) {
        super(path, true);
        this.fs = Objects.requireNonNull(fs);
        initOffsets();
    }

    @Override
    public @NotNull FileSystem getFileSystem() {
        return fs;
    }

    @Override
    public @NotNull URI toUri() {
        return fs.toUri(this);
    }

    @Override
    public @NotNull SftpFilePath toAbsolutePath() {
        return fs.toAbsolutePath(this);
    }

    @Override
    public @NotNull SftpFilePath toRealPath(LinkOption @NotNull ... options) throws IOException {
        return fs.toRealPath(this, options);
    }

    @Override
    public @NotNull WatchKey register(@NotNull WatchService watcher,
                                      WatchEvent.Kind<?> @NotNull [] events,
                                      WatchEvent.Modifier @NotNull ... modifiers) throws IOException {
        throw Messages.unsupportedOperation(Path.class, "register");
    }


    InputStream newInputStream(OpenOption... options) throws IOException {
        return fs.newInputStream(this, options);
    }

    OutputStream newOutputStream(OpenOption... options) throws IOException {
        return fs.newOutputStream(this, options);
    }

    SeekableByteChannel newByteChannel(Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return fs.newByteChannel(this, options, attrs);
    }

    DirectoryStream<Path> newDirectoryStream(DirectoryStream.Filter<? super Path> filter) throws IOException {
        return fs.newDirectoryStream(this, filter);
    }

    void createDirectory(FileAttribute<?>... attrs) throws IOException {
        fs.createDirectory(this, attrs);
    }

    void delete() throws IOException {
        fs.delete(this);
    }

    SftpFilePath readSymbolicLink() throws IOException {
        return fs.readSymbolicLink(this);
    }

    void copy(SftpFilePath target, CopyOption... options) throws IOException {
        fs.copy(this, target, options);
    }

    void move(SftpFilePath target, CopyOption... options) throws IOException {
        fs.move(this, target, options);
    }

    boolean isSameFile(Path other) throws IOException {
        if (this.equals(other)) {
            return true;
        }
        if (other == null || getFileSystem() != other.getFileSystem()) {
            return false;
        }
        return fs.isSameFile(this, (SftpFilePath) other);
    }

    boolean isHidden() throws IOException {
        return fs.isHidden(this);
    }

    FileStore getFileStore() throws IOException {
        return fs.getFileStore(this);
    }

    void checkAccess(AccessMode... modes) throws IOException {
        fs.checkAccess(this, modes);
    }

    PosixFileAttributes readAttributes(LinkOption... options) throws IOException {
        return fs.readAttributes(this, options);
    }

    Map<String, Object> readAttributes(String attributes, LinkOption... options) throws IOException {
        return fs.readAttributes(this, attributes, options);
    }

    void setOwner(UserPrincipal owner) throws IOException {
        fs.setOwner(this, owner);
    }

    void setGroup(GroupPrincipal group) throws IOException {
        fs.setGroup(this, group);
    }

    void setPermissions(Set<PosixFilePermission> permissions) throws IOException {
        fs.setPermissions(this, permissions);
    }

    void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        fs.setTimes(this, lastModifiedTime, lastAccessTime, createTime);
    }

    void setAttribute(String attribute, Object value, LinkOption... options) throws IOException {
        fs.setAttribute(this, attribute, value, options);
    }

    @Override
    protected SftpFilePath createPath(String path) {
        return new SftpFilePath(fs, path);
    }

    long getTotalSpace() throws IOException {
        return fs.getTotalSpace(this);
    }

    long getUsableSpace() throws IOException {
        return fs.getUsableSpace(this);
    }

    long getUnallocatedSpace() throws IOException {
        return fs.getUnallocatedSpace(this);
    }
}
