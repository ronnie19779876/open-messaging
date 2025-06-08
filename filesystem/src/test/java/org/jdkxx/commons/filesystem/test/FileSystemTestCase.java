package org.jdkxx.commons.filesystem.test;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.jdkxx.commons.filesystem.test.resource.SecurityOptions;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class FileSystemTestCase extends FilePathTestCase {
    private Path getPath() throws Exception {
        URI uri = URI.create("s3://filesystem/images/header1.jpg"); //minio
        //URI uri = URI.create("s3://lkl-zf-flink-cdc-test/images/"); //hcp
        //URI uri = URI.create("s3://lkl-zf-flink-log-test/images/"); //oss
        return registry.getPath(uri);
    }

    @Test
    public void test_newInputStream() throws Exception {
        URI uri = URI.create("s3://filesystem/files/test.txt"); //minio
        Path path = registry.getPath(uri);
        try (InputStream is = Files.newInputStream(path)) {
            String lines = IOUtils.toString(is, StandardCharsets.UTF_8);
            log.info("read file [{}] from {}", lines, path);
        }
    }

    @Test
    public void test_newOutputStream() throws Exception {
        URI uri = URI.create("s3://filesystem/files/data.txt"); //minio
        Path path = registry.getPath(uri);
        try (OutputStream out = Files.newOutputStream(path)) {
            IOUtils.write("how are you", out, StandardCharsets.UTF_8);
        }
    }

    @Test
    public void test_newByteChannel() throws Exception {
        String location = String.format("s3://%s@%s:%s/%s",
                resource.get(SecurityOptions.S3_USERNAME),
                resource.get(SecurityOptions.S3_HOSTNAME),
                resource.get(SecurityOptions.S3_PORT),
                "filesystem/files/data.txt");  //minio
        URI uri = URI.create(location);
        Path path = Paths.get(uri);
        ByteBuffer out = ByteBuffer.allocate(1024);
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            long size = channel.size();
            log.info("size is {}", size);
            ByteBuffer in = ByteBuffer.allocate(5);
            int reads;
            while ((reads = channel.read(in)) != -1) {
                byte[] bytes = in.array();
                bytes = Arrays.copyOfRange(bytes, 0, reads);
                out.put(bytes);
                in.flip();
            }
            log.info("out is {}", new String(out.array(), StandardCharsets.UTF_8));
        }

        Path dest = registry.getPath(URI.create("s3://filesystem/files/data1.txt"));
        try (SeekableByteChannel channel = Files.newByteChannel(dest, StandardOpenOption.WRITE)) {
            out.flip();
            int writes = channel.write(out);
            log.info("writes is {}, position: {}", writes, channel.position());
        }
    }

    @Test
    public void test_newBufferedReader() throws Exception {
        String location = String.format("s3://%s@%s:%s/%s",
                resource.get(SecurityOptions.S3_USERNAME),
                resource.get(SecurityOptions.S3_HOSTNAME),
                resource.get(SecurityOptions.S3_PORT),
                "filesystem/files/data.txt");  //minio
        URI uri = URI.create(location);
        Path path = Paths.get(uri);
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            Stream<String> lines = reader.lines();
            for (String line : lines.collect(Collectors.toList())) {
                log.info("line is {}", line);
            }
        }
    }

    @Test
    public void test_exists() throws Exception {
        boolean exists = Files.exists(getPath());
        Assert.assertTrue(exists);
    }

    @Test
    public void test_readAttributes() throws Exception {
        PosixFileAttributes attributes = Files.readAttributes(getPath(), PosixFileAttributes.class);
        log.info("read attributes, size:{}", attributes.size());
        log.info("read attributes, owner:{}", attributes.owner());
        log.info("read attributes, group:{}", attributes.group());
        log.info("read attributes, isDirectory:{}", attributes.isDirectory());
        log.info("read attributes, create time:{}", attributes.creationTime());
        //log.info("read attributes, permissions:{}", attributes.permissions());
        log.info("read attributes, last modified time:{}", attributes.lastModifiedTime());
        log.info("read attributes, last access time:{}", attributes.lastAccessTime());
    }

    @Test
    public void test_listFiles() throws Exception {
        URI uri = URI.create("s3://filesystem/files/"); //minio
        //URI uri = URI.create("s3://lkl-zf-flink-cdc-test/images/"); //hcp
        //URI uri = URI.create("s3://lkl-zf-flink-log-test/images/"); //oss
        Path directory = registry.getPath(uri);
        try (Stream<Path> stream = Files.list(directory)) {
            for (Path path : stream.collect(Collectors.toList())) {
                byte[] bytes = Files.readAllBytes(path);
                log.info("read file {} bytes from {}", bytes.length, path);
            }
        }
    }

    @Test
    public void test_createDirectories() throws Exception {
        URI uri = URI.create("s3://filesystem/files/"); //minio
        //URI uri = URI.create("s3://lkl-zf-flink-cdc-test/images/"); //hcp
        //URI uri = URI.create("s3://lkl-zf-flink-log-test/images/"); //oss
        Path path = registry.getPath(uri);
        Path directories = Files.createDirectories(path);
        Assert.assertTrue(Files.exists(directories));
    }

    @Test
    public void test_delete() throws Exception {
        URI uri = URI.create("s3://filesystem/files/"); //minio
        //URI uri = URI.create("s3://lkl-zf-flink-cdc-test/images/"); //hcp
        //URI uri = URI.create("s3://lkl-zf-flink-log-test/images/"); //oss
        Path path = registry.getPath(uri);
        Files.delete(path);
        Assert.assertFalse(Files.exists(path));
    }

    @Test
    public void test_writeFile() throws Exception {
        URI uri = URI.create("s3://filesystem/files/"); //minio
        //URI uri = URI.create("s3://lkl-zf-flink-cdc-test/images/"); //hcp
        //URI uri = URI.create("s3://lkl-zf-flink-log-test/images/"); //oss
        Path directory = registry.getPath(uri);
        Path file = directory.resolve("test.txt");
        Path dest = Files.write(file, "hello world".getBytes());
        Assert.assertTrue(Files.exists(dest));
    }

    @Test
    public void test_readFile() throws Exception {
        URI uri = URI.create("s3://filesystem/files/test.txt");
        //URI uri = URI.create("s3://lkl-zf-flink-cdc-test/files/test.txt"); //hcp
        //URI uri = URI.create("s3://lkl-zf-flink-log-test/files/test.txt");  //oss
        Path path = registry.getPath(uri);
        byte[] bytes = Files.readAllBytes(path);
        Assert.assertEquals("hello world", new String(bytes));
    }

    @Test
    public void test_newDirectoryStream() throws Exception {
        URI uri = URI.create("s3://filesystem/files/"); //minio
        Path directory = registry.getPath(uri);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                log.info("path is {}, is exist: {}", path, Files.exists(path));
            }
        }
    }
}
