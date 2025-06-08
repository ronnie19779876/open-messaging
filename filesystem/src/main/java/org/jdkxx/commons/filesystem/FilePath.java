package org.jdkxx.commons.filesystem;

import org.jdkxx.commons.filesystem.config.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.StringJoiner;

public abstract class FilePath implements Path {
    private static final WatchEvent.Modifier[] NO_MODIFIERS = {};

    @Override
    public Path getFileName() {
        int nameCount = getNameCount();
        return nameCount == 0 ? null : getName(nameCount - 1);
    }

    @Override
    public @NotNull Path getName(int index) {
        return subpath(index, index + 1);
    }

    @Override
    public boolean startsWith(@NotNull String other) {
        return startsWith(getFileSystem().getPath(other));
    }

    @Override
    public boolean endsWith(@NotNull String other) {
        return endsWith(getFileSystem().getPath(other));
    }

    @Override
    public @NotNull Path resolveSibling(@NotNull Path other) {
        Objects.requireNonNull(other);
        Path parent = getParent();
        return parent == null ? other : parent.resolve(other);
    }

    @Override
    public @NotNull Path resolveSibling(@NotNull String other) {
        return resolveSibling(getFileSystem().getPath(other));
    }

    @Override
    public @NotNull WatchKey register(@NotNull WatchService watcher,
                                      WatchEvent.Kind<?> @NotNull ... events) throws IOException {
        return register(watcher, events, NO_MODIFIERS);
    }

    @Override
    public @NotNull Iterator<Path> iterator() {
        return new Iterator<Path>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < getNameCount();
            }

            @Override
            public Path next() {
                if (hasNext()) {
                    Path result = getName(index);
                    index++;
                    return result;
                }
                throw new NoSuchElementException();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public @NotNull File toFile() {
        throw Messages.unsupportedOperation(Path.class, "toFile");
    }

    @Override
    public @NotNull String toString() {
        return new StringJoiner(", ", FilePath.class.getSimpleName() + "[", "]")
                .toString();
    }

    public abstract String path();
}
