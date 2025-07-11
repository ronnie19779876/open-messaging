package org.jdkxx.commons.filesystem.config;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.regex.PatternSyntaxException;

public final class Messages {
    private static final String RESOURCE_BUNDLE_NAME = "org.jdkxx.commons.filesystem.fs";
    private static final ResourceBundle BUNDLE = getBundle();

    private Messages() {
        throw new IllegalStateException("cannot create instances of " + getClass().getName());
    }

    private static ResourceBundle getBundle() {
        try {
            return ResourceBundle.getBundle(RESOURCE_BUNDLE_NAME, UTF8Control.INSTANCE);
        } catch (UnsupportedOperationException e) {
            // Java 9 or up; defaults to UTF-8
            return ResourceBundle.getBundle(RESOURCE_BUNDLE_NAME);
        }
    }

    private static synchronized String getMessage(String key) {
        return BUNDLE.getString(key);
    }

    private static String getMessage(String key, Object... args) {
        String format = getMessage(key);
        return String.format(format, args);
    }

    /**
     * Creates an exception that can be thrown for an invalid index.
     *
     * @param index The index that was invalid.
     * @return The created exception.
     */
    public static IllegalArgumentException invalidIndex(int index) {
        return new IllegalArgumentException(getMessage("invalidIndex", index));
    }

    /**
     * Creates an exception that can be thrown for an invalid range.
     *
     * @param beginIndex The beginning index of the range, inclusive.
     * @param endIndex   The end index of the range, exclusive.
     * @return The created exception.
     */
    public static IllegalArgumentException invalidRange(int beginIndex, int endIndex) {
        return new IllegalArgumentException(getMessage("invalidRange", beginIndex, endIndex));
    }

    /**
     * Creates an {@code UnsupportedOperationException} that can be thrown if an I/O operation, such as
     * {@link BasicFileAttributeView#setTimes(FileTime, FileTime, FileTime)}, is not supported.
     *
     * @param cls       The class defining the operation, e.g. {@link BasicFileAttributeView}.
     * @param operation The name of the operation (method).
     * @return The created exception.
     */
    public static UnsupportedOperationException unsupportedOperation(Class<?> cls, String operation) {
        return new UnsupportedOperationException(getMessage("unsupportedOperation", cls.getSimpleName(), operation));
    }

    /**
     * Returns an object for providing translated messages and exceptions for paths.
     *
     * @return An object for providing translated messages and exceptions for paths.
     */
    public static PathMessages path() {
        return PathMessages.INSTANCE;
    }

    /**
     * A utility class for providing translated messages and exceptions for paths.
     *
     * @author Rob Spoor
     */
    public static final class PathMessages {

        private static final PathMessages INSTANCE = new PathMessages();

        private PathMessages() {
            super();
        }

        /**
         * Creates an exception that can be thrown if a path contains a nul ({@code \0}) character.
         *
         * @param path The path that contains a nul character.
         * @return The created exception.
         */
        public InvalidPathException nulCharacterNotAllowed(String path) {
            return new InvalidPathException(path, getMessage("path.nulCharacterNotAllowed"));
        }

        /**
         * Creates an exception that can be thrown if {@link Path#relativize(Path)} is called and one of the paths is absolute and the other is not.
         *
         * @return The created exception.
         */
        public IllegalArgumentException relativizeAbsoluteRelativeMismatch() {
            return new IllegalArgumentException(getMessage("path.relativizeAbsoluteRelativeMismatch"));
        }
    }

    /**
     * Returns an object for providing translated messages and exceptions for path matchers.
     *
     * @return An object for providing translated messages and exceptions for path matchers.
     */
    public static PathMatcherMessages pathMatcher() {
        return PathMatcherMessages.INSTANCE;
    }

    /**
     * A utility class for providing translated messages and exceptions for path matchers.
     *
     * @author Rob Spoor
     */
    public static final class PathMatcherMessages {

        private static final PathMatcherMessages INSTANCE = new PathMatcherMessages();

        private PathMatcherMessages() {
            super();
        }

