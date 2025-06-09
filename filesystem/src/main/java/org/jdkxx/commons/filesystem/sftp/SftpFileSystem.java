package org.jdkxx.commons.filesystem.sftp;

import com.jcraft.jsch.SftpStatVFS;
import lombok.extern.slf4j.Slf4j;
import org.jdkxx.commons.filesystem.*;
import org.jdkxx.commons.filesystem.api.FileAttributes;
import org.jdkxx.commons.filesystem.api.FileSystemChannel;
import org.jdkxx.commons.filesystem.config.FileSystemEnvironment;
import org.jdkxx.commons.filesystem.config.Messages;
import org.jdkxx.commons.filesystem.config.OpenOptions;
import org.jdkxx.commons.filesystem.principal.DefaultGroupPrincipal;
import org.jdkxx.commons.filesystem.principal.DefaultUserPrincipal;
import org.jdkxx.commons.filesystem.utils.LinkOptionSupport;
import org.jdkxx.commons.filesystem.utils.PosixFilePermissionSupport;
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

@Slf4j
public class SftpFileSystem extends AbstractFileSystem {
    private final Iterable<FileStore> fileStores;
    private final Iterable<Path> rootDirectories;
    private final String defaultDirectory;

    SftpFileSystem(FileSystemProvider provider, URI uri, FileSystemEnvironment environment) throws IOException {
        super(provider, uri);
        this.rootDirectories = Collections.singleton(new SftpFilePath(this, File.separator));
        this.fileStores = Collections.singleton(new SftpFileStore(new SftpFilePath(this, File.separator)));
        this.pool = new SshChannelPool(uri.getHost(), uri.getPort(), environment);
        try (FileSystemChannel channel = this.pool.get()) {
            this.defaultDirectory = channel.pwd();
        }
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return rootDirectories;
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        // TODO: get the actual file stores, instead of only returning the root file store
        return fileStores;
    }

    @Override
    public @NotNull Path getPath(@NotNull String first, String... more) {
        StringBuilder sb = new StringBuilder(first);
        for (String s : more) {
            sb.append(File.separator).append(s);
        }
        return new SftpFilePath(this, sb.toString());
    }

    URI toUri(SftpFilePath path) {
        SftpFilePath absPath = (SftpFilePath) toAbsolutePath(path).normalize();
        return toUri(absPath.path());
    }

    SftpFilePath toAbsolutePath(SftpFilePath path) {
        if (path.isAbsolute()) {
            return path;
        }
        String absPath = String.format("%s/%s", defaultDirectory, path.path());
        return new SftpFilePath(this, absPath);
    }

    SftpFilePath toRealPath(SftpFilePath path, LinkOption... options) throws IOException {
        boolean followLinks = LinkOptionSupport.followLinks(options);
        try (FileSystemChannel channel = pool.get()) {
            return toRealPath(channel, path, followLinks).path;
        }
    }

    InputStream newInputStream(SftpFilePath path, OpenOption... options) throws IOException {
        OpenOptions openOptions = OpenOptions.forNewInputStream(options);

        try (FileSystemChannel channel = pool.get()) {
            return newInputStream(channel, path, openOptions);
        }
    }

    OutputStream newOutputStream(SftpFilePath path, OpenOption... options) throws IOException {
        OpenOptions openOptions = OpenOptions.forNewOutputStream(options);

        try (FileSystemChannel channel = pool.get()) {
            return newOutputStream(channel, path, false, openOptions).out;
        }
    }

    SeekableByteChannel newByteChannel(SftpFilePath path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        if (attrs.length > 0) {
            throw Messages.fileSystemProvider().unsupportedCreateFileAttribute(attrs[0].name());
        }
        OpenOptions openOptions = OpenOptions.forNewByteChannel(options);
        try (FileSystemChannel channel = pool.get()) {
            if (openOptions.read) {
                // use findAttributes instead of getAttributes, to let the opening of the stream provide the correct error message
                FileAttributes attributes = getAttributes(channel, path, false);
                InputStream in = newInputStream(channel, path, openOptions);
                long size = attributes == null ? 0 : attributes.getSize();
                return SeekableFileByteChannel.builder()
                        .inputStream(in)
                        .size(size)
                        .build();
            }

            // if appended, then we need the attributes, to find the initial position of the channel
            boolean requireAttributes = openOptions.append;
            SftpAttributesAndOutputStreamPair outPair = newOutputStream(channel, path, requireAttributes, openOptions);
            long initialPosition = outPair.attributes == null ? 0 : outPair.attributes.getSize();
            if (openOptions.write && !openOptions.append) {
                initialPosition = 0;
            }
            return SeekableFileByteChannel.builder()
                    .outputStream(outPair.out)
                    .position(initialPosition)
                    .build();
        }
    }

