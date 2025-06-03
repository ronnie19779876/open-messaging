package org.jdkxx.commons.filesystem.s3;

import org.jdkxx.commons.filesystem.AbstractFilePath;
import org.jdkxx.commons.filesystem.FilePath;
import org.jdkxx.commons.filesystem.config.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class S3FilePath extends AbstractFilePath {
    private final S3FileSystem fs;

    public S3FilePath(S3FileSystem fs, String path) {
        this(fs, path, false);
    }

    private S3FilePath(S3FileSystem fs, String path, boolean normalized) {
        super(path, normalized);
        this.fs = Objects.requireNonNull(fs);
    }

    @Override
    public @NotNull FileSystem getFileSystem() {
        return fs;
    }

    @Override
    public Path getRoot() {
        return fs.getRoot();
    }

    @Override
    public Path getFileName() {
        String path = this.path();
        final int lastUnixPos = path.lastIndexOf(fs.getSeparator());
        path = path.substring(lastUnixPos + 1);
        return createPath(path);
    }

    @Override
    public Path getParent() {
        FilePath parent = (FilePath) super.getParent();
        if (parent == null) {
            return createPath("");
        }
        try {
            Path directory = createPath(parent.path());
            directory = directory.resolve(fs.getSeparator());
            if (((S3FilePath) directory).isDirectory()) {
                return directory;
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException(ex);
        }
        return parent;
    }

    @Override
    public @NotNull URI toUri() {
        return fs.toUri(this);
    }

    @Override
    public @NotNull Path toAbsolutePath() {
        return fs.toAbsolutePath(this);
    }

    @Override
    public @NotNull Path toRealPath(@NotNull LinkOption... options) throws IOException {
        return fs.toRealPath(this, options);
    }

    @Override
    public @NotNull WatchKey register(@NotNull WatchService watcher,
                                      WatchEvent.@NotNull Kind<?> @NotNull [] events,
                                      @NotNull WatchEvent.Modifier @NotNull ... modifiers) throws IOException {
        throw Messages.unsupportedOperation(Path.class, "register");
    }

    @Override
    protected FilePath createPath(String path) {
        return new S3FilePath(fs, path, true);
    }

    boolean isSameFile(Path other) throws IOException {
        if (this.equals(other)) {
            return true;
        }
        if (other == null || getFileSystem() != other.getFileSystem()) {
            return false;
        }
        return fs.isSameFile(this, (S3FilePath) other);
    }

    boolean exists() {
        try {
            return this.fs.exists(this);
        } catch (IOException ex) {
            return false;
        }
    }

    void setAccessControlList(List<AclEntry> acl) throws IOException {
        fs.setAccessControlList(this, acl);
    }

    void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        fs.setTimes(this, lastModifiedTime, lastAccessTime, createTime);
    }

    void checkAccess(AccessMode... modes) throws IOException {
        fs.checkAccess(this, modes);
    }

    void setOwner(UserPrincipal owner) throws IOException {
        fs.setOwner(this, owner);
    }

    void setGroup(GroupPrincipal group) throws IOException {
        fs.setOwner(this, group);
    }

    void setPermissions(Set<PosixFilePermission> permissions) throws IOException {
        fs.setPermissions(this, permissions);
    }

    boolean isDirectory() throws IOException {
        PosixFileAttributes attributes = readAttributes();
        return attributes.isDirectory();
    }

    PosixFileAttributes readAttributes(LinkOption... options) throws IOException {
        return fs.readAttributes(this, options);
    }

    Map<String, Object> readAttributes(String attributes, LinkOption... options) throws IOException {
        return fs.readAttributes(this, attributes, options);
    }

    void setAttribute(String attribute, Object value, LinkOption... options) throws IOException {
        fs.setAttribute(this, attribute, value, options);
    }

    InputStream newInputStream(OpenOption... options) throws IOException {
        return fs.newInputStream(this, options);
    }

    OutputStream newOutputStream(OpenOption... options) throws IOException {
        return fs.newOutputStream(this, options);
    }

    SeekableByteChannel newByteChannel(Set<? extends OpenOption> options,
                                       FileAttribute<?>... attrs) throws IOException {
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

    void copy(S3FilePath target, CopyOption... options) throws IOException {
        fs.copy(this, target, options);
    }

    void move(S3FilePath target, CopyOption... options) throws IOException {
        fs.move(this, target, options);
    }
}
