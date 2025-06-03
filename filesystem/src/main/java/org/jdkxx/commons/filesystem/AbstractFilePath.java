package org.jdkxx.commons.filesystem;

import org.jdkxx.commons.filesystem.config.Messages;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;

public abstract class AbstractFilePath extends FilePath {
    /* The full path. */
    private final String path;

    /* The offsets in the full path of all the separate name elements. */
    private int[] offsets;

    protected AbstractFilePath(String path, boolean normalized) {
        Objects.requireNonNull(path);
        this.path = normalized ? path : normalize(path);
    }

    @Override
    public boolean isAbsolute() {
        return path.startsWith(getFileSystem().getSeparator());
    }

    @Override
    public Path getRoot() {
        return isAbsolute() ? createPath(getFileSystem().getSeparator()) : null;
    }

    @Override
    public Path getParent() {
        initOffsets();
        String parentPath = parentPath();
        return parentPath != null ? createPath(parentPath) : null;
    }

    @Override
    public int getNameCount() {
        initOffsets();
        return offsets.length;
    }

    @Override
    public @NotNull Path subpath(int beginIndex, int endIndex) {
        initOffsets();
        if (beginIndex < 0 || beginIndex >= offsets.length
                || endIndex <= beginIndex || endIndex > offsets.length) {
            throw Messages.invalidRange(beginIndex, endIndex);
        }
        final int begin = begin(beginIndex);
        final int end = end(endIndex - 1);
        String subpath = path.substring(begin, end);
        if (subpath.endsWith("/")) {
            subpath = subpath.substring(0, subpath.length() - 1);
        }
        return createPath(subpath);
    }

    @Override
    public boolean startsWith(Path other) {
        if (getFileSystem() != other.getFileSystem() || getClass() != other.getClass()) {
            return false;
        }
        final AbstractFilePath that = (AbstractFilePath) other;
        if (that.path.isEmpty()) {
            return path.isEmpty();
        }
        String separator = getFileSystem().getSeparator();
        if (separator.equals(that.path)) {
            return isAbsolute();
        }
        if (!path.startsWith(that.path)) {
            return false;
        }
        return path.length() == that.path.length() || path.charAt(that.path.length()) == '/';
    }

    @Override
    public boolean endsWith(Path other) {
        if (getFileSystem() != other.getFileSystem() || getClass() != other.getClass()) {
            return false;
        }
        final AbstractFilePath that = (AbstractFilePath) other;
        if (that.path.isEmpty()) {
            return path.isEmpty();
        }
        if (that.isAbsolute()) {
            return path.equals(that.path);
        }
        if (!path.endsWith(that.path)) {
            return false;
        }
        return path.length() == that.path.length() || path.charAt(path.length() - that.path.length() - 1) == '/';
    }

    @Override
    public @NotNull Path normalize() {
        int count = getNameCount();
        if (count == 0) {
            return this;
        }
        Deque<String> nameElements = new ArrayDeque<>(count);
        int nonParentCount = 0;
        for (int i = 0; i < count; i++) {
            if (equalsNameAt(".", i)) {
                continue;
            }
            boolean isParent = equalsNameAt("..", i);
            // If this is a parent and there is at least one non-parent, pop it.
            if (isParent && nonParentCount > 0) {
                nameElements.pollLast();
                nonParentCount--;
                continue;
            }
            if (!isAbsolute() || !isParent) {
                // for non-absolute paths, this may add a parent if there are only parents, but that's OK.
                // Example: foo/../../bar will lead to ../bar
                // For absolute paths, any leading. will not be included, though.
                String nameElement = nameAt(i);
                nameElements.addLast(nameElement);
            }
            if (!isParent) {
                nonParentCount++;
            }
        }
        String separator = getFileSystem().getSeparator();
        StringBuilder sb = new StringBuilder(path.length());
        if (isAbsolute()) {
            sb.append(separator);
        }
        for (Iterator<String> i = nameElements.iterator(); i.hasNext(); ) {
            sb.append(i.next());
            if (i.hasNext()) {
                sb.append(separator);
            }
        }
        return createPath(sb.toString());
    }

    @Override
    public @NotNull Path resolve(@NotNull Path other) {
        String separator = getFileSystem().getSeparator();
        final AbstractFilePath that = checkPath(other);
        if (path.isEmpty() || that.isAbsolute()) {
            return that;
        }
        if (that.path.isEmpty()) {
            return this;
        }
        final String resolvedPath;
        if (path.endsWith(separator) || that.path.equals(separator)) {
            resolvedPath = path + that.path;
        } else {
            resolvedPath = path + separator + that.path;
        }
        return createPath(resolvedPath);
    }

