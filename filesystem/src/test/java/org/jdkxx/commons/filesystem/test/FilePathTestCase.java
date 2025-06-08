package org.jdkxx.commons.filesystem.test;

import lombok.extern.slf4j.Slf4j;
import org.jdkxx.commons.configuration.Configuration;
import org.jdkxx.commons.filesystem.config.FileSystemConfiguration;
import org.jdkxx.commons.filesystem.register.FileSystemRegistry;
import org.jdkxx.commons.filesystem.test.resource.ResourceFileLoader;
import org.jdkxx.commons.filesystem.test.resource.SecurityOptions;
import org.junit.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;

//@RunWith(Parameterized.class)
@Slf4j
public class FilePathTestCase {
    protected static FileSystemRegistry registry;
    protected static Configuration resource;

    @BeforeClass
    public static void setUp() throws Exception {
        resource = ResourceFileLoader.load(ResourceFileLoader.MINIO_PREFIX);
        Properties properties = new Properties();
        properties.put("path.style.access", true);
        FileSystemConfiguration configuration = FileSystemConfiguration.builder("s3")
                .withProtocol(resource.get(SecurityOptions.S3_PROTOCOL))
                .withHost(resource.get(SecurityOptions.S3_HOSTNAME))
                .withPort(resource.get(SecurityOptions.S3_PORT))
                .withUsername(resource.get(SecurityOptions.S3_USERNAME))
                .withPassword(resource.get(SecurityOptions.S3_PASSWORD).toCharArray())
                .withBucket(resource.get(SecurityOptions.S3_BUCKET))
                .withProperties(properties)
                .build();
        registry = FileSystemRegistry.create().register(configuration);
    }

    @Test
    public void test_getParent() throws Exception {
        //URI uri = URI.create("s3://tM48ZeS3IEqkM60fZgEF@server.jdkxx.org:29000/filesystem/images/header1.jpg");
        //URI uri = URI.create("s3://tM48ZeS3IEqkM60fZgEF@/filesystem/images/header1.jpg");
        URI uri = URI.create("s3://filesystem/images/header1.jpg");
        Path file = registry.getPath(uri);
        Path parent = file.getParent();
        log.info("file path is [{}] and parent path is [{}], parent path is exist: {}",
                file, parent, Files.exists(parent));
    }

    @Test
    public void test_subpath() throws Exception {
        //URI uri = URI.create("s3://tM48ZeS3IEqkM60fZgEF@server.jdkxx.org:29000/filesystem/images/header1.jpg");
        //URI uri = URI.create("s3://tM48ZeS3IEqkM60fZgEF@/filesystem/images/header1.jpg");
        URI uri = URI.create("s3://filesystem/images/header1.jpg");
        Path file = registry.getPath(uri);
        Path subpath = file.subpath(0, 2);
        log.info("subpath: {}, is exist: {}", subpath, Files.exists(subpath));
    }

    @Test
    public void test_iterator() throws Exception {
        //URI uri = URI.create("s3://tM48ZeS3IEqkM60fZgEF@server.jdkxx.org:29000/filesystem/images/header1.jpg");
        //URI uri = URI.create("s3://tM48ZeS3IEqkM60fZgEF@/filesystem/images/header1.jpg");
        URI uri = URI.create("s3://filesystem/images/header1.jpg");
        Path file = registry.getPath(uri);
        file.iterator().forEachRemaining(path -> log.info("element: {}", path));
    }

    @Test
    public void test_getPath() {
        String location = String.format("s3://%s@%s:%s/%s",
                resource.get(SecurityOptions.S3_USERNAME), resource.get(SecurityOptions.S3_HOSTNAME),
                resource.get(SecurityOptions.S3_PORT), "filesystem/images/header1.jpg");
        URI uri = URI.create(location);
        //URI uri = URI.create("s3://tM48ZeS3IEqkM60fZgEF@server.jdkxx.org:29000/images/header1.jpg#filesystem");
        //URI uri = URI.create("s3://tM48ZeS3IEqkM60fZgEF@server.jdkxx.org:29000/filesystem/images/header1.jpg#filesystem");
        Path path = Paths.get(uri);
        log.info("path to URI is {}", path.toUri());
        Assert.assertTrue(Files.exists(path));
    }

    @Test
    public void test_toFile() {
        String location = String.format("s3://%s@%s:%s/%s",
                resource.get(SecurityOptions.S3_USERNAME), resource.get(SecurityOptions.S3_HOSTNAME),
                resource.get(SecurityOptions.S3_PORT), "filesystem/images/header1.jpg");
        URI uri = URI.create(location);
        Path path = Paths.get(uri);
        Assert.assertThrows(UnsupportedOperationException.class, path::toFile);
    }

    @Test
    public void test_toUri() throws Exception {
        //URI uri = URI.create("s3://tM48ZeS3IEqkM60fZgEF@server.jdkxx.org:29000/filesystem/images/header1.jpg");
        //URI uri = URI.create("s3://tM48ZeS3IEqkM60fZgEF@/filesystem/images/header1.jpg");
        URI uri = URI.create("s3://filesystem/images/header1.jpg");
        Path file = registry.getPath(uri);
        uri = file.toUri();
        log.info("to URI is {}", uri);

        Path other = Paths.get(uri);
        Assert.assertTrue(Files.exists(other));

        Path another = registry.getPath(uri);
        Assert.assertTrue(Files.exists(another));
    }

    @Test
    public void test_toAbsolutePath() throws Exception {
        URI uri = URI.create("s3://filesystem/images/header1.jpg");
        Path file = registry.getPath(uri);
        Path path = file.toAbsolutePath();
        log.info("AbsolutePath is exist {}", Files.exists(path));
        Assert.assertTrue(Files.exists(path));
    }

    @Test
    public void testFilePath() throws Exception {
        //URI uri = URI.create("s3://tM48ZeS3IEqkM60fZgEF@server.jdkxx.org:29000/filesystem/images/header1.jpg");
        //URI uri = URI.create("s3://tM48ZeS3IEqkM60fZgEF@/filesystem/images/header1.jpg");
        URI uri = URI.create("s3://filesystem/images/");
        Path directory = registry.getPath(uri);
        log.info("directory is {}", directory);
        Path root = directory.getRoot();
        log.info("directory [{}] root path is [{}]", directory, root);
        Path file = directory.resolve("header1.jpg");
        log.info("file path is {}", file);
        Path filename = file.getFileName();
        log.info("file name is {}", filename);
        for (int idx = 0; idx < file.getNameCount(); idx++) {
            Path name = file.getName(idx);
            log.info("part {} of name is {}", idx, name);
        }

        boolean startsWith = directory.startsWith("images");
        log.info("startsWith is {}", startsWith);
        boolean endsWith = directory.endsWith("images/");
        log.info("endsWith is {}", endsWith);
    }

    @Test
    public void testSortPaths() {
        URI[] uris = {URI.create("s3://filesystem/images/header1.jpg"),
                URI.create("s3://filesystem/images/header0.jpg"),
                URI.create("s3://filesystem/images/header3.jpg"),
                URI.create("s3://filesystem/images/header2.jpg"),
                URI.create("s3://filesystem/images/header5.jpg"),
                URI.create("s3://filesystem/images/header4.jpg")};
        Arrays.stream(uris).map(uri -> {
            try {
                return registry.getPath(uri);
            } catch (URISyntaxException ignored) {
                return null;
            }
        }).filter(Objects::nonNull).sorted().forEach(path -> log.info("path: {}", path));
    }

    @AfterClass
    public static void close() throws Exception {
        registry.close();
    }
}
