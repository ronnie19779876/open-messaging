package org.jdkxx.commons.filesystem.sftp;

import org.jdkxx.commons.filesystem.config.UTF8Control;

import java.util.ResourceBundle;

public final class SftpMessages {

    private static final ResourceBundle BUNDLE = getBundle();

    private SftpMessages() {
        throw new IllegalStateException("cannot create instances of " + getClass().getName());
    }

    private static ResourceBundle getBundle() {
        final String baseName = "org.jdkxx.commons.filesystem.sftp.fs";
        try {
            return ResourceBundle.getBundle(baseName, UTF8Control.INSTANCE);
        } catch (UnsupportedOperationException e) {
            // Java 9 or up; defaults to UTF-8
            return ResourceBundle.getBundle(baseName);
        }
    }

    private static synchronized String getMessage(String key) {
        return BUNDLE.getString(key);
    }

    private static String getMessage(String key, Object... args) {
        String format = getMessage(key);
        return String.format(format, args);
    }

    static String copyOfSymbolicLinksAcrossFileSystemsNotSupported() {
        return getMessage("copyOfSymbolicLinksAcrossFileSystemsNotSupported");
    }

    static String clientConnectionWaitTimeoutExpired() {
        return getMessage("clientConnectionWaitTimeoutExpired");
    }

    static String createdInputStream(String path) {
        return getMessage("log.createdInputStream", path);
    }

    static String closedInputStream(String path) {
        return getMessage("log.closedInputStream", path);
    }

    static String createdOutputStream(String path) {
        return getMessage("log.createdOutputStream", path);
    }

    static String closedOutputStream(String path) {
        return getMessage("log.closedOutputStream", path);
    }
}
