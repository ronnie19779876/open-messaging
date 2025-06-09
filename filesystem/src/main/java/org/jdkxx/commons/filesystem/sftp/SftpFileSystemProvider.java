package org.jdkxx.commons.filesystem.sftp;

import lombok.extern.slf4j.Slf4j;
import org.jdkxx.commons.filesystem.AbstractFileSystemProvider;
import org.jdkxx.commons.filesystem.config.FileSystemEnvironment;
import org.jdkxx.commons.filesystem.config.Messages;

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
public class SftpFileSystemProvider extends AbstractFileSystemProvider<SftpFileSystem> {
    @Override
    public String getScheme() {
        return SFTP_SCHEME;
    }

    @Override
    protected SftpFileSystem createFileSystem(URI uri, Map<String, ?> environment) throws IOException {
        return new SftpFileSystem(this, uri, (FileSystemEnvironment) environment);
    }

    @Override
    protected SftpFileSystem getExistingFileSystem(URI uri) {
        URI normalizedURI = normalizeWithoutPassword(uri);
        return fileSystems.get(normalizedURI);
    }

    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        return toSftpFilePath(path).newInputStream(options);
    }

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        return toSftpFilePath(path).newOutputStream(options);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return toSftpFilePath(path).newByteChannel(options, attrs);
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return toSftpFilePath(dir).newDirectoryStream(filter);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        toSftpFilePath(dir).createDirectory(attrs);
    }

    @Override
    public void delete(Path path) throws IOException {
        toSftpFilePath(path).delete();
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        return toSftpFilePath(link).readSymbolicLink();
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        toSftpFilePath(source).copy(toSftpFilePath(target), options);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        toSftpFilePath(source).move(toSftpFilePath(target), options);
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        return toSftpFilePath(path).isSameFile(path2);
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        return toSftpFilePath(path).isHidden();
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        return toSftpFilePath(path).getFileStore();
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        toSftpFilePath(path).checkAccess(modes);
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        Objects.requireNonNull(type);
        if (type == BasicFileAttributeView.class) {
            return type.cast(new AttributeView("basic", toSftpFilePath(path)));
        }
        if (type == FileOwnerAttributeView.class) {
            return type.cast(new AttributeView("owner", toSftpFilePath(path)));
        }
        if (type == PosixFileAttributeView.class) {
            return type.cast(new AttributeView("posix", toSftpFilePath(path)));
        }
        return null;
    }

    private static final class AttributeView implements PosixFileAttributeView {
        private final String name;
        private final SftpFilePath path;

        private AttributeView(String name, SftpFilePath path) {
            this.name = Objects.requireNonNull(name);
            this.path = Objects.requireNonNull(path);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public UserPrincipal getOwner() throws IOException {
            return readAttributes().owner();
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

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        if (type == BasicFileAttributes.class || type == PosixFileAttributes.class) {
            return type.cast(toSftpFilePath(path).readAttributes(options));
        }
        throw Messages.fileSystemProvider().unsupportedFileAttributesType(type);
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return toSftpFilePath(path).readAttributes(attributes, options);
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        toSftpFilePath(path).setAttribute(attribute, value, options);
    }

    private static SftpFilePath toSftpFilePath(Path path) {
        Objects.requireNonNull(path);
        if (path instanceof SftpFilePath) {
            return (SftpFilePath) path;
        }
        throw new ProviderMismatchException();
    }
}
