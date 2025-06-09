package org.jdkxx.commons.filesystem;

import lombok.extern.slf4j.Slf4j;
import org.jdkxx.commons.filesystem.api.ChannelPool;
import org.jdkxx.commons.filesystem.api.FileAttributes;
import org.jdkxx.commons.filesystem.api.FileSystemChannel;
import org.jdkxx.commons.filesystem.config.Messages;
import org.jdkxx.commons.filesystem.config.OpenOptions;
import org.jdkxx.commons.filesystem.utils.PathMatcherSupport;
import org.jdkxx.commons.filesystem.utils.URISupport;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Slf4j
public abstract class AbstractFileSystem extends FileSystem {
    protected static final String CURRENT_DIR = ".";
    protected static final String PARENT_DIR = "..";
    protected static final Set<String> SUPPORTED_FILE_ATTRIBUTE_VIEWS = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList("basic", "owner", "posix")));
    private static final Set<String> BASIC_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "basic:lastModifiedTime", "basic:lastAccessTime", "basic:creationTime", "basic:size",
            "basic:isRegularFile", "basic:isDirectory", "basic:isSymbolicLink", "basic:isOther", "basic:fileKey")));
    private static final Set<String> OWNER_ATTRIBUTES =
            Collections.unmodifiableSet(new HashSet<>(Collections.singletonList("owner:owner")));
    private static final Set<String> POSIX_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "posix:lastModifiedTime", "posix:lastAccessTime", "posix:creationTime", "posix:size",
            "posix:isRegularFile", "posix:isDirectory", "posix:isSymbolicLink", "posix:isOther", "posix:fileKey",
            "posix:owner", "posix:group", "posix:permissions")));
    protected final FileSystemProvider provider;
    protected final URI uri;
    protected ChannelPool pool;
    protected final AtomicBoolean open = new AtomicBoolean(true);

    protected AbstractFileSystem(FileSystemProvider provider, URI uri) {
        this.provider = Objects.requireNonNull(provider);
        this.uri = Objects.requireNonNull(uri);
    }

    protected URI toUri(String path) {
        return URISupport.create(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), path,
                null, null);
    }

    protected void copyFile(FileSystemChannel sourceChannel,
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

    protected String getViewPrefix(String attributes) {
        String view;
        int pos = attributes.indexOf(':');
        if (pos == -1) {
            view = "basic";
        } else {
            view = attributes.substring(0, pos);
        }
        if (!SUPPORTED_FILE_ATTRIBUTE_VIEWS.contains(view)) {
            throw Messages.fileSystemProvider().unsupportedFileAttributeView(view);
        }
        return view;
    }

    protected Set<String> getAllowedAttributes(String attributes) {
        if (attributes.startsWith("basic:")) {
            return BASIC_ATTRIBUTES;
        } else if (attributes.startsWith("owner:")) {
            return OWNER_ATTRIBUTES;
        } else if (attributes.startsWith("posix:")) {
            return POSIX_ATTRIBUTES;
        } else {
            // should not occur
            throw Messages.fileSystemProvider().unsupportedFileAttributeView(attributes.substring(0, attributes.indexOf(':')));
        }
    }

    protected Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        String prefix = getViewPrefix(attributes);
        if (!attributes.startsWith(prefix)) {
            attributes = prefix + ":" + attributes;
        }
        Set<String> allowedAttributes = getAllowedAttributes(attributes);
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

    abstract protected PosixFileAttributes readAttributes(Path path, LinkOption... options) throws IOException;

    protected Map<String, Object> getAttributeMap(String attributes, Set<String> allowedAttributes) {
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

    protected List<FileEntry> listFileEntry(FileSystemChannel channel, String path) throws IOException {
        List<FileEntry> entries = channel.listFiles(path);
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
            FileAttributes attributes = channel.readAttributes(path, true);
            if (!attributes.isDir()) {
                throw new NotDirectoryException(path);
            }
        }
        return entries;
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return File.separator;
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return SUPPORTED_FILE_ATTRIBUTE_VIEWS;
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        final Pattern pattern = PathMatcherSupport.toPattern(syntaxAndPattern);
        return path -> pattern.matcher(path.toString()).matches();
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        throw Messages.unsupportedOperation(FileSystem.class, "getFileStores");
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw Messages.unsupportedOperation(FileSystem.class, "getUserPrincipalLookupService");
    }

    @Override
    public WatchService newWatchService() throws IOException {
        throw Messages.unsupportedOperation(FileSystem.class, "newWatchService");
    }

    @Override
    public void close() throws IOException {
        if (open.getAndSet(false)) {
            //provider.removeFileSystem(uri);
            if (pool != null) {
                pool.close();
            }
        }
    }
}
