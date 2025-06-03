package org.jdkxx.commons.filesystem.utils;

import java.nio.file.LinkOption;

public final class LinkOptionSupport {

    private LinkOptionSupport() {
        throw new IllegalStateException("cannot create instances of " + getClass().getName());
    }

    /**
     * Returns whether the given link options indicate that links should be followed.
     *
     * @param options The link options to check.
     * @return {@code false} if one of the given link options is {@link LinkOption#NOFOLLOW_LINKS}, or {@code true} otherwise.
     */
    public static boolean followLinks(LinkOption... options) {
        for (LinkOption option : options) {
            if (option == LinkOption.NOFOLLOW_LINKS) {
                return false;
            }
        }
        return true;
    }
}
