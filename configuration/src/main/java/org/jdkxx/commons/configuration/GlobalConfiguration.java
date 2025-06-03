package org.jdkxx.commons.configuration;

import static com.google.common.base.Preconditions.checkNotNull;

public final class GlobalConfiguration {
    // key separator character
    private static final String KEY_SEPARATOR = ".";

    // the keys whose values should be hidden
    private static final String[] SENSITIVE_KEYS =
            new String[]{
                    "password",
                    "secret",
                    "fs.azure.account.key",
                    "apikey",
                    "auth-params",
                    "service-key",
                    "token",
                    "basic-auth",
                    "jaas.config",
                    "http-headers"
            };

    // the hidden content to be displayed
    public static final String HIDDEN_CONTENT = "******";

    private GlobalConfiguration() {}

    /**
     * Check whether the key is a hidden key.
     *
     * @param key the config key
     */
    public static boolean isSensitive(String key) {
        checkNotNull(key, "key is null");
        final String keyInLower = key.toLowerCase();
        for (String hideKey : SENSITIVE_KEYS) {
            if (keyInLower.length() >= hideKey.length() && keyInLower.contains(hideKey)) {
                return true;
            }
        }
        return false;
    }
}
