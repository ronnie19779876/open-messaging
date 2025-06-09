package org.jdkxx.commons.filesystem;

import org.jdkxx.commons.filesystem.config.FileSystemEnvironment;
import org.jdkxx.commons.filesystem.config.Messages;
import org.jdkxx.commons.filesystem.utils.URISupport;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;

public abstract class AbstractFileSystemProvider<FS extends FileSystem> extends FileSystemProvider {
    protected static final String SFTP_SCHEME = "sftp";
    protected static final String S3_SCHEME = "s3";
    protected final FileSystems<FS> fileSystems = new FileSystems<>(this::createFileSystem);

    @Override
    public FileSystem getFileSystem(URI uri) {
        checkURI(uri, true, false);
        return getExistingFileSystem(uri);
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
    public @NotNull Path getPath(@NotNull URI uri) {
        checkURI(uri, true, true);
        FS fs = getExistingFileSystem(uri);
        return fs.getPath(uri.getPath());
    }

    abstract protected FS createFileSystem(URI uri, Map<String, ?> environment) throws IOException;

    abstract protected FS getExistingFileSystem(URI uri);

    protected URI normalizeWithUsername(URI uri, String username) {
        if (username == null && uri.getUserInfo() == null && uri.getPath() == null && uri.getQuery() == null && uri.getFragment() == null) {
            // nothing to normalize or add, return the URI
            return uri;
        }
        // no path, query or fragment
        return URISupport.create(uri.getScheme(), username, uri.getHost(), uri.getPort(), null, null, null);
    }

    protected URI normalizeWithoutPassword(URI uri) {
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
        return URISupport.create(uri.getScheme(), username, uri.getHost(), uri.getPort(), null, null, null);
    }

    protected void checkURI(URI uri, boolean allowUserInfo, boolean allowPath) {
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
    }
}
