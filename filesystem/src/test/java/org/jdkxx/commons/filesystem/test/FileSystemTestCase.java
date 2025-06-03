package org.jdkxx.commons.filesystem.test;

import lombok.extern.slf4j.Slf4j;
import org.jdkxx.commons.configuration.Configuration;
import org.jdkxx.commons.filesystem.register.FileSystemRegistry;
import org.jdkxx.commons.filesystem.test.resource.ResourceFileLoader;
import org.jdkxx.commons.filesystem.test.resource.SecurityOptions;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class FileSystemTestCase {
    public static void main(String[] args) throws Exception {
        Configuration configuration = ResourceFileLoader.load(ResourceFileLoader.MINIO_PREFIX);
        Properties properties = new Properties();
        properties.put("path.style.access", true);

        try (FileSystemRegistry registry = FileSystemRegistry.builder("s3")
                .withProtocol(configuration.get(SecurityOptions.S3_PROTOCOL))
                .withHost(configuration.get(SecurityOptions.S3_HOSTNAME))
                .withPort(configuration.get(SecurityOptions.S3_PORT))
                .withUsername(configuration.get(SecurityOptions.S3_USERNAME))
                .withPassword(configuration.get(SecurityOptions.S3_PASSWORD).toCharArray())
                .withBucket(configuration.get(SecurityOptions.S3_BUCKET))
                .withProperties(properties)
                .build()) {
            URI uri = URI.create("s3://filesystem/images/"); //minio
            //URI uri = URI.create("s3://lkl-zf-flink-cdc-test/images/"); //hcp
            //URI uri = URI.create("s3://lkl-zf-flink-log-test/images/"); //oss
            Path directory = registry.resolve(uri);
            log.info("path {} is existed {}.", directory, Files.exists(directory));

            //Files.delete(directory);

            //Path directories = Files.createDirectories(directory);
            //log.info("directories are '{}'", directories);

            //Path directory = Paths.get(uri);
            try (Stream<Path> stream = Files.list(directory)) {
                for (Path path : stream.collect(Collectors.toList())) {
                    byte[] bytes = Files.readAllBytes(path);
                    log.info("read file {} bytes from {}", bytes.length, path);
                }
            }
        }
    }
}
