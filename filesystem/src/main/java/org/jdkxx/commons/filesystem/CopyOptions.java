package org.jdkxx.commons.filesystem;

import org.jdkxx.commons.filesystem.config.Messages;

import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class CopyOptions {
    public final boolean replaceExisting;
    public final Collection<? extends CopyOption> options;

    private CopyOptions(boolean replaceExisting,
                        Collection<? extends CopyOption> options) {
        this.replaceExisting = replaceExisting;
        this.options = options;
    }

    public Collection<OpenOption> toOpenOptions(OpenOption... additional) {
        List<OpenOption> openOptions = new ArrayList<>(options.size() + additional.length);
        for (CopyOption option : options) {
            if (option instanceof OpenOption) {
                openOptions.add((OpenOption) option);
            }
        }
        Collections.addAll(openOptions, additional);
        return openOptions;
    }

    public static CopyOptions forCopy(CopyOption... options) {
        boolean replaceExisting = false;
        for (CopyOption option : options) {
            if (option == StandardCopyOption.REPLACE_EXISTING) {
                replaceExisting = true;
            } else if (!isIgnoredCopyOption(option)) {
                throw Messages.fileSystemProvider().unsupportedCopyOption(option);
            }
        }
        return new CopyOptions(replaceExisting, Arrays.asList(options));
    }

    public static CopyOptions forMove(boolean sameFileSystem, CopyOption... options) {
        boolean replaceExisting = false;
        for (CopyOption option : options) {
            if (option == StandardCopyOption.REPLACE_EXISTING) {
                replaceExisting = true;
            } else if (!(option == StandardCopyOption.ATOMIC_MOVE && sameFileSystem) && !isIgnoredCopyOption(option)) {
                throw Messages.fileSystemProvider().unsupportedCopyOption(option);
            }
        }
        return new CopyOptions(replaceExisting, Arrays.asList(options));
    }

    private static boolean isIgnoredCopyOption(CopyOption option) {
        return option == LinkOption.NOFOLLOW_LINKS;
    }
}
