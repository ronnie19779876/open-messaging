package org.jdkxx.commons.filesystem.config.options;

import com.jcraft.jsch.Proxy;
import org.jdkxx.commons.configuration.ConfigOption;
import org.jdkxx.commons.configuration.ConfigOptions;
import org.jdkxx.commons.filesystem.pool.PoolConfig;

import java.util.Map;

public interface FileSystemOptions {
    ConfigOption<String> SCHEME = ConfigOptions
            .key("scheme")
            .stringType()
            .defaultValue("file");

    ConfigOption<String> HOST = ConfigOptions
            .key("host")
            .stringType()
            .defaultValue("localhost");

    ConfigOption<Integer> PORT = ConfigOptions
            .key("port")
            .intType()
            .defaultValue(80);

    ConfigOption<String> USERNAME = ConfigOptions
            .key("username")
            .stringType()
            .noDefaultValue();

    ConfigOption<String> PASSWORD = ConfigOptions
            .key("password")
            .stringType()
            .noDefaultValue();

    ConfigOption<Integer> CONNECT_TIMEOUT = ConfigOptions
            .key("connectTimeout")
            .intType()
            .noDefaultValue();

    ConfigOption<String> IDENTITIES = ConfigOptions
            .key("identities")
            .stringType()
            .noDefaultValue();

    ConfigOption<String> KNOWN_HOSTS = ConfigOptions
            .key("knownHosts")
            .stringType()
            .noDefaultValue();

    ConfigOption<Proxy> PROXY = ConfigOptions
            .key("proxy")
            .beanType(Proxy.class)
            .noDefaultValue();

    ConfigOption<Map<String, String>> CONFIG = ConfigOptions
            .key("config")
            .mapType()
            .noDefaultValue();

    ConfigOption<Integer> TIMEOUT = ConfigOptions
            .key("timeOut")
            .intType()
            .noDefaultValue();

    ConfigOption<String> DEFAULT_DIR = ConfigOptions
            .key("defaultDir")
            .stringType()
            .noDefaultValue();

    ConfigOption<PoolConfig> POOL_CONFIG = ConfigOptions
            .key("poolConfig")
            .beanType(PoolConfig.class)
            .noDefaultValue();

    ConfigOption<String> BUCKET = ConfigOptions
            .key("bucket")
            .stringType()
            .noDefaultValue();

    ConfigOption<String> PROTOCOL = ConfigOptions
            .key("protocol")
            .stringType()
            .defaultValue("https");

    ConfigOption<Boolean> PATH_STYLE_ACCESS = ConfigOptions
            .key("path.style.access")
            .booleanType()
            .defaultValue(true);
}
