package org.jdkxx.commons.filesystem.config;

import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public final class OpenOptions {
    public final boolean read;
    public final boolean write;
    public final boolean append;
    public final boolean create;
    public final boolean createNew;
    public final boolean deleteOnClose;

    public final Collection<? extends OpenOption> options;

    private OpenOptions(boolean read, boolean write, boolean append, boolean create, boolean createNew, boolean deleteOnClose,
                        Collection<? extends OpenOption> options) {

        this.read = read;
        this.write = write;
        this.append = append;
        this.create = create;
        this.createNew = createNew;
        this.deleteOnClose = deleteOnClose;

        this.options = options;
    }

    public static OpenOptions forNewInputStream(OpenOption... options) {
        return forNewInputStream(Arrays.asList(options));
    }

    public static OpenOptions forNewInputStream(Collection<? extends OpenOption> options) {
        if (options.isEmpty()) {
            return new OpenOptions(true, false, false, false, false, false, Collections.<OpenOption>emptySet());
        }

        boolean deleteOnClose = false;

        for (OpenOption option : options) {
            if (option == StandardOpenOption.DELETE_ON_CLOSE) {
                deleteOnClose = true;
            } else if (option != StandardOpenOption.READ && !isIgnoredOpenOption(option)) {
                throw Messages.fileSystemProvider().unsupportedOpenOption(option);
            }
        }

        return new OpenOptions(true, false, false, false, false, deleteOnClose, options);
    }

    public static OpenOptions forNewOutputStream(OpenOption... options) {
        return forNewOutputStream(Arrays.asList(options));
    }

    public static OpenOptions forNewOutputStream(Collection<? extends OpenOption> options) {
        if (options.isEmpty()) {
            // CREATE, TRUNCATE_EXISTING and WRITE, i.e., create, not createNew, and not append
            return new OpenOptions(false, true, false, true, false, false, Collections.<OpenOption>emptySet());
        }

        boolean append = false;
        boolean truncateExisting = false;
        boolean create = false;
        boolean createNew = false;
        boolean deleteOnClose = false;

        for (OpenOption option : options) {
            if (option == StandardOpenOption.APPEND) {
                append = true;
            } else if (option == StandardOpenOption.TRUNCATE_EXISTING) {
                truncateExisting = true;
            } else if (option == StandardOpenOption.CREATE) {
                create = true;
            } else if (option == StandardOpenOption.CREATE_NEW) {
                createNew = true;
            } else if (option == StandardOpenOption.DELETE_ON_CLOSE) {
                deleteOnClose = true;
            } else if (option != StandardOpenOption.WRITE && !isIgnoredOpenOption(option)) {
                throw Messages.fileSystemProvider().unsupportedOpenOption(option);
            }
        }

        // append and truncateExisting contradict each other
        if (append && truncateExisting) {
            throw Messages.fileSystemProvider().illegalOpenOptionCombination(options);
        }

        return new OpenOptions(false, true, append, create, createNew, deleteOnClose, options);
    }

    public static OpenOptions forNewByteChannel(Set<? extends OpenOption> options) {

        boolean read = false;
        boolean write = false;
        boolean append = false;
        boolean truncateExisting = false;
        boolean create = false;
        boolean createNew = false;
        boolean deleteOnClose = false;

        for (OpenOption option : options) {
            if (option == StandardOpenOption.READ) {
                read = true;
            } else if (option == StandardOpenOption.WRITE) {
                write = true;
            } else if (option == StandardOpenOption.APPEND) {
                append = true;
            } else if (option == StandardOpenOption.TRUNCATE_EXISTING) {
                truncateExisting = true;
            } else if (option == StandardOpenOption.CREATE) {
                create = true;
            } else if (option == StandardOpenOption.CREATE_NEW) {
                createNew = true;
            } else if (option == StandardOpenOption.DELETE_ON_CLOSE) {
                deleteOnClose = true;
            } else if (!isIgnoredOpenOption(option)) {
                throw Messages.fileSystemProvider().unsupportedOpenOption(option);
            }
        }

        // as per Files.newByteChannel, if none of these options is given, default to read
        if (!read && !write && !append) {
            read = true;
        }

        // APPEND may not be used with READ or TRUNCATE_EXISTING
        if (append && (read || truncateExisting)) {
            throw Messages.fileSystemProvider().illegalOpenOptionCombination(options);
        }

        if (append) {
            // !read, open in write-only mode
            write = true;
        }

        // although Files.newByteChannel supports READ and WRITE, SFTPFileSystem does not
        if (read && write) {
            throw Messages.fileSystemProvider().illegalOpenOptionCombination(options);
        }

        return new OpenOptions(read, write, append, create, createNew, deleteOnClose, options);
    }

    private static boolean isIgnoredOpenOption(OpenOption option) {
        return option == StandardOpenOption.SPARSE
                || option == StandardOpenOption.SYNC
                || option == StandardOpenOption.DSYNC
                || option == LinkOption.NOFOLLOW_LINKS;
    }
}
