package org.jdkxx.commons.filesystem.s3;

import lombok.extern.slf4j.Slf4j;
import org.jdkxx.commons.filesystem.*;
import org.jdkxx.commons.filesystem.api.ChannelPool;
import org.jdkxx.commons.filesystem.api.FileAttributes;
import org.jdkxx.commons.filesystem.api.FileSystemChannel;
import org.jdkxx.commons.filesystem.config.FileSystemEnvironment;
import org.jdkxx.commons.filesystem.config.Messages;
import org.jdkxx.commons.filesystem.config.OpenOptions;
import org.jdkxx.commons.filesystem.principal.DefaultGroupPrincipal;
import org.jdkxx.commons.filesystem.principal.DefaultUserPrincipal;
import org.jdkxx.commons.filesystem.utils.LinkOptionSupport;
import org.jdkxx.commons.filesystem.utils.URISupport;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class S3ObjectFileSystem extends AbstractFileSystem {
    private final Iterable<Path> rootDirectories;
    private final ChannelPool pool;
    private final AtomicBoolean readOnly = new AtomicBoolean(false);

    S3ObjectFileSystem(FileSystemProvider provider,
                       URI uri,
                       FileSystemEnvironment environment) throws IOException {
        super(provider, uri);
        this.pool = new S3ObjectChannelPool(uri.getHost(), environment);
        this.rootDirectories = Collections.singleton(new S3ObjectFilePath(this, File.separator, environment.getBucketName()));
    }

    @Override
    public boolean isReadOnly() {
        return readOnly.get();
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return rootDirectories;
    }

    @Override
    public @NotNull Path getPath(@NotNull String first, @NotNull String @NotNull ... more) {
        if (first.startsWith(getSeparator())) {
            first = first.substring(1);
        }
        String bucket = more.length > 0 ? more[0] : "";
        return new S3ObjectFilePath(this, first, bucket);
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return DefaultUserPrincipal.builder();
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        throw Messages.unsupportedOperation(FileSystem.class, "supportedFileAttributeViews");
    }

    @Override
    protected URI toUri(String path) {
        int pos = path.indexOf(File.separator);
        String bucket = path.substring(0, pos);
        return URISupport.create(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
                path.substring(pos),
                null, bucket);
    }

    boolean exists(S3ObjectFilePath path) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            return channel.exists(path.path());
        }
    }

    InputStream newInputStream(S3ObjectFilePath path, OpenOption... options) throws IOException {
        OpenOptions openOptions = OpenOptions.forNewInputStream(options);
        try (FileSystemChannel channel = pool.get()) {
            return newInputStream(channel, path, openOptions);
        }
    }

    private InputStream newInputStream(FileSystemChannel channel, S3ObjectFilePath path, OpenOptions options) throws IOException {
        assert options.read;
        return channel.newInputStream(path.path(), options);
    }

    OutputStream newOutputStream(S3ObjectFilePath path, OpenOption... options) throws IOException {
        OpenOptions openOptions = OpenOptions.forNewOutputStream(options);
        try (FileSystemChannel channel = pool.get()) {
            return channel.newOutputStream(path.path(), openOptions);
        }
    }

    SeekableByteChannel newByteChannel(S3ObjectFilePath path,
                                       Set<? extends OpenOption> options,
                                       FileAttribute<?>... attrs) throws IOException {
        if (attrs.length > 0) {
            throw Messages.fileSystemProvider().unsupportedCreateFileAttribute(attrs[0].name());
        }
        OpenOptions openOptions = OpenOptions.forNewByteChannel(options);
        try (FileSystemChannel channel = pool.get()) {
            if (openOptions.read) {
                FileAttributes attributes = findAttributes(channel, path);
                long size = attributes != null ? attributes.getSize() : 0;
                InputStream is = newInputStream(channel, path, openOptions);
                return SeekableFileByteChannel.builder()
                        .inputStream(is)
                        .size(size)
                        .build();
            }
            OutputStream out = newOutputStream(path, options.toArray(new OpenOption[]{}));
            return SeekableFileByteChannel.builder()
                    .outputStream(out)
                    .position(0)
                    .build();
        }
    }

    DirectoryStream<Path> newDirectoryStream(S3ObjectFilePath path,
                                             DirectoryStream.Filter<? super Path> filter) throws IOException {
        if (!path.isDirectory()) {
            throw new NotDirectoryException("Path - [" + path + "] not a folder.");
        }
        try (FileSystemChannel channel = pool.get()) {
            List<FileEntry> entries = listFileEntry(channel, path.path());
            return new S3DirectoryStream(path, entries, filter);
        }
    }

    void createDirectory(S3ObjectFilePath path, FileAttribute<?>... attrs) throws IOException {
        if (attrs.length > 0) {
            throw Messages.fileSystemProvider().unsupportedCreateFileAttribute(attrs[0].name());
        }
        try (FileSystemChannel channel = pool.get()) {
            channel.mkdir(path.path());
        }
    }

    void delete(S3ObjectFilePath path) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            channel.delete(path.path());
        }
    }

    @Override
    protected PosixFileAttributes readAttributes(Path path, LinkOption... options) throws IOException {
        boolean followLinks = LinkOptionSupport.followLinks(options);
        try (FileSystemChannel channel = pool.get()) {
            FileAttributes attributes = channel.readAttributes(((S3ObjectFilePath) path).path(), followLinks);
            return new S3FileAttributes(attributes);
        }
    }

    void copy(S3ObjectFilePath source, S3ObjectFilePath target, CopyOption... options) throws IOException {
        boolean sameFileSystem = haveSameFileSystem(source, target);
        CopyOptions copyOptions = CopyOptions.forCopy(options);
        try (FileSystemChannel channel = pool.get()) {
            FileAttributes sourceAttributes = channel.readAttributes(source.path(), false);
            if (!sameFileSystem) {
                copyAcrossFileSystems(channel, source, sourceAttributes, target, copyOptions);
                return;
            }
            try {
                if (source.path().equals(target.toRealPath().toString())) {
                    // non-op, don't do a thing as specified by Files.copy
                    return;
                }
            } catch (NoSuchFileException e) {
                // the target does not exist, or either path is an invalid link, ignore the error and continue
            }
            FileAttributes targetAttributes = findAttributes(channel, target);
            if (targetAttributes != null) {
                if (copyOptions.replaceExisting) {
                    channel.delete(target.path());
                } else {
                    throw new FileAlreadyExistsException(target.path());
                }
            }
            if (sourceAttributes.isDir()) {
                channel.mkdir(target.path());
            } else {
                try (FileSystemChannel channel2 = pool.getOrCreate()) {
                    copyFile(channel, source, channel2, target, copyOptions);
                }
            }
        }
    }

    void move(S3ObjectFilePath source, S3ObjectFilePath target, CopyOption... options) throws IOException {
        boolean sameFileSystem = haveSameFileSystem(source, target);
        CopyOptions copyOptions = CopyOptions.forMove(sameFileSystem, options);
        try (FileSystemChannel channel = pool.get()) {
            if (!sameFileSystem) {
                FileAttributes sourceAttributes = channel.readAttributes(source.path(), false);
                copyAcrossFileSystems(channel, source, sourceAttributes, target, copyOptions);
                channel.delete(source.path());
                return;
            }
            try {
                if (isSameFile(source, target)) {
                    // non-op, don't do a thing as specified by Files.move
                    return;
                }
            } catch (NoSuchFileException e) {
                // the source or target does not exist, or either path is an invalid link
                // call getAttributes to ensure the source file exists
                // ignore any error to target or if the source link is invalid
                channel.readAttributes(source.path(), false);
            }

            if (source.toAbsolutePath().getParent() == null) {
                // cannot move or rename the root
                throw new DirectoryNotEmptyException(source.path());
            }

            FileAttributes targetAttributes = findAttributes(channel, target);
            if (copyOptions.replaceExisting && targetAttributes != null) {
                channel.delete(target.path());
            }
            channel.rename(source.path(), target.path());
        }
    }

    boolean isSameFile(S3ObjectFilePath path, S3ObjectFilePath other) throws IOException {
        if (!haveSameFileSystem(path, other)) {
            return false;
        }
        if (path.equals(other)) {
            return true;
        }
        return path.toRealPath().equals(other.toRealPath());
    }

    void checkAccess(S3ObjectFilePath path, AccessMode... modes) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            FileAttributes attributes = channel.readAttributes(path.path(), true);
            for (AccessMode mode : modes) {
                if (!hasAccess(attributes, mode)) {
                    throw new AccessDeniedException(path.path());
                }
            }
        }
    }

    void setAccessControlList(S3ObjectFilePath path, List<AclEntry> acl) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            channel.chmod(path.path(), acl);
        }
    }

    void setTimes(S3ObjectFilePath path, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        if (path.isDirectory()) {
            throw new IllegalArgumentException("Path - [" + path + "] is a directory, expected a file path.");
        }

        if (lastAccessTime != null) {
            setLastAccessTime(path, lastAccessTime, false);
        }
        if (createTime != null) {
            setCreateTime(path, createTime, false);
        }
        if (lastModifiedTime != null) {
            setLastModifiedTime(path, lastModifiedTime, false);
        }
    }

    void setLastModifiedTime(S3ObjectFilePath path, FileTime lastModifiedTime, boolean followLinks) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            if (followLinks) {
                path = (S3ObjectFilePath) path.toRealPath();
            }
            // times are in seconds
            channel.setMtime(path.path(), lastModifiedTime.to(TimeUnit.MILLISECONDS));
        }
    }

    void setLastAccessTime(S3ObjectFilePath path, FileTime lastModifiedTime, boolean followLinks) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            if (followLinks) {
                path = (S3ObjectFilePath) path.toRealPath();
            }
            // times are in seconds
            channel.setAtime(path.path(), lastModifiedTime.to(TimeUnit.MILLISECONDS));
        }
    }

    void setCreateTime(S3ObjectFilePath path, FileTime createTime, boolean followLinks) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            if (followLinks) {
                path = (S3ObjectFilePath) path.toRealPath();
            }
            // times are in seconds
            channel.setCtime(path.path(), createTime.to(TimeUnit.MILLISECONDS));
        }
    }

    void setOwner(S3ObjectFilePath path, UserPrincipal owner) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            channel.chown(path.path(), owner.getName());
        }
    }

    void setPermissions(S3ObjectFilePath path, Set<PosixFilePermission> permissions) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            UserPrincipal owner = path.readAttributes().owner();
            AclEntry.Builder builder = AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                    .setPrincipal(owner);
            EnumSet<AclEntryPermission> perms = EnumSet.noneOf(AclEntryPermission.class);
            for (PosixFilePermission permission : permissions) {
                switch (permission) {
                    case OWNER_READ:
                        perms.add(AclEntryPermission.READ_DATA);
                        break;
                    case OWNER_WRITE:
                        perms.add(AclEntryPermission.WRITE_DATA);
                }
            }
            builder.setPermissions(perms);
            channel.chmod(path.path(), Collections.singletonList(builder.build()));
        }
    }

    private boolean hasAccess(FileAttributes attrs, AccessMode mode) {
        Set<PosixFilePermission> permissions = attrs.getPermissions();
        switch (mode) {
            case READ:
                return permissions.contains(PosixFilePermission.OWNER_READ);
            case WRITE:
                return permissions.contains(PosixFilePermission.OWNER_WRITE);
            default:
                return false;
        }
    }

    private boolean haveSameFileSystem(S3ObjectFilePath path, S3ObjectFilePath other) {
        return path.getFileSystem() == other.getFileSystem();
    }

    private void copyAcrossFileSystems(FileSystemChannel sourceChannel,
                                       FilePath source,
                                       FileAttributes sourceAttributes,
                                       FilePath target, CopyOptions options) throws IOException {
        S3ObjectFileSystem targetFileSystem = (S3ObjectFileSystem) target.getFileSystem();
        try (FileSystemChannel targetChannel = targetFileSystem.pool.getOrCreate()) {
            FileAttributes targetAttributes = findAttributes(targetChannel, target);
            if (targetAttributes != null) {
                if (options.replaceExisting) {
                    targetChannel.delete(target.path());
                } else {
                    throw new FileAlreadyExistsException(target.path());
                }
            }
            if (sourceAttributes.isDir()) {
                targetChannel.mkdir(target.path());
            } else {
                copyFile(sourceChannel, source, targetChannel, target, options);
            }
        }
    }

    private FileAttributes findAttributes(FileSystemChannel channel,
                                          FilePath path) throws IOException {
        try {
            return channel.readAttributes(path.path(), false);
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    Map<String, Object> readAttributes(S3ObjectFilePath path, String attributes, LinkOption... options) throws IOException {
        return super.readAttributes(path, attributes, options);
    }

    @SuppressWarnings("unchecked")
    void setAttribute(S3ObjectFilePath path, String attributes, Object value, LinkOption... options) throws IOException {
        String prefix = getViewPrefix(attributes);
        if (!attributes.startsWith(prefix)) {
            attributes = prefix + ":" + attributes;
        }
        boolean followLinks = LinkOptionSupport.followLinks(options);
        switch (attributes) {
            case "basic:lastModifiedTime":
            case "posix:lastModifiedTime":
                setLastModifiedTime(path, (FileTime) value, followLinks);
                break;
            case "basic:lastAccessTime":
            case "posix:lastAccessTime":
                setLastAccessTime(path, (FileTime) value, followLinks);
                break;
            case "basic:creationTime":
            case "posix:creationTime":
                setCreateTime(path, (FileTime) value, followLinks);
                break;
            case "owner:owner":
            case "posix:owner":
                setOwner(path, (UserPrincipal) value);
                break;
            case "posix:group":
                setOwner(path, (GroupPrincipal) value);
                break;
            case "posix:permissions":
                Set<PosixFilePermission> permissions = (Set<PosixFilePermission>) value;
                setPermissions(path, permissions);
                break;
            default:
                throw Messages.fileSystemProvider().unsupportedFileAttribute(attributes);
        }
    }

    private static final class S3DirectoryStream extends AbstractDirectoryStream<Path> {
        private final S3ObjectFilePath path;
        private final List<FileEntry> entries;
        private Iterator<FileEntry> iterator;

        private S3DirectoryStream(S3ObjectFilePath path, List<FileEntry> entries, Filter<? super Path> filter) {
            super(filter);
            this.path = path;
            this.entries = entries;
        }

        @Override
        protected void setupIteration() {
            iterator = entries.iterator();
        }

        @Override
        protected Path getNext() throws IOException {
            return iterator.hasNext() ?
                    new S3ObjectFilePath((S3ObjectFileSystem) path.getFileSystem(), iterator.next().getLongName(), path.bucket()) : null;
        }
    }

    private static final class S3FileAttributes implements PosixFileAttributes {
        private final FileAttributes attributes;

        S3FileAttributes(FileAttributes attributes) {
            this.attributes = attributes;
        }

        @Override
        public UserPrincipal owner() {
            return DefaultUserPrincipal.builder()
                    .name(getOwner())
                    .build();
        }

        @Override
        public GroupPrincipal group() {
            return DefaultGroupPrincipal.builder()
                    .group(getOwner())
                    .build();
        }

        private String getOwner() {
            return attributes.as(S3ObjectFileAttributes.class).getOwner().getId();
        }

        @Override
        public Set<PosixFilePermission> permissions() {
            return attributes.getPermissions();
        }

        @Override
        public FileTime lastModifiedTime() {
            return FileTime.from(attributes.getMTime(), TimeUnit.MILLISECONDS);
        }

        @Override
        public FileTime lastAccessTime() {
            if (attributes.getLastAccessTime() > 0) {
                return FileTime.from(attributes.getLastAccessTime(), TimeUnit.MILLISECONDS);
            }
            return null;
        }

        @Override
        public FileTime creationTime() {
            if (attributes.getCreationTime() > 0) {
                return FileTime.from(attributes.getCreationTime(), TimeUnit.MILLISECONDS);
            }
            return null;
        }

        @Override
        public boolean isRegularFile() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return attributes.isDir();
        }

        @Override
        public boolean isSymbolicLink() {
            return false;
        }

        @Override
        public boolean isOther() {
            return !(isRegularFile() || isDirectory() || isSymbolicLink());
        }

        @Override
        public long size() {
            return attributes.getSize();
        }

        @Override
        public Object fileKey() {
            return null;
        }
    }
}
