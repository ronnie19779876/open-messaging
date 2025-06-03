package org.jdkxx.commons.filesystem.utils;

import java.net.URI;
import java.net.URISyntaxException;

public final class URISupport {
    private URISupport() {
        throw new IllegalStateException("cannot create instances of " + getClass().getName()); //$NON-NLS-1$
    }

    /**
     * Utility method that calls {@link URI#URI(String, String, String, int, String, String, String)}, wrapping any thrown {@link URISyntaxException}
     * in an {@link IllegalArgumentException}.
     *
     * @param scheme   The scheme name.
     * @param userInfo The username and authorization information.
     * @param host     The host name.
     * @param port     The port number.
     * @param path     The path.
     * @param query    The query.
     * @param fragment The fragment.
     * @return The created URI.
     * @throws IllegalArgumentException If creating the URI caused a {@link URISyntaxException} to be thrown.
     * @see URI#create(String)
     */
    public static URI create(String scheme, String userInfo, String host, int port, String path, String query, String fragment) {
        try {
            if (path != null && !path.startsWith("/")) {
                path = "/" + path;
            }
            return new URI(scheme, userInfo, host, port, path, query, fragment);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Utility method that calls {@link URI#URI(String, String, String, String, String)}, wrapping any thrown {@link URISyntaxException} in an
     * {@link IllegalArgumentException}.
     *
     * @param scheme    The scheme name.
     * @param authority The authority.
     * @param path      The path.
     * @param query     The query.
     * @param fragment  The fragment.
     * @return The created URI.
     * @throws IllegalArgumentException If creating the URI caused a {@link URISyntaxException} to be thrown.
     * @see URI#create(String)
     */
    public static URI create(String scheme, String authority, String path, String query, String fragment) {
        try {
            return new URI(scheme, authority, path, query, fragment);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Utility method that calls {@link URI#URI(String, String, String, String)}, wrapping any thrown {@link URISyntaxException} in an
     * {@link IllegalArgumentException}.
     *
     * @param scheme   The scheme name.
     * @param host     The host name.
     * @param path     The path.
     * @param fragment The fragment.
     * @return The created URI.
     * @throws IllegalArgumentException If creating the URI caused a {@link URISyntaxException} to be thrown.
     * @see URI#create(String)
     */
    public static URI create(String scheme, String host, String path, String fragment) {
        try {
            return new URI(scheme, host, path, fragment);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Utility method that calls {@link URI#URI(String, String, String)}, wrapping any thrown {@link URISyntaxException} in an
     * {@link IllegalArgumentException}.
     *
     * @param scheme   The scheme name.
     * @param ssp      The scheme-specific part.
     * @param fragment The fragment.
     * @return The created URI.
     * @throws IllegalArgumentException If creating the URI caused a {@link URISyntaxException} to be thrown.
     * @see URI#create(String)
     */
    public static URI create(String scheme, String ssp, String fragment) {
        try {
            return new URI(scheme, ssp, fragment);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
