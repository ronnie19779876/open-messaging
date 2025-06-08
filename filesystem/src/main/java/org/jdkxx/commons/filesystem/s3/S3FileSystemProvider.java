package org.jdkxx.commons.filesystem.s3;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jdkxx.commons.filesystem.FileSystems;
import org.jdkxx.commons.filesystem.utils.URISupport;
import org.jdkxx.commons.filesystem.config.FileSystemEnvironment;
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
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
public class S3FileSystemProvider extends FileSystemProvider {
    private final FileSystems<S3FileSystem> fileSystems = new FileSystems<>(this::createFileSystem);
    private static final String FILESYSTEM_SCHEME = "s3";

    private S3FileSystem createFileSystem(URI uri, Map<String, ?> env) throws IOException {
        return new S3FileSystem(this, uri, (FileSystemEnvironment) env);
    }

    @Override
    public String getScheme() {
        return FILESYSTEM_SCHEME;
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        // user info must come from the environment map
        checkURI(uri, false, false);
        FileSystemEnvironment environment = FileSystemEnvironment.copy(env);
        String username = environment.getUsername();
        URI normalizedURI = normalizeWithUsername(uri, username);
        return fileSystems.add(normalizedURI, environment);
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        checkURI(uri, true, false);
        return getExistingFileSystem(uri);
    }

    public boolean exists(Path path, LinkOption... options) {
        return toS3FilePath(path).exists();
    }

    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        return toS3FilePath(path).newInputStream(options);
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        return toS3FilePath(path).newOutputStream(options);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path,
                                              Set<? extends OpenOption> options,
                                              FileAttribute<?>... attrs) throws IOException {
        return toS3FilePath(path).newByteChannel(options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir,
                                                    DirectoryStream.Filter<? super Path> filter) throws IOException {
        return toS3FilePath(dir).newDirectoryStream(filter);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        toS3FilePath(dir).createDirectory(attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        toS3FilePath(path).delete();
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        toS3FilePath(source).copy(toS3FilePath(target), options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        toS3FilePath(source).move(toS3FilePath(target), options);
    }

    @Override
    public @NotNull Path getPath(@NotNull URI uri) {
        checkURI(uri, true, true);
        FileSystem fs = getExistingFileSystem(uri);
        String path = uri.getPath();
        if (path.startsWith(File.separator)) {
            path = path.substring(1);
        }

        String bucket;
        if (StringUtils.isNoneBlank(uri.getFragment())) {
            bucket = uri.getFragment();
        } else {
            int pos = path.indexOf(File.separator);
            bucket = path.substring(0, pos);
        }
        if (StringUtils.startsWithIgnoreCase(path, bucket)) {
            path = path.substring(bucket.length() + 1);
        }
        return fs.getPath(path, bucket);
    }

    @Override
    public boolean isSameFile(Path path, Path other) throws IOException {
        return toS3FilePath(path).isSameFile(other);
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return false;
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        throw Messages.fileStore().unsupportedAttribute("getFileStore");
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        toS3FilePath(path).checkAccess(modes);
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        Objects.requireNonNull(type);
        if (type == BasicFileAttributeView.class) {
            return type.cast(new AttributeView("basic", toS3FilePath(path)));
        }
        if (type == FileOwnerAttributeView.class) {
            return type.cast(new AttributeView("owner", toS3FilePath(path)));
        }
        if (type == PosixFileAttributeView.class) {
            return type.cast(new AttributeView("posix", toS3FilePath(path)));
        }
        if (type == AclFileAttributeView.class) {
            return type.cast(new AttributeView("acl", toS3FilePath(path)));
        }
        return null;
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        if (type == BasicFileAttributes.class || type == PosixFileAttributes.class) {
            return type.cast(toS3FilePath(path).readAttributes(options));
        }
        throw Messages.fileSystemProvider().unsupportedFileAttributesType(type);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return toS3FilePath(path).readAttributes(attributes, options);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        toS3FilePath(path).setAttribute(attribute, value, options);
    }

    private S3FilePath toS3FilePath(Path path) {
        Objects.requireNonNull(path);
        if (path instanceof S3FilePath) {
            return (S3FilePath) path;
        }
        throw new ProviderMismatchException();
    }

    private FileSystem getExistingFileSystem(URI uri) {
        URI normalizedURI = normalizeWithoutPassword(uri);
        return fileSystems.get(normalizedURI);
    }

    private URI normalizeWithoutPassword(URI uri) {
        String userInfo = uri.getUserInfo();
        if (userInfo == null && uri.getPath() == null && uri.getQuery() == null && uri.getFragment() == null) {
            // nothing to normalize, return the URI
            return uri;
        }
        String username = null;
        if (userInfo != null) {
            int index = userInfo.indexOf(':');
            username = index == -1 ? userInfo : userInfo.substring(0, index);
        }
        // no path, query or fragment
        return URISupport.create(uri.getScheme(), username, uri.getHost(), uri.getPort(),
                null, null, null);
    }

    private URI normalizeWithUsername(URI uri, String username) {
        if (username == null && uri.getUserInfo() == null && uri.getPath() == null
                && uri.getQuery() == null && uri.getFragment() == null) {
            // nothing to normalize or add, return the URI
            return uri;
        }
        // no path, query or fragment
        return URISupport.create(uri.getScheme(), username, uri.getHost(), uri.getPort(),
                null, null, null);
    }

    private void checkURI(URI uri, boolean allowUserInfo, boolean allowPath) {
        if (!uri.isAbsolute()) {
            throw Messages.uri().notAbsolute(uri);
        }
        if (!getScheme().equalsIgnoreCase(uri.getScheme())) {
            throw Messages.uri().invalidScheme(uri, getScheme());
        }
        if (!allowUserInfo && uri.getUserInfo() != null && !uri.getUserInfo().isEmpty()) {
            throw Messages.uri().hasUserInfo(uri);
        }
        if (uri.isOpaque()) {
            throw Messages.uri().notHierarchical(uri);
        }
        if (!allowPath && uri.getPath() != null && !uri.getPath().isEmpty()) {
            throw Messages.uri().hasPath(uri);
        }
        if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            throw Messages.uri().hasQuery(uri);
        }
        //if (uri.getFragment() != null && !uri.getFragment().isEmpty()) {
        //    throw Messages.uri().hasFragment(uri);
        //}
    }

    private static final class AttributeView implements AclFileAttributeView, PosixFileAttributeView {
        private final String name;
        private final S3FilePath path;

        private AttributeView(String name, S3FilePath path) {
            this.name = Objects.requireNonNull(name);
            this.path = Objects.requireNonNull(path);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<AclEntry> getAcl() throws IOException {
            return null;
        }

        @Override
        public void setAcl(List<AclEntry> acl) throws IOException {
            path.setAccessControlList(acl);
        }

        @Override
        public UserPrincipal getOwner() throws IOException {
            return path.readAttributes().owner();
        }

        @Override
        public PosixFileAttributes readAttributes() throws IOException {
            return path.readAttributes();
        }

        @Override
        public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
            path.setTimes(lastModifiedTime, lastAccessTime, createTime);
        }

        @Override
        public void setOwner(UserPrincipal owner) throws IOException {
            path.setOwner(owner);
        }

        @Override
        public void setGroup(GroupPrincipal group) throws IOException {
            path.setGroup(group);
        }

        @Override
        public void setPermissions(Set<PosixFilePermission> perms) throws IOException {
            path.setPermissions(perms);
        }
    }
}