    DirectoryStream<Path> newDirectoryStream(SftpFilePath path, DirectoryStream.Filter<? super Path> filter) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            List<FileEntry> entries = listFileEntry(channel, path.path());
            return new SftpPathDirectoryStream(path, entries, filter);
        }
    }

    void createDirectory(SftpFilePath path, FileAttribute<?>... attrs) throws IOException {
        if (attrs.length > 0) {
            throw Messages.fileSystemProvider().unsupportedCreateFileAttribute(attrs[0].name());
        }
        try (FileSystemChannel channel = pool.get()) {
            channel.mkdir(path.path());
        }
    }

    void delete(SftpFilePath path) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            FileAttributes attributes = getAttributes(channel, path, false);
            boolean isDirectory = attributes.isDir();
            channel.delete(path.path(), isDirectory);
        }
    }

    SftpFilePath readSymbolicLink(SftpFilePath path) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            return readSymbolicLink(channel, path);
        }
    }

    void copy(SftpFilePath source, SftpFilePath target, CopyOption... options) throws IOException {
        boolean sameFileSystem = haveSameFileSystem(source, target);
        CopyOptions copyOptions = CopyOptions.forCopy(options);
        try (FileSystemChannel channel = pool.get()) {
            // get the attributes to determine whether a directory needs to be created or a file needs to be copied
            // Files.copy specifies that for links, the final target must be copied
            SftpPathAndAttributesPair sourcePair = toRealPath(channel, source, true);
            if (!sameFileSystem) {
                copyAcrossFileSystems(channel, source, sourcePair.attributes, target, copyOptions);
                return;
            }
            try {
                if (sourcePair.path.path().equals(toRealPath(channel, target, true).path.path())) {
                    // non-op, don't do a thing as specified by Files.copy
                    return;
                }
            } catch (@SuppressWarnings("unused") NoSuchFileException e) {
                // the target does not exist, or either path is an invalid link, ignore the error and continue
            }
            FileAttributes targetAttributes = getAttributes(channel, target, false);
            if (targetAttributes != null) {
                if (copyOptions.replaceExisting) {
                    channel.delete(target.path(), targetAttributes.isDir());
                } else {
                    throw new FileAlreadyExistsException(target.path());
                }
            }

            if (sourcePair.attributes.isDir()) {
                channel.mkdir(target.path());
            } else {
                try (FileSystemChannel channel2 = pool.getOrCreate()) {
                    copyFile(channel, source, channel2, target, copyOptions);
                }
            }
        }
    }

    void move(SftpFilePath source, SftpFilePath target, CopyOption... options) throws IOException {
        boolean sameFileSystem = haveSameFileSystem(source, target);
        CopyOptions copyOptions = CopyOptions.forMove(sameFileSystem, options);
        try (FileSystemChannel channel = pool.get()) {
            if (!sameFileSystem) {
                FileAttributes attributes = getAttributes(channel, source, false);
                if (attributes.isLink()) {
                    throw new IOException(SftpMessages.copyOfSymbolicLinksAcrossFileSystemsNotSupported());
                }
                copyAcrossFileSystems(channel, source, attributes, target, copyOptions);
                channel.delete(source.path(), attributes.isDir());
                return;
            }
            try {
                if (isSameFile(channel, source, target)) {
                    // non-op, don't do a thing as specified by Files.move
                    return;
                }
            } catch (NoSuchFileException e) {
                // the source or target does not exist, or either path is an invalid link
                // call getAttributes to ensure the source file exists
                // ignore any error to target or if the source link is invalid
                getAttributes(channel, source, false);
            }

            if (toAbsolutePath(source).getParent() == null) {
                // cannot move or rename the root
                throw new DirectoryNotEmptyException(source.path());
            }

            FileAttributes targetAttributes = getAttributes(channel, target, false);
            if (copyOptions.replaceExisting && targetAttributes != null) {
                channel.delete(target.path(), targetAttributes.isDir());
            }
            channel.rename(source.path(), target.path());
        }
    }

    boolean isSameFile(SftpFilePath path, SftpFilePath path2) throws IOException {
        if (!haveSameFileSystem(path, path2)) {
            return false;
        }
        if (path.equals(path2)) {
            return true;
        }
        try (FileSystemChannel channel = pool.get()) {
            return isSameFile(channel, path, path2);
        }
    }

    boolean isHidden(SftpFilePath path) throws IOException {
        // call getAttributes to check for existence
        try (FileSystemChannel channel = pool.get()) {
            getAttributes(channel, path, false);
        }
        String fileName = path.fileName();
        return !CURRENT_DIR.equals(fileName) && !PARENT_DIR.equals(fileName) && ".".startsWith(fileName);
    }

    FileStore getFileStore(SftpFilePath path) throws IOException {
        // call getAttributes to check for existence
        try (FileSystemChannel channel = pool.get()) {
            getAttributes(channel, path, false);
        }
        return new SftpFileStore(path);
    }

    void checkAccess(SftpFilePath path, AccessMode... modes) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            FileAttributes attributes = getAttributes(channel, path, true);
            for (AccessMode mode : modes) {
                if (!hasAccess(attributes, mode)) {
                    throw new AccessDeniedException(path.path());
                }
            }
        }
    }

    @Override
    protected PosixFileAttributes readAttributes(Path path, LinkOption... options) throws IOException {
        boolean followLinks = LinkOptionSupport.followLinks(options);
        try (FileSystemChannel channel = pool.get()) {
            FileAttributes attributes = getAttributes(channel, (SftpFilePath) path, followLinks);
            return new SftpPathFileAttributes(attributes);
        }
    }

    void setOwner(SftpFilePath path, UserPrincipal owner) throws IOException {
        setOwner(path, owner, false);
    }

    private void setOwner(SftpFilePath path, UserPrincipal owner, boolean followLinks) throws IOException {
        try {
            try (FileSystemChannel channel = pool.get()) {
                if (followLinks) {
                    path = toRealPath(channel, path, followLinks).path;
                }
                channel.chown(path.path(), owner.getName());
            }
        } catch (NumberFormatException e) {
            throw new IOException(e);
        }
    }

    void setGroup(SftpFilePath path, GroupPrincipal group) throws IOException {
        setGroup(path, group, false);
    }

    private void setGroup(SftpFilePath path, GroupPrincipal group, boolean followLinks) throws IOException {
        try {
            try (FileSystemChannel channel = pool.get()) {
                if (followLinks) {
                    path = toRealPath(channel, path, followLinks).path;
                }
                channel.changeGroup(path.path(), group.getName());
            }
        } catch (NumberFormatException e) {
            throw new IOException(e);
        }
    }

    void setPermissions(SftpFilePath path, Set<PosixFilePermission> permissions) throws IOException {
        setPermissions(path, permissions, false);
    }

    void setTimes(SftpFilePath path, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        if (lastAccessTime != null) {
            throw new IOException(Messages.fileSystemProvider().unsupportedFileAttribute("lastAccessTime"));
        }
        if (createTime != null) {
            throw new IOException(Messages.fileSystemProvider().unsupportedFileAttribute("createAccessTime"));
        }
        if (lastModifiedTime != null) {
            setLastModifiedTime(path, lastModifiedTime, false);
        }
    }

    void setLastModifiedTime(SftpFilePath path, FileTime lastModifiedTime, boolean followLinks) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            if (followLinks) {
                path = toRealPath(channel, path, followLinks).path;
            }
            // times are in seconds
            channel.setMtime(path.path(), lastModifiedTime.to(TimeUnit.SECONDS));
        }
    }

    @SuppressWarnings("unchecked")
    void setAttribute(SftpFilePath path, String attribute, Object value, LinkOption... options) throws IOException {
        String view;
        int pos = attribute.indexOf(':');
        if (pos == -1) {
            view = "basic";
            attribute = "basic:" + attribute;
        } else {
            view = attribute.substring(0, pos);
        }
        if (!"basic".equals(view) && !"owner".equals(view) && !"posix".equals(view)) {
            throw Messages.fileSystemProvider().unsupportedFileAttributeView(view);
        }

        boolean followLinks = LinkOptionSupport.followLinks(options);

        switch (attribute) {
            case "basic:lastModifiedTime":
            case "posix:lastModifiedTime":
                setLastModifiedTime(path, (FileTime) value, followLinks);
                break;
            case "owner:owner":
            case "posix:owner":
                setOwner(path, (UserPrincipal) value, followLinks);
                break;
            case "posix:group":
                setGroup(path, (GroupPrincipal) value, followLinks);
                break;
            case "posix:permissions":
                Set<PosixFilePermission> permissions = (Set<PosixFilePermission>) value;
                setPermissions(path, permissions, followLinks);
                break;
            default:
                throw Messages.fileSystemProvider().unsupportedFileAttribute(attribute);
        }
    }

    private void setPermissions(SftpFilePath path, Set<PosixFilePermission> permissions, boolean followLinks) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            if (followLinks) {
                path = toRealPath(channel, path, followLinks).path;
            }
            channel.chmod(path.path(), PosixFilePermissionSupport.toMask(permissions));
        }
    }

    long getTotalSpace(SftpFilePath path) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            SftpStatVFS stat = ((SshChannel) channel).statVFS(path.path());
            // don't use stat.getSize because that uses kilobyte precision
            return stat.getFragmentSize() * stat.getBlocks();
        } catch (UnsupportedOperationException e) {
            // statVFS is not available
            return Long.MAX_VALUE;
        }
    }

    long getUsableSpace(SftpFilePath path) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            SftpStatVFS stat = ((SshChannel) channel).statVFS(path.path());
            // don't use stat.getAvailForNonRoot because that uses kilobyte precision
            return stat.getFragmentSize() * stat.getAvailBlocks();
        } catch (UnsupportedOperationException e) {
            // statVFS is not available
            return Long.MAX_VALUE;
        }
    }

    long getUnallocatedSpace(SftpFilePath path) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            SftpStatVFS stat = ((SshChannel) channel).statVFS(path.path());
            // don't use stat.getAvail because that uses kilobyte precision
            return stat.getFragmentSize() * stat.getFreeBlocks();
        } catch (UnsupportedOperationException e) {
            // statVFS is not available
            return Long.MAX_VALUE;
        }
    }

    Map<String, Object> readAttributes(SftpFilePath path, String attributes, LinkOption... options) throws IOException {
        return super.readAttributes(path, attributes, options);
    }

    private static final class SftpPathFileAttributes implements PosixFileAttributes {
        private final FileAttributes attributes;

        private SftpPathFileAttributes(FileAttributes attributes) {
            this.attributes = attributes;
        }

        @Override
        public UserPrincipal owner() {
            String user = Integer.toString(attributes.uid());
            return DefaultUserPrincipal.builder().name(user).build();
        }

        @Override
        public GroupPrincipal group() {
            String group = Integer.toString(attributes.gid());
            return DefaultGroupPrincipal.builder().group(group).build();
        }

        @Override
        public Set<PosixFilePermission> permissions() {
            return PosixFilePermissionSupport.fromMask(attributes.getPermissionMask());
        }

        @Override
        public FileTime lastModifiedTime() {
            // times are in seconds
            return FileTime.from(attributes.getMTime(), TimeUnit.SECONDS);
        }

        @Override
        public FileTime lastAccessTime() {
            // times are in seconds
            return FileTime.from(attributes.getLastAccessTime(), TimeUnit.SECONDS);
        }

        @Override
        public FileTime creationTime() {
            return lastModifiedTime();
        }

        @Override
        public boolean isRegularFile() {
            return attributes.isReg();
        }

        @Override
        public boolean isDirectory() {
            return attributes.isDir();
        }

        @Override
        public boolean isSymbolicLink() {
            return attributes.isLink();
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

    private boolean hasAccess(FileAttributes attrs, AccessMode mode) {
        switch (mode) {
            case READ:
                return PosixFilePermissionSupport.hasPermission(attrs.getPermissionMask(), PosixFilePermission.OWNER_READ);
            case WRITE:
                return PosixFilePermissionSupport.hasPermission(attrs.getPermissionMask(), PosixFilePermission.OWNER_WRITE);
            case EXECUTE:
                return PosixFilePermissionSupport.hasPermission(attrs.getPermissionMask(), PosixFilePermission.OWNER_EXECUTE);
            default:
                return false;
        }
    }

    private boolean isSameFile(FileSystemChannel channel, SftpFilePath path, SftpFilePath path2) throws IOException {
        if (path.equals(path2)) {
            return true;
        }
        return toRealPath(channel, path, true).path.path().equals(toRealPath(channel, path2, true).path.path());
    }

    private void copyAcrossFileSystems(FileSystemChannel sourceChannel, SftpFilePath source, FileAttributes sourceAttributes,
                                       SftpFilePath target, CopyOptions options)
            throws IOException {
        SftpFileSystem targetFileSystem = (SftpFileSystem) target.getFileSystem();
        try (FileSystemChannel targetChannel = targetFileSystem.pool.getOrCreate()) {
            FileAttributes targetAttributes = getAttributes(targetChannel, target, false);
            if (targetAttributes != null) {
                if (options.replaceExisting) {
                    targetChannel.delete(target.path(), targetAttributes.isDir());
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

    private boolean haveSameFileSystem(SftpFilePath path, SftpFilePath path2) {
        return path.getFileSystem() == path2.getFileSystem();
    }

    private InputStream newInputStream(FileSystemChannel channel, SftpFilePath path, OpenOptions options) throws IOException {
        assert options.read;
        return channel.newInputStream(path.path(), options);
    }

    private SftpAttributesAndOutputStreamPair newOutputStream(FileSystemChannel channel, SftpFilePath path, boolean requireAttributes, OpenOptions options)
            throws IOException {
        FileAttributes attributes = null;
        // retrieve the attributes unless create is true and createNew is false, because then the file can be created
        if (!options.create || options.createNew) {
            attributes = getAttributes(channel, path, false);
            if (attributes != null && attributes.isDir()) {
                throw Messages.fileSystemProvider().isDirectory(path.path());
            }
            if (!options.createNew && attributes == null) {
                throw new NoSuchFileException(path.path());
            } else if (options.createNew && attributes != null) {
                throw new FileAlreadyExistsException(path.path());
            }
        }
        // else the file can be created if necessary
        if (attributes == null && requireAttributes) {
            attributes = getAttributes(channel, path, false);
        }
        OutputStream out = channel.newOutputStream(path.path(), options);
        return new SftpAttributesAndOutputStreamPair(attributes, out);
    }

    private SftpPathAndAttributesPair toRealPath(FileSystemChannel channel, SftpFilePath path, boolean followLinks) throws IOException {
        SftpFilePath absPath = (SftpFilePath) toAbsolutePath(path).normalize();
        FileAttributes attributes = getAttributes(channel, absPath, false);
        if (followLinks && attributes.isLink()) {
            SftpFilePath link = readSymbolicLink(channel, absPath);
            return toRealPath(channel, link, followLinks);
        }
        return new SftpPathAndAttributesPair(absPath, attributes);
    }

    private FileAttributes getAttributes(FileSystemChannel channel, SftpFilePath path, boolean followLinks) throws IOException {
        return channel.readAttributes(path.path(), followLinks);
    }

    private SftpFilePath readSymbolicLink(FileSystemChannel channel, SftpFilePath path) throws IOException {
        String link = channel.readSymbolicLink(path.path());
        return (SftpFilePath) path.resolveSibling(link);
    }

    private static final class SftpPathAndAttributesPair {
        private final SftpFilePath path;
        private final FileAttributes attributes;

        private SftpPathAndAttributesPair(SftpFilePath path, FileAttributes attributes) {
            this.path = path;
            this.attributes = attributes;
        }
    }

    private static final class SftpAttributesAndOutputStreamPair {
        private final FileAttributes attributes;
        private final OutputStream out;

        private SftpAttributesAndOutputStreamPair(FileAttributes attributes, OutputStream out) {
            this.attributes = attributes;
            this.out = out;
        }
    }

    private static final class SftpPathDirectoryStream extends AbstractDirectoryStream<Path> {
        private final SftpFilePath path;
        private final List<FileEntry> entries;
        private Iterator<FileEntry> iterator;

        private SftpPathDirectoryStream(SftpFilePath path, List<FileEntry> entries, DirectoryStream.Filter<? super Path> filter) {
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
            return iterator.hasNext() ? path.resolve(iterator.next().getFileName()) : null;
        }
    }
}
