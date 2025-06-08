package org.jdkxx.commons.filesystem.register;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jdkxx.commons.filesystem.config.FileSystemConfiguration;
import org.jdkxx.commons.lang.ClassUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class FileSystemRegistry implements AutoCloseable {
    private final Map<URI, FileSystem> fileSystems = new ConcurrentHashMap<>(3);

    private FileSystemRegistry() {
    }

    public FileSystemRegistry register(FileSystemConfiguration configuration) throws IOException {
        Objects.requireNonNull(configuration, "configuration must not be null");
        URI uri = configuration.getURIWithUsername();
        if (!fileSystems.containsKey(uri)) {
            FileSystem fs = FileSystems.newFileSystem(configuration.getURI(), configuration.getEnvironment(),
                    ClassUtils.getDefaultClassLoader());
            if (fs != null) {
                fileSystems.put(uri, fs);
            }
        }
        return this;
    }

    public Path getPath(URI uri) throws URISyntaxException {
        String scheme = uri.getScheme();
        if (StringUtils.equalsIgnoreCase(scheme, "s3")
                || StringUtils.equalsIgnoreCase(scheme, "s3a")
                || StringUtils.equalsIgnoreCase(scheme, "oss")) {
            scheme = "s3";
        }
        String userInfo = uri.getUserInfo();
        if (StringUtils.isBlank(userInfo) && StringUtils.isBlank(uri.getHost())) {
            userInfo = getUserInfo(uri.getAuthority());
        }
        String bucket = getBucket(uri);
        for (URI key : fileSystems.keySet()) {
            if (StringUtils.equalsIgnoreCase(scheme, key.getScheme()) &&
                    (StringUtils.equalsIgnoreCase(bucket, getBucket(key.getPath())) ||
                            StringUtils.equalsIgnoreCase(userInfo, key.getUserInfo()))) {
                URI fact = new URI(key.getScheme(),
                        key.getUserInfo(),
                        key.getHost(),
                        key.getPort(),
                        getFullPath(uri, bucket),
                        null,
                        bucket);
                return Paths.get(fact);
            }
        }
        throw new URISyntaxException(uri.toString(), "No registered FileSystem found for " + uri);
    }

    private String getBucket(URI uri) {
        String userInfo = uri.getUserInfo();
        if (StringUtils.isNotBlank(uri.getFragment())) {
            return uri.getFragment();
        } else if (StringUtils.isNotBlank(userInfo)) {
            return getBucket(uri.getPath());
        } else if (StringUtils.isBlank(userInfo) && StringUtils.isBlank(uri.getHost())) {
            return getBucket(uri.getPath());
        } else {
            return uri.getHost();
        }
    }

    private String getBucket(String path) {
        if (StringUtils.startsWith(path, File.separator)) {
            path = StringUtils.substringAfter(path, File.separator);
        }
        String[] split = StringUtils.split(path, File.separator);
        if (split != null && split.length > 0) {
            return split[0];
        }
        return null;
    }

    private String getUserInfo(String authority) {
        String[] split = StringUtils.split(authority, "@");
        if (split != null && split.length > 0) {
            return split[0];
        }
        return null;
    }

    public String getFullPath(URI source, String bucket) {
        String path = source.getPath();
        List<String> token = new ArrayList<>();
        String[] parts = StringUtils.split(path, File.separator);
        Arrays.stream(parts)
                .filter(part -> !StringUtils.equalsIgnoreCase(part, bucket))
                .forEach(token::add);
        String result = StringUtils.join(token, File.separator);
        if (!StringUtils.startsWith(result, File.separator)) {
            result = File.separator + result;
        }
        if (StringUtils.endsWith(path, File.separator)) {
            result += File.separator;
        }
        return result;
    }

    public static FileSystemRegistry create() {
        return new FileSystemRegistry();
    }

    @Override
    public void close() throws Exception {
        for (FileSystem fileSystem : fileSystems.values()) {
            fileSystem.close();
        }
    }
}