    @Override
    public @NotNull Path relativize(@NotNull Path other) {
        final AbstractFilePath that = checkPath(other);
        if (this.equals(that)) {
            return createPath("");
        }
        if (isAbsolute() != that.isAbsolute()) {
            throw Messages.path().relativizeAbsoluteRelativeMismatch();
        }
        if (path.isEmpty()) {
            return other;
        }
        final int thisNameCount = getNameCount();
        final int thatNameCount = that.getNameCount();
        final int nameCount = Math.min(thisNameCount, thatNameCount);
        int index = 0;
        while (index < nameCount) {
            if (!equalsNameAt(that, index)) {
                break;
            }
            index++;
        }
        final int parentDirs = thisNameCount - index;
        int length = parentDirs * 3 - 1;
        if (index < thatNameCount) {
            length += that.path.length() - that.offsets[index] + 1;
        }
        final StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < parentDirs; i++) {
            sb.append("..");
            if (i < length) {
                sb.append(getFileSystem().getSeparator());
            }
            // Else don't add a trailing slash at the end
        }
        if (index < thatNameCount) {
            sb.append(that.path, that.offsets[index], that.path.length());
        }
        return createPath(sb.toString());
    }

    @Override
    public String path() {
        return this.path;
    }

    @Override
    public int compareTo(@NotNull Path other) {
        Objects.requireNonNull(other);
        final AbstractFilePath that = getClass().cast(other);
        return path.compareTo(that.path);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        AbstractFilePath other = (AbstractFilePath) obj;
        return getFileSystem() == other.getFileSystem() && path.equals(other.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public @NotNull String toString() {
        return path;
    }

    protected abstract FilePath createPath(String path);

    private boolean equalsNameAt(AbstractFilePath that, int index) {
        final int thisBegin = begin(index);
        final int thisEnd = end(index);
        final int thisLength = thisEnd - thisBegin;
        final int thatBegin = that.begin(index);
        final int thatEnd = that.end(index);
        final int thatLength = thatEnd - thatBegin;
        if (thisLength != thatLength) {
            return false;
        }
        return path.regionMatches(thisBegin, that.path, thatBegin, thisLength);
    }

    private AbstractFilePath checkPath(Path path) {
        Objects.requireNonNull(path);
        if (getClass().isInstance(path)) {
            return getClass().cast(path);
        }
        throw new ProviderMismatchException();
    }

    private String nameAt(int index) {
        initOffsets();
        if (index < 0 || index >= offsets.length) {
            throw Messages.invalidIndex(index);
        }

        final int begin = begin(index);
        final int end = end(index);
        return path.substring(begin, end);
    }

    private boolean equalsNameAt(String name, int index) {
        final int thisBegin = begin(index);
        final int thisEnd = end(index);
        final int thisLength = thisEnd - thisBegin;
        if (thisLength != name.length()) {
            return false;
        }
        return path.regionMatches(thisBegin, name, 0, thisLength);
    }

    private int begin(int index) {
        return offsets[index];
    }

    private int end(int index) {
        return index == offsets.length - 1 ? path.length() : offsets[index + 1] - 1;
    }

    private String parentPath() {
        initOffsets();
        final int count = offsets.length;
        if (count == 0) {
            return null;
        }
        final int end = offsets[count - 1] - 1;
        if (end <= 0) {
            // The parent is the root (possibly null)
            return rootPath();
        }
        return path.substring(0, end);
    }

    private String rootPath() {
        return isAbsolute() ? getFileSystem().getSeparator() : null;
    }

    private String normalize(String path) {
        if (path.isEmpty()) {
            return path;
        }
        StringBuilder sb = new StringBuilder(path.length());
        char prev = '\0';
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '/' && prev == '/') {
                continue;
            }
            if (c == '\0') {
                throw Messages.path().nulCharacterNotAllowed(path);
            }
            sb.append(c);
            prev = c;
        }
        return sb.toString();
    }

    private synchronized void initOffsets() {
        if (offsets == null) {
            String separator = getFileSystem().getSeparator();
            String path = this.path;
            if (path.endsWith(separator)) {
                path = path.substring(0, path.length() - 1);
            }
            if (separator.equals(path)) {
                offsets = new int[0];
                return;
            }
            boolean isAbsolute = isAbsolute();
            // At least one result for non-root paths
            int count = 1;
            int start = isAbsolute ? 1 : 0;
            while ((start = path.indexOf(separator, start)) != -1) {
                count++;
                start++;
            }
            int[] result = new int[count];
            start = isAbsolute ? 1 : 0;
            int index = 0;
            result[index++] = start;
            while ((start = path.indexOf(separator, start)) != -1) {
                start++;
                result[index++] = start;
            }
            offsets = result;
        }
    }
}
