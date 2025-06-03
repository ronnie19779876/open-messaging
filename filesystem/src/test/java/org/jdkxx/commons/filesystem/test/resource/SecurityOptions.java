package org.jdkxx.commons.filesystem.test.resource;

import org.jdkxx.commons.configuration.ConfigOption;
import org.jdkxx.commons.configuration.ConfigOptions;

public class SecurityOptions {
    public static ConfigOption<String> S3_PROTOCOL = ConfigOptions
            .key("s3.protocol")
            .stringType()
            .defaultValue("https");

    public static ConfigOption<String> S3_HOSTNAME = ConfigOptions
            .key("s3.host")
            .stringType()
            .noDefaultValue();

    public static ConfigOption<Integer> S3_PORT = ConfigOptions
            .key("s3.prot")
            .intType()
            .defaultValue(80);

    public static ConfigOption<String> S3_USERNAME = ConfigOptions
            .key("s3.username")
            .stringType()
            .noDefaultValue();

    public static ConfigOption<String> S3_PASSWORD = ConfigOptions
            .key("s3.password")
            .stringType()
            .noDefaultValue();

    public static ConfigOption<String> S3_BUCKET = ConfigOptions
            .key("s3.bucket")
            .stringType()
            .noDefaultValue();
}