        /**
         * Returns an object for providing translated messages and exceptions for globs.
         *
         * @return An object for providing translated messages and exceptions for globs.
         */
        public PathMatcherGlobMessages glob() {
            return PathMatcherGlobMessages.INSTANCE;
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystem#getPathMatcher(String)} is called with a string that does not contain a
         * syntax part.
         *
         * @param syntaxAndInput The input to {@link FileSystem#getPathMatcher(String)} that's missing the syntax.
         * @return The created exception.
         */
        public IllegalArgumentException syntaxNotFound(String syntaxAndInput) {
            return new IllegalArgumentException(getMessage("pathMatcher.syntaxNotFound", syntaxAndInput));
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystem#getPathMatcher(String)} is called with an unsupported syntax.
         *
         * @param syntax The unsupported syntax.
         * @return The created exception.
         */
        public UnsupportedOperationException unsupportedPathMatcherSyntax(String syntax) {
            return new UnsupportedOperationException(getMessage("pathMatcher.unsupportedPathMatcherSyntax", syntax));
        }
    }

    /**
     * A utility class for providing translated messages and exceptions for globs.
     *
     * @author Rob Spoor
     */
    public static final class PathMatcherGlobMessages {

        private static final PathMatcherGlobMessages INSTANCE = new PathMatcherGlobMessages();

        private PathMatcherGlobMessages() {
            super();
        }

        /**
         * Creates an exception that can be thrown if a glob contains nested groups.
         *
         * @param glob  The glob that contains nested groups.
         * @param index The index at which the (first) nested group is encountered.
         * @return The created exception.
         */
        public PatternSyntaxException nestedGroupsNotSupported(String glob, int index) {
            return new PatternSyntaxException(getMessage("pathMatcher.glob.nestedGroupsNotSupported"), glob, index);
        }

        /**
         * Creates an exception that can be thrown if a glob contains a group end character (<code>&#125;</code>) that does not close a group.
         *
         * @param glob  The glob that contains the unexpected group end character.
         * @param index The index at which the unexpected group end character occurs.
         * @return The created exception.
         */
        public PatternSyntaxException unexpectedGroupEnd(String glob, int index) {
            return new PatternSyntaxException(getMessage("pathMatcher.glob.unexpectedGroupEnd"), glob, index);
        }

        /**
         * Creates an exception that can be thrown if a glob missing a group end character (<code>&#125;</code>).
         *
         * @param glob The glob that misses a group end character.
         * @return The created exception.
         */
        public PatternSyntaxException missingGroupEnd(String glob) {
            return new PatternSyntaxException(getMessage("pathMatcher.glob.missingGroupEnd"), glob, glob.length());
        }

        /**
         * Creates an exception that can be thrown if a glob contains nested classes.
         *
         * @param glob  The glob that contains nested classes.
         * @param index The index at which the (first) nested class is encountered.
         * @return The created exception.
         */
        public PatternSyntaxException nestedClassesNotSupported(String glob, int index) {
            return new PatternSyntaxException(getMessage("pathMatcher.glob.nestedClassesNotSupported"), glob, index);
        }

        /**
         * Creates an exception that can be thrown if a glob contains a class end character ({@code ]}) that does not close a class.
         *
         * @param glob  The glob that contains the unexpected class end character.
         * @param index The index at which the unexpected class end character occurs.
         * @return The created exception.
         */
        public PatternSyntaxException unexpectedClassEnd(String glob, int index) {
            return new PatternSyntaxException(getMessage("pathMatcher.glob.unexpectedClassEnd"), glob, index);
        }

        /**
         * Creates an exception that can be thrown if a glob missing a class end character ({@code ]}).
         *
         * @param glob The glob that misses a class end character.
         * @return The created exception.
         */
        public PatternSyntaxException missingClassEnd(String glob) {
            return new PatternSyntaxException(getMessage("pathMatcher.glob.missingClassEnd"), glob, glob.length());
        }

        /**
         * Creates an exception that can be thrown if a glob contains a separator (e.g. {@code /}) in a class.
         *
         * @param glob  The glob that contains a separator in a class.
         * @param index The index at which the separator occurs.
         * @return The created exception.
         */
        public PatternSyntaxException separatorNotAllowedInClass(String glob, int index) {
            return new PatternSyntaxException(getMessage("pathMatcher.glob.separatorNotAllowedInClass"), glob, index);
        }

        /**
         * Creates an exception that can be thrown if a glob contains an escape character ({@code \}) that does not escape an actual glob meta-character.
         *
         * @param glob  The glob that contains an unexpected escape character.
         * @param index The index at which the unexpected escape character occurs.
         * @return The created exception.
         */
        public PatternSyntaxException unescapableChar(String glob, int index) {
            return new PatternSyntaxException(getMessage("pathMatcher.glob.unescapableChar"), glob, index);
        }
    }

    /**
     * Returns an object for providing translated messages and exceptions for file stores.
     *
     * @return An object for providing translated messages and exceptions for file stores.
     */
    public static FileStoreMessages fileStore() {
        return FileStoreMessages.INSTANCE;
    }

    /**
     * A utility class for providing translated messages and exceptions for file stores.
     *
     * @author Rob Spoor
     */
    public static final class FileStoreMessages {

        private static final FileStoreMessages INSTANCE = new FileStoreMessages();

        private FileStoreMessages() {
            super();
        }

        /**
         * Creates an exception that can be thrown if {@link FileStore#getAttribute(String)} is called with an unsupported attribute.
         *
         * @param attribute The unsupported attribute.
         * @return The created exception.
         */
        public UnsupportedOperationException unsupportedAttribute(String attribute) {
            return new UnsupportedOperationException(getMessage("fileStore.unsupportedAttribute", attribute));
        }
    }

    /**
     * Returns an object for providing translated messages and exceptions for file system providers.
     *
     * @return An object for providing translated messages and exceptions for file system providers.
     */
    public static FileSystemProviderMessages fileSystemProvider() {
        return FileSystemProviderMessages.INSTANCE;
    }

    /**
     * A utility class for providing translated messages and exceptions for file system providers.
     *
     * @author Rob Spoor
     */
    public static final class FileSystemProviderMessages {

        private static final FileSystemProviderMessages INSTANCE = new FileSystemProviderMessages();

        private FileSystemProviderMessages() {
            super();
        }

        /**
         * Returns an object for providing translated messages and exceptions for file system provider properties.
         *
         * @return An object for providing translated messages and exceptions for file system provider properties.
         */
        public FileSystemProviderEnvMessages env() {
            return FileSystemProviderEnvMessages.INSTANCE;
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystemProvider#newByteChannel(Path, Set, FileAttribute...)} or a similar method is
         * called with a directory.
         *
         * @param dir The directory path.
         * @return The created exception.
         */
        public FileSystemException isDirectory(String dir) {
            return new FileSystemException(dir, null, getMessage("fileSystemProvider.isDirectory"));
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystemProvider#newByteChannel(Path, Set, FileAttribute...)} or a similar method is
         * called with an illegal combination of open options.
         *
         * @param options The illegal combination of open options.
         * @return The created exception.
         */
        public IllegalArgumentException illegalOpenOptionCombination(OpenOption... options) {
            return illegalOpenOptionCombination(Arrays.asList(options));
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystemProvider#newByteChannel(Path, Set, FileAttribute...)} or a similar method is
         * called with an illegal combination of open options.
         *
         * @param options The illegal combination of options.
         * @return The created exception.
         */
        public IllegalArgumentException illegalOpenOptionCombination(Collection<? extends OpenOption> options) {
            return new IllegalArgumentException(getMessage("fileSystemProvider.illegalOpenOptionCombination", options));
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystemProvider#copy(Path, Path, CopyOption...)} or
         * {@link FileSystemProvider#move(Path, Path, CopyOption...)} is called with an illegal combination of copy options.
         *
         * @param options The illegal combination of copy options.
         * @return The created exception.
         */
        public IllegalArgumentException illegalCopyOptionCombination(CopyOption... options) {
            return illegalCopyOptionCombination(Arrays.asList(options));
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystemProvider#copy(Path, Path, CopyOption...)} or
         * {@link FileSystemProvider#move(Path, Path, CopyOption...)} is called with an illegal combination of copy options.
         *
         * @param options The illegal combination of copy options.
         * @return The created exception.
         */
        public IllegalArgumentException illegalCopyOptionCombination(Collection<? extends CopyOption> options) {
            return new IllegalArgumentException(getMessage("fileSystemProvider.illegalCopyOptionCombination", options));
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystemProvider#readAttributes(Path, Class, LinkOption...)} is called with an
         * unsupported file attributes type.
         *
         * @param type The unsupported type.
         * @return The created exception.
         */
        public UnsupportedOperationException unsupportedFileAttributesType(Class<? extends BasicFileAttributes> type) {
            return new UnsupportedOperationException(getMessage("fileSystemProvider.unsupportedFileAttributesType", type.getName()));
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystemProvider#readAttributes(Path, String, LinkOption...)} or
         * {@link FileSystemProvider#setAttribute(Path, String, Object, LinkOption...)} is called with an unsupported file attribute view.
         *
         * @param view The unsupported view.
         * @return The created exception.
         */
        public UnsupportedOperationException unsupportedFileAttributeView(String view) {
            return new UnsupportedOperationException(getMessage("fileSystemProvider.unsupportedFileAttributeView", view));
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystemProvider#newByteChannel(Path, Set, FileAttribute...)},
         * {@link FileSystemProvider#createDirectory(Path, FileAttribute...)} or a similar method is called with an unsupported file attribute.
         *
         * @param attribute The unsupported attribute.
         * @return The created exception.
         */
        public UnsupportedOperationException unsupportedCreateFileAttribute(String attribute) {
            return new UnsupportedOperationException(getMessage("fileSystemProvider.unsupportedFileAttribute", attribute));
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystemProvider#readAttributes(Path, String, LinkOption...)} or
         * {@link FileSystemProvider#setAttribute(Path, String, Object, LinkOption...)} is called with an unsupported file attribute.
         *
         * @param attribute The unsupported attribute.
         * @return The created exception.
         */
        public IllegalArgumentException unsupportedFileAttribute(String attribute) {
            return new IllegalArgumentException(getMessage("fileSystemProvider.unsupportedFileAttribute", attribute));
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystemProvider#newByteChannel(Path, Set, FileAttribute...)} or a similar method is
         * called with an unsupported open option.
         *
         * @param option The unsupported open option.
         * @return The created exception.
         */
        public UnsupportedOperationException unsupportedOpenOption(OpenOption option) {
            return new UnsupportedOperationException(getMessage("fileSystemProvider.unsupportedOpenOption", option));
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystemProvider#copy(Path, Path, CopyOption...)} or
         * {@link FileSystemProvider#move(Path, Path, CopyOption...)} is called with an unsupported copy option.
         *
         * @param option The unsupported copy option.
         * @return The created exception.
         */
        public UnsupportedOperationException unsupportedCopyOption(CopyOption option) {
            return new UnsupportedOperationException(getMessage("fileSystemProvider.unsupportedCopyOption", option));
        }
    }

    /**
     * A utility class for providing translated messages and exceptions for file system provider properties.
     *
     * @author Rob Spoor
     */
    public static final class FileSystemProviderEnvMessages {

        private static final FileSystemProviderEnvMessages INSTANCE = new FileSystemProviderEnvMessages();

        private FileSystemProviderEnvMessages() {
            super();
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystemProvider#newFileSystem(URI, Map)} or
         * {@link FileSystemProvider#newFileSystem(Path, Map)} is called with a required property missing.
         *
         * @param property The name of the missing property.
         * @return The created exception.
         */
        public IllegalArgumentException missingProperty(String property) {
            return new IllegalArgumentException(getMessage("fileSystemProvider.env.missingProperty", property));
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystemProvider#newFileSystem(URI, Map)} or
         * {@link FileSystemProvider#newFileSystem(Path, Map)} is called with an invalid value for a property.
         *
         * @param property The name of the property.
         * @param value    The invalid value.
         * @return The created exception.
         */
        public IllegalArgumentException invalidProperty(String property, Object value) {
            return new IllegalArgumentException(getMessage("fileSystemProvider.env.invalidProperty", property, value));
        }

        /**
         * Creates an exception that can be thrown if {@link FileSystemProvider#newFileSystem(URI, Map)} or
         * {@link FileSystemProvider#newFileSystem(Path, Map)} is called with an invalid combination of properties.
         *
         * @param properties The names of the properties.
         * @return The created exception.
         */
        public IllegalArgumentException invalidPropertyCombination(Collection<String> properties) {
            return new IllegalArgumentException(getMessage("fileSystemProvider.env.invalidPropertyCombination", properties));
        }
    }

    /**
     * Returns an object for providing translated messages and exceptions for directory streams.
     *
     * @return An object for providing translated messages and exceptions for directory streams.
     */
    public static DirectoryStreamMessages directoryStream() {
        return DirectoryStreamMessages.INSTANCE;
    }

    /**
     * A utility class for providing translated messages and exceptions for directory streams.
     *
     * @author Rob Spoor
     */
    public static final class DirectoryStreamMessages {

        private static final DirectoryStreamMessages INSTANCE = new DirectoryStreamMessages();

        private DirectoryStreamMessages() {
            super();
        }

        /**
         * Creates an exception that can be closed if {@link DirectoryStream#iterator()} is called on a closed directory stream.
         *
         * @return The created exception.
         */
        public IllegalStateException closed() {
            return new IllegalStateException(getMessage("directoryStream.closed"));
        }

        /**
         * Creates an exception that can be closed if {@link DirectoryStream#iterator()} is called after the iterator was already returned.
         *
         * @return The created exception.
         */
        public IllegalStateException iteratorAlreadyReturned() {
            return new IllegalStateException(getMessage("directoryStream.iteratorAlreadyReturned"));
        }
    }

    /**
     * Returns an object for providing translated messages and exceptions for file channels.
     *
     * @return An object for providing translated messages and exceptions for file channels.
     */
    public static FileChannelMessages fileChannel() {
        return FileChannelMessages.INSTANCE;
    }

    /**
     * A utility class for providing translated messages and exceptions for file channels.
     *
     * @author Rob Spoor
     */
    public static final class FileChannelMessages {

        private static final FileChannelMessages INSTANCE = new FileChannelMessages();

        private FileChannelMessages() {
            super();
        }

        /**
         * Creates an exception that can be thrown if {@link FileChannel#transferTo(long, long, WritableByteChannel)} or
         * {@link FileChannel#transferFrom(ReadableByteChannel, long, long)} is called with a negative count.
         *
         * @param count The negative count.
         * @return The created exception.
         */
        public IllegalArgumentException negativeCount(long count) {
            return new IllegalArgumentException(getMessage("fileChannel.negativeCount", count));
        }

        /**
         * Creates an exception that can be thrown if {@link FileChannel#read(ByteBuffer, long)}, {@link FileChannel#write(ByteBuffer, long)},
         * {@link FileChannel#transferTo(long, long, WritableByteChannel)} or {@link FileChannel#transferFrom(ReadableByteChannel, long, long)}
         * is called with a negative position.
         *
         * @param position The negative position.
         * @return The created exception.
         */
        public IllegalArgumentException negativePosition(long position) {
            return new IllegalArgumentException(getMessage("fileChannel.negativePosition", position));
        }
    }

    /**
     * Returns an object for providing translated messages and exceptions for (seekable) byte channels.
     *
     * @return An object for providing translated messages and exceptions for (seekable) byte channels.
     */
    public static ByteChannelMessages byteChannel() {
        return ByteChannelMessages.INSTANCE;
    }

    /**
     * A utility class for providing translated messages and exceptions for (seekable) byte channels.
     *
     * @author Rob Spoor
     */
    public static final class ByteChannelMessages {

        private static final ByteChannelMessages INSTANCE = new ByteChannelMessages();

        private ByteChannelMessages() {
            super();
        }

        /**
         * Creates an exception that can be thrown if {@link SeekableByteChannel#position(long)} is called with a negative position.
         *
         * @param position The negative position.
         * @return The created exception.
         */
        public IllegalArgumentException negativePosition(long position) {
            return new IllegalArgumentException(getMessage("byteChannel.negativePosition", position));
        }

        /**
         * Creates an exception that can be thrown if {@link SeekableByteChannel#truncate(long)} is called with a negative size.
         *
         * @param size The negative size.
         * @return The created exception.
         */
        public IllegalArgumentException negativeSize(long size) {
            return new IllegalArgumentException(getMessage("byteChannel.negativeSize", size));
        }
    }

    /**
     * Returns an object for providing translated messages and exceptions for URI validation.
     *
     * @return An object for providing translated messages and exceptions for URI validation.
     */
    public static URIMessages uri() {
        return URIMessages.INSTANCE;
    }

    /**
     * A utility class for providing translated messages and exceptions for URI validation.
     *
     * @author Rob Spoor
     */
    public static final class URIMessages {

        private static final URIMessages INSTANCE = new URIMessages();

        private URIMessages() {
            super();
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) has an invalid scheme.
         *
         * @param uri            The URI with the invalid scheme.
         * @param expectedScheme The expected scheme.
         * @return The created exception.
         * @see URI#getScheme()
         */
        public IllegalArgumentException invalidScheme(URI uri, String expectedScheme) {
            return new IllegalArgumentException(getMessage("uri.invalidScheme", expectedScheme, uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) is not absolute.
         *
         * @param uri The non-absolute URI.
         * @return The created exception.
         * @see URI#isAbsolute()
         */
        public IllegalArgumentException notAbsolute(URI uri) {
            return new IllegalArgumentException(getMessage("uri.notAbsolute", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) is not hierarchical.
         * This is the same as being opaque.
         *
         * @param uri The non-hierarchical URI.
         * @return The created exception.
         * @see URI#isOpaque()
         */
        public IllegalArgumentException notHierarchical(URI uri) {
            return new IllegalArgumentException(getMessage("uri.notHierarchical", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) has an authority component.
         *
         * @param uri The URI with the authority component.
         * @return The created exception.
         * @see URI#getAuthority()
         * @see #hasNoAuthority(URI)
         */
        public IllegalArgumentException hasAuthority(URI uri) {
            return new IllegalArgumentException(getMessage("uri.hasAuthority", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) has a fragment component.
         *
         * @param uri The URI with the fragment component.
         * @return The created exception.
         * @see URI#getFragment()
         * @see #hasNoFragment(URI)
         */
        public IllegalArgumentException hasFragment(URI uri) {
            return new IllegalArgumentException(getMessage("uri.hasFragment", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) has a host component.
         *
         * @param uri The URI with the host component.
         * @return The created exception.
         * @see URI#getHost()
         * @see #hasNoHost(URI)
         */
        public IllegalArgumentException hasHost(URI uri) {
            return new IllegalArgumentException(getMessage("uri.hasHost", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) has a path component.
         *
         * @param uri The URI with the path component.
         * @return The created exception.
         * @see URI#getPath()
         * @see #hasNoHost(URI)
         */
        public IllegalArgumentException hasPath(URI uri) {
            return new IllegalArgumentException(getMessage("uri.hasPath", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) has a port number.
         *
         * @param uri The URI with the port number.
         * @return The created exception.
         * @see URI#getPort()
         * @see #hasNoPort(URI)
         */
        public IllegalArgumentException hasPort(URI uri) {
            return new IllegalArgumentException(getMessage("uri.hasPort", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) has a query component.
         *
         * @param uri The URI with the query component.
         * @return The created exception.
         * @see URI#getQuery()
         * @see #hasNoQuery(URI)
         */
        public IllegalArgumentException hasQuery(URI uri) {
            return new IllegalArgumentException(getMessage("uri.hasQuery", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) has a user-info component.
         *
         * @param uri The URI with the user-info component.
         * @return The created exception.
         * @see URI#getUserInfo()
         * @see #hasNoUserInfo(URI)
         */
        public IllegalArgumentException hasUserInfo(URI uri) {
            return new IllegalArgumentException(getMessage("uri.hasUserInfo", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) does not have an authority
         * component.
         *
         * @param uri The URI without the authority component.
         * @return The created exception.
         * @see URI#getAuthority()
         * @see #hasAuthority(URI)
         */
        public IllegalArgumentException hasNoAuthority(URI uri) {
            return new IllegalArgumentException(getMessage("uri.hasNoAuthority", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) does not have a fragment
         * component.
         *
         * @param uri The URI without the fragment component.
         * @return The created exception.
         * @see URI#getFragment()
         * @see #hasFragment(URI)
         */
        public IllegalArgumentException hasNoFragment(URI uri) {
            return new IllegalArgumentException(getMessage("uri.hasNoFragment", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) does not have a host component.
         *
         * @param uri The URI without the host component.
         * @return The created exception.
         * @see URI#getHost()
         * @see #hasHost(URI)
         */
        public IllegalArgumentException hasNoHost(URI uri) {
            return new IllegalArgumentException(getMessage("uri.hasNoHost", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) does not have a path component.
         *
         * @param uri The URI without the path component.
         * @return The created exception.
         * @see URI#getPath()
         * @see #hasPath(URI)
         */
        public IllegalArgumentException hasNoPath(URI uri) {
            return new IllegalArgumentException(getMessage("uri.hasNoPath", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) does not have a port number.
         *
         * @param uri The URI without the port number.
         * @return The created exception.
         * @see URI#getPort()
         * @see #hasPort(URI)
         */
        public IllegalArgumentException hasNoPort(URI uri) {
            return new IllegalArgumentException(getMessage("uri.hasNoPort", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) does not have a query component.
         *
         * @param uri The URI without the query component.
         * @return The created exception.
         * @see URI#getQuery()
         * @see #hasQuery(URI)
         */
        public IllegalArgumentException hasNoQuery(URI uri) {
            return new IllegalArgumentException(getMessage("uri.hasNoQuery", uri));
        }

        /**
         * Creates an exception that can be thrown if a URI (e.g., used for {@link FileSystemProvider#getPath(URI)}) does not have a user-info
         * component.
         *
         * @param uri The URI without the user-info component.
         * @return The created exception.
         * @see URI#getUserInfo()
         * @see #hasUserInfo(URI)
         */
        public IllegalArgumentException hasNoUserInfo(URI uri) {
            return new IllegalArgumentException(getMessage("uri.hasNoUserInfo", uri));
        }
    }
}
