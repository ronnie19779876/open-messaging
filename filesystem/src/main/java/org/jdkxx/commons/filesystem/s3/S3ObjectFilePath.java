package org.jdkxx.commons.filesystem.s3;

import org.apache.commons.lang3.StringUtils;
import org.jdkxx.commons.filesystem.AbstractFilePath;
import org.jdkxx.commons.filesystem.FilePath;
import org.jdkxx.commons.filesystem.config.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.File;
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

public class S3ObjectFilePath extends AbstractFilePath {
    private final S3ObjectFileSystem fs;
    private final String bucket;

    public S3ObjectFilePath(S3ObjectFileSystem fs, String path, String bucket) {
        this(fs, path, false, bucket);
    }

    private S3ObjectFilePath(S3ObjectFileSystem fs, String path, boolean normalized, String bucket) {
        super(path, normalized);
        this.fs = Objects.requireNonNull(fs);
        this.bucket = bucket;
        initOffsets();
    }

    @Override
    public @NotNull FileSystem getFileSystem() {
        return fs;
    }

    @Override
    public Path getRoot() {
        String path = this.path();
        if (offsets.length > 1) {
            path = StringUtils.substring(path, offsets[0], offsets[1]);
        } else if (offsets.length == 0) {
            path = File.separator;
        }
        return new S3ObjectFilePath(fs, path, false, bucket);
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
        Path directory = createPath(parent.path());
        directory = directory.resolve(File.separator);
        return directory;
    }

    @Override
    public @NotNull URI toUri() {
        String path = String.format("%s%s%s", this.bucket, File.separator, this.path());
        return fs.toUri(path);
    }

    @Override
    public @NotNull Path toAbsolutePath() {
        return new S3ObjectFilePath(fs, this.path(), false, bucket);
    }

    @Override
    public @NotNull Path toRealPath(@NotNull LinkOption @NotNull ... options) throws IOException {
        return toAbsolutePath();
    }

    @Override
    public @NotNull WatchKey register(@NotNull WatchService watcher,
                                      WatchEvent.@NotNull Kind<?> @NotNull [] events,
                                      @NotNull WatchEvent.Modifier @NotNull ... modifiers) throws IOException {
        throw Messages.unsupportedOperation(Path.class, "register");
    }

    @Override
    protected FilePath createPath(String path) {
        return new S3ObjectFilePath(fs, path, false, bucket);
    }

    @Override
    public boolean isAbsolute() {
        return true;
    }

    @Override
    public @NotNull Path relativize(@NotNull Path other) {
        throw Messages.unsupportedOperation(Path.class, "relativize");
    }

    @Override
    public @NotNull Path normalize() {
        throw Messages.unsupportedOperation(Path.class, "normalize");
    }

    String bucket() {
        return bucket;
    }

    boolean isSameFile(Path other) throws IOException {
        if (this.equals(other)) {
            return true;
        }
        if (other == null || getFileSystem() != other.getFileSystem()) {
            return false;
        }
        return fs.isSameFile(this, (S3ObjectFilePath) other);
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

    void copy(S3ObjectFilePath target, CopyOption... options) throws IOException {
        fs.copy(this, target, options);
    }

    void move(S3ObjectFilePath target, CopyOption... options) throws IOException {
        fs.move(this, target, options);
    }
}
