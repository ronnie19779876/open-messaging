package org.jdkxx.commons.configuration.options;

import org.jdkxx.commons.configuration.ConfigOption;

import static org.jdkxx.commons.configuration.ConfigOptions.key;

public class CoreOptions {
    public static final ConfigOption<String> TMP_DIRS =
            key("io.tmp.dirs")
                    .stringType()
                    .defaultValue(System.getProperty("java.io.tmpdir"));
}
