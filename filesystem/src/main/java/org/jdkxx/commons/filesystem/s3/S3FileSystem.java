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
import org.jdkxx.commons.filesystem.utils.FileSystemProviderSupport;
import org.jdkxx.commons.filesystem.utils.LinkOptionSupport;
import org.jdkxx.commons.filesystem.utils.PathMatcherSupport;
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
import java.util.regex.Pattern;

@Slf4j
public class S3FileSystem extends FileSystem {
    private static final String CURRENT_DIR = ".";
    private static final String PARENT_DIR = "..";
    private final FileSystemProvider provider;
    private final URI uri;
    private final ChannelPool pool;
    private final AtomicBoolean opened = new AtomicBoolean(true);
    private final AtomicBoolean readOnly = new AtomicBoolean(false);

    S3FileSystem(FileSystemProvider provider,
                 URI uri,
                 FileSystemEnvironment environment) throws IOException {
        this.provider = Objects.requireNonNull(provider);
        this.uri = Objects.requireNonNull(uri);
        this.pool = new S3ChannelPool(uri.getHost(), environment);
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public boolean isOpen() {
        return opened.get();
    }

    @Override
    public boolean isReadOnly() {
        return readOnly.get();
    }

    @Override
    public String getSeparator() {
        return File.separator;
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singleton(new S3FilePath(this, ""));
    }

    @Override
    public @NotNull Path getPath(@NotNull String first, @NotNull String @NotNull ... more) {
        if (first.startsWith(getSeparator())) {
            first = first.substring(1);
        }
        StringBuilder sb = new StringBuilder(first);
        for (String s : more) {
            sb.append(getSeparator()).append(s);
        }
        return new S3FilePath(this, sb.toString());
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        final Pattern pattern = PathMatcherSupport.toPattern(syntaxAndPattern);
        return path -> pattern.matcher(path.toString()).matches();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        return DefaultUserPrincipal.builder();
    }

    @Override
    public void close() throws IOException {
        if (opened.getAndSet(false)) {
            pool.close();
        }
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        throw Messages.unsupportedOperation(FileSystem.class, "getFileStores");
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        throw Messages.unsupportedOperation(FileSystem.class, "supportedFileAttributeViews");
    }

    @Override
    public WatchService newWatchService() throws IOException {
        throw Messages.unsupportedOperation(FileSystem.class, "newWatchService");
    }


    URI toUri(S3FilePath path) {
        Path absPath = toAbsolutePath(path).normalize();
        return toUri(((AbstractFilePath) absPath).path());
    }

    URI toUri(String path) {
        return URISupport.create(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), path,
                null, null);
    }

    S3FilePath getRoot() {
        return new S3FilePath(this, "");
    }

    boolean exists(S3FilePath path) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            return channel.exists(path.path());
        }
    }

    InputStream newInputStream(S3FilePath path, OpenOption... options) throws IOException {
        OpenOptions openOptions = OpenOptions.forNewInputStream(options);
        try (FileSystemChannel channel = pool.get()) {
            return newInputStream(channel, path, openOptions);
        }
    }

    private InputStream newInputStream(FileSystemChannel channel, S3FilePath path, OpenOptions options) throws IOException {
        assert options.read;
        return channel.newInputStream(path.path(), options);
    }

    OutputStream newOutputStream(S3FilePath path, OpenOption... options) throws IOException {
        OpenOptions openOptions = OpenOptions.forNewOutputStream(options);
        try (FileSystemChannel channel = pool.get()) {
            return channel.newOutputStream(path.path(), openOptions);
        }
    }

    SeekableByteChannel newByteChannel(S3FilePath path,
                                       Set<? extends OpenOption> options,
                                       FileAttribute<?>... attrs) throws IOException {
        if (attrs.length > 0) {
            throw Messages.fileSystemProvider().unsupportedCreateFileAttribute(attrs[0].name());
        }

        OpenOptions openOptions = OpenOptions.forNewByteChannel(options);
        try (FileSystemChannel channel = pool.get()) {
            if (openOptions.read) {
                InputStream in = newInputStream(channel, path, openOptions);
                return FileSystemProviderSupport.createSeekableByteChannel(in, 0);
            }
            OutputStream out = newOutputStream(path, options.toArray(new OpenOption[]{}));
            return FileSystemProviderSupport.createSeekableByteChannel(out, 0);
        }
    }

    DirectoryStream<Path> newDirectoryStream(S3FilePath path,
                                             DirectoryStream.Filter<? super Path> filter) throws IOException {
        if (!path.isDirectory()) {
            throw new NotDirectoryException("Path - [" + path + "] not a folder.");
        }
        try (FileSystemChannel channel = pool.get()) {
            List<FileEntry> entries = channel.listFiles(path.path());
            boolean isDirectory = false;
            for (Iterator<FileEntry> i = entries.iterator(); i.hasNext(); ) {
                FileEntry entry = i.next();
                String filename = entry.getFileName();
                if (CURRENT_DIR.equals(filename)) {
                    isDirectory = true;
                    i.remove();
                } else if (PARENT_DIR.equals(filename)) {
                    i.remove();
                }
            }
            if (!isDirectory) {
                FileAttributes attributes = channel.readAttributes(path.path(), false);
                if (!attributes.isDir()) {
                    throw new NotDirectoryException(path.path());
                }
            }
            return new S3DirectoryStream(path, entries, filter);
        }
    }

    void createDirectory(S3FilePath path, FileAttribute<?>... attrs) throws IOException {
        if (attrs.length > 0) {
            throw Messages.fileSystemProvider().unsupportedCreateFileAttribute(attrs[0].name());
        }
        try (FileSystemChannel channel = pool.get()) {
            channel.mkdir(path.path());
        }
    }

    void delete(S3FilePath path) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            channel.delete(path.path());
        }
    }

    PosixFileAttributes readAttributes(S3FilePath path, LinkOption... options) throws IOException {
        boolean followLinks = LinkOptionSupport.followLinks(options);
        try (FileSystemChannel channel = pool.get()) {
            FileAttributes attributes = channel.readAttributes(path.path(), followLinks);
            return new S3FileAttributes(attributes);
        }
    }

    void copy(S3FilePath source, S3FilePath target, CopyOption... options) throws IOException {
        boolean sameFileSystem = haveSameFileSystem(source, target);
        CopyOptions copyOptions = CopyOptions.forCopy(options);
        try (FileSystemChannel channel = pool.get()) {
            FileAttributes sourceAttributes = channel.readAttributes(source.path(), false);
            if (!sameFileSystem) {
                copyAcrossFileSystems(channel, source, sourceAttributes, target, copyOptions);
                return;
            }
            try {
                if (source.path().equals(toRealPath(target).path())) {
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

    S3FilePath toRealPath(S3FilePath path, LinkOption... options) throws IOException {
        return (S3FilePath) toAbsolutePath(path).normalize();
    }

    S3FilePath toAbsolutePath(S3FilePath path) {
        if (path.isAbsolute()) {
            return path;
        }
        return new S3FilePath(this, path.path());
    }

    void move(S3FilePath source, S3FilePath target, CopyOption... options) throws IOException {
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

            if (toAbsolutePath(source).getParent() == null) {
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

    boolean isSameFile(S3FilePath path, S3FilePath other) throws IOException {
        if (!haveSameFileSystem(path, other)) {
            return false;
        }
        if (path.equals(other)) {
            return true;
        }
        return toRealPath(path).path().equals(toRealPath(other).path());
    }

    void checkAccess(S3FilePath path, AccessMode... modes) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            FileAttributes attributes = channel.readAttributes(path.path(), true);
            for (AccessMode mode : modes) {
                if (!hasAccess(attributes, mode)) {
                    throw new AccessDeniedException(path.path());
                }
            }
        }
    }

    void setAccessControlList(S3FilePath path, List<AclEntry> acl) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            channel.chmod(path.path(), acl);
        }
    }

    void setTimes(S3FilePath path, FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
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

    void setLastModifiedTime(S3FilePath path, FileTime lastModifiedTime, boolean followLinks) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            if (followLinks) {
                path = toRealPath(path);
            }
            // times are in seconds
            channel.setMtime(path.path(), lastModifiedTime.to(TimeUnit.MILLISECONDS));
        }
    }

    void setLastAccessTime(S3FilePath path, FileTime lastModifiedTime, boolean followLinks) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            if (followLinks) {
                path = toRealPath(path);
            }
            // times are in seconds
            channel.setAtime(path.path(), lastModifiedTime.to(TimeUnit.MILLISECONDS));
        }
    }

    void setCreateTime(S3FilePath path, FileTime createTime, boolean followLinks) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            if (followLinks) {
                path = toRealPath(path);
            }
            // times are in seconds
            channel.setCtime(path.path(), createTime.to(TimeUnit.MILLISECONDS));
        }
    }

    void setOwner(S3FilePath path, UserPrincipal owner) throws IOException {
        try (FileSystemChannel channel = pool.get()) {
            channel.chown(path.path(), owner.getName());
        }
    }

    void setPermissions(S3FilePath path, Set<PosixFilePermission> permissions) throws IOException {
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

    private boolean haveSameFileSystem(S3FilePath path, S3FilePath other) {
        return path.getFileSystem() == other.getFileSystem();
    }

    private void copyAcrossFileSystems(FileSystemChannel sourceChannel,
                                       FilePath source,
                                       FileAttributes sourceAttributes,
                                       FilePath target, CopyOptions options) throws IOException {
        S3FileSystem targetFileSystem = (S3FileSystem) target.getFileSystem();
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

    private void copyFile(FileSystemChannel sourceChannel,
                          FilePath source,
                          FileSystemChannel targetChannel,
                          FilePath target,
                          CopyOptions options) throws IOException {
        OpenOptions inOptions = OpenOptions.forNewInputStream(options.toOpenOptions(StandardOpenOption.READ));
        OpenOptions outOptions = OpenOptions
                .forNewOutputStream(options.toOpenOptions(StandardOpenOption.WRITE, StandardOpenOption.CREATE));
        try (InputStream in = sourceChannel.newInputStream(source.path(), inOptions)) {
            targetChannel.storeFile(target.path(), in, outOptions.options);
        }
    }

    private static final Set<String> BASIC_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "basic:lastModifiedTime", "basic:lastAccessTime", "basic:creationTime", "basic:size",
            "basic:isRegularFile", "basic:isDirectory", "basic:isSymbolicLink", "basic:isOther", "basic:fileKey")));

    private static final Set<String> OWNER_ATTRIBUTES =
            Collections.unmodifiableSet(new HashSet<>(Collections.singletonList("owner:owner")));

    private static final Set<String> POSIX_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "posix:lastModifiedTime", "posix:lastAccessTime", "posix:creationTime", "posix:size",
            "posix:isRegularFile", "posix:isDirectory", "posix:isSymbolicLink", "posix:isOther", "posix:fileKey",
            "posix:owner", "posix:group", "posix:permissions")));

    private static final Set<String> SUPPORTED_FILE_ATTRIBUTE_VIEWS = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList("basic", "owner", "posix")));

    Map<String, Object> readAttributes(S3FilePath path, String attributes, LinkOption... options) throws IOException {
        String view;
        int pos = attributes.indexOf(':');
        if (pos == -1) {
            view = "basic";
            attributes = "basic:" + attributes;
        } else {
            view = attributes.substring(0, pos);
        }
        if (!SUPPORTED_FILE_ATTRIBUTE_VIEWS.contains(view)) {
            throw Messages.fileSystemProvider().unsupportedFileAttributeView(view);
        }
        Set<String> allowedAttributes;
        if (attributes.startsWith("basic:")) {
            allowedAttributes = BASIC_ATTRIBUTES;
        } else if (attributes.startsWith("owner:")) {
            allowedAttributes = OWNER_ATTRIBUTES;
        } else if (attributes.startsWith("posix:")) {
            allowedAttributes = POSIX_ATTRIBUTES;
        } else {
            // should not occur
            throw Messages.fileSystemProvider().unsupportedFileAttributeView(attributes.substring(0, attributes.indexOf(':')));
        }
        Map<String, Object> result = getAttributeMap(attributes, allowedAttributes);
        PosixFileAttributes posixAttributes = readAttributes(path, options);
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            switch (entry.getKey()) {
                case "basic:lastModifiedTime":
                case "posix:lastModifiedTime":
                    entry.setValue(posixAttributes.lastModifiedTime());
                    break;
                case "basic:lastAccessTime":
                case "posix:lastAccessTime":
                    entry.setValue(posixAttributes.lastAccessTime());
                    break;
                case "basic:creationTime":
                case "posix:creationTime":
                    entry.setValue(posixAttributes.creationTime());
                    break;
                case "basic:size":
                case "posix:size":
                    entry.setValue(posixAttributes.size());
                    break;
                case "basic:isRegularFile":
                case "posix:isRegularFile":
                    entry.setValue(posixAttributes.isRegularFile());
                    break;
                case "basic:isDirectory":
                case "posix:isDirectory":
                    entry.setValue(posixAttributes.isDirectory());
                    break;
                case "basic:isSymbolicLink":
                case "posix:isSymbolicLink":
                    entry.setValue(posixAttributes.isSymbolicLink());
                    break;
                case "basic:isOther":
                case "posix:isOther":
                    entry.setValue(posixAttributes.isOther());
                    break;
                case "basic:fileKey":
                case "posix:fileKey":
                    entry.setValue(posixAttributes.fileKey());
                    break;
                case "owner:owner":
                case "posix:owner":
                    entry.setValue(posixAttributes.owner());
                    break;
                case "posix:group":
                    entry.setValue(posixAttributes.group());
                    break;
                case "posix:permissions":
                    entry.setValue(posixAttributes.permissions());
                    break;
                default:
                    // should not occur
                    throw new IllegalStateException("unexpected attribute name: " + entry.getKey());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    void setAttribute(S3FilePath path, String attribute, Object value, LinkOption... options) throws IOException {
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
                throw Messages.fileSystemProvider().unsupportedFileAttribute(attribute);
        }
    }

    private Map<String, Object> getAttributeMap(String attributes, Set<String> allowedAttributes) {
        int indexOfColon = attributes.indexOf(':');
        String prefix = attributes.substring(0, indexOfColon + 1);
        attributes = attributes.substring(indexOfColon + 1);

        String[] attributeList = attributes.split(",");
        Map<String, Object> result = new HashMap<>(allowedAttributes.size());

        for (String attribute : attributeList) {
            String prefixedAttribute = prefix + attribute;
            if (allowedAttributes.contains(prefixedAttribute)) {
                result.put(prefixedAttribute, null);
            } else if ("*".equals(attribute)) {
                for (String s : allowedAttributes) {
                    result.put(s, null);
                }
            } else {
                throw Messages.fileSystemProvider().unsupportedFileAttribute(attribute);
            }
        }
        return result;
    }

    private static final class S3DirectoryStream extends AbstractDirectoryStream<Path> {
        private final S3FilePath path;
        private final List<FileEntry> entries;
        private Iterator<FileEntry> iterator;

        private S3DirectoryStream(S3FilePath path, List<FileEntry> entries, Filter<? super Path> filter) {
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
                    new S3FilePath((S3FileSystem) path.getFileSystem(), iterator.next().getLongName()) : null;
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
