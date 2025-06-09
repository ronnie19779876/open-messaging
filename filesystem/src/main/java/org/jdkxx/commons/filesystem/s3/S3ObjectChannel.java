package org.jdkxx.commons.filesystem.s3;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import lombok.extern.slf4j.Slf4j;
import org.jdkxx.commons.filesystem.FileEntry;
import org.jdkxx.commons.filesystem.api.FileAttributes;
import org.jdkxx.commons.filesystem.api.FileSystemChannel;
import org.jdkxx.commons.filesystem.config.FileSystemEnvironment;
import org.jdkxx.commons.filesystem.config.OpenOptions;
import org.jdkxx.commons.filesystem.config.options.FileSystemOptions;
import org.jdkxx.commons.filesystem.pool.PoolingObject;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import static com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;

@Slf4j
public class S3ObjectChannel extends PoolingObject<IOException> implements FileSystemChannel {
    private final AmazonS3 channel;
    private final String bucketName;

    public S3ObjectChannel(String host, FileSystemEnvironment environment) {
        this.bucketName = environment.getBucketName();
        System.setProperty("com.amazonaws.sdk.disableCertChecking", "true");
        System.setProperty("com.amazonaws.services.s3.disableGetObjectMD5Validation", "true");
        ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setSignerOverride("S3SignerType");
        clientConfig.setProtocol(Protocol.valueOf(environment.getProtocol().toUpperCase()));
        this.channel = AmazonS3ClientBuilder.standard().withClientConfiguration(clientConfig)
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(environment.getUsername(), environment.getPassword())))
                .withEndpointConfiguration(new EndpointConfiguration(environment.endpoint(host), ""))
                .withPathStyleAccessEnabled(environment.getProperties(FileSystemOptions.PATH_STYLE_ACCESS))
                .build();
    }

    @Override
    public InputStream newInputStream(String path, OpenOptions options) throws IOException {
        try {
            S3Object result = this.channel.getObject(bucketName, path);
            return result.getObjectContent();
        } catch (AmazonServiceException ex) {
            int code = ex.getStatusCode();
            if (code == 404) {
                throw new FileNotFoundException("Path - [" + path + "] not found.");
            }
            throw new IOException(ex);
        }
    }

    @Override
    public OutputStream newOutputStream(String path, OpenOptions options) throws IOException {
        return new S3OutputStream(this, path);
    }

    @Override
    public void storeFile(String path,
                          InputStream local,
                          Collection<? extends OpenOption> openOptions) throws IOException {
        try {
            channel.putObject(bucketName, path, local, null);
        } catch (AmazonServiceException ex) {
            int code = ex.getStatusCode();
            switch (code) {
                case 409:
                    throw new FileAlreadyExistsException("Object - [" + path + "] Object already exists.");
                case 400:
                    throw new NotDirectoryException("Path - [" + path + "] Parent not a folder.");
                default:
                    throw new IOException(ex);
            }
        }
    }

    @Override
    public void rename(String source, String target) throws IOException {
        try {
            channel.copyObject(bucketName, source, bucketName, target);
            delete(source);
        } catch (AmazonClientException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void mkdir(String path) throws IOException {
        String[] folders = path.split("/");
        String current = "";
        for (String folder : folders) {
            if (!folder.isEmpty()) {
                current += folder + "/";
                if (!exists(current)) {
                    ObjectMetadata metadata = new ObjectMetadata();
                    metadata.setLastModified(new Date());
                    metadata.setContentLength(0);
                    putObject(current, new byte[0], metadata);
                }
            }
        }
    }

    @Override
    public boolean exists(String path) {
        return channel.doesObjectExist(bucketName, path);
    }

    @Override
    public void delete(String path, boolean isDirectory) throws IOException {
        List<FileEntry> entries = listFiles(path);
        for (FileEntry entry : entries) {
            if (entry.attributes().isDir()) {
                delete(entry.getLongName());
            } else {
                try {
                    channel.deleteObject(bucketName, entry.getLongName());
                } catch (AmazonClientException ex) {
                    throw new IOException(ex);
                }
            }
        }
        try {
            channel.deleteObject(bucketName, path);
        } catch (AmazonClientException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public List<FileEntry> listFiles(String path) throws IOException {
        FutureTask<List<FileEntry>> task = new FutureTask<>(() -> {
            final List<FileEntry> entries = new ArrayList<>();
            try {
                ListObjectsV2Result result = channel.listObjectsV2(bucketName, path);
                List<S3ObjectSummary> summaries = result.getObjectSummaries();
                for (S3ObjectSummary summary : summaries) {
                    String current = summary.getKey();
                    current = current.replace(path, "");
                    if (!current.isEmpty() && current.split("/").length < 2) {
                        FileAttributes attributes = readAttributes(summary.getKey(), false);
                        FileEntry entry = new S3ObjectFileEntry(summary.getKey(), attributes.as(S3ObjectFileAttributes.class));
                        entries.add(entry);
                    }
                }
                return entries;
            } catch (AmazonServiceException ex) {
                throw new IOException(ex);
            }
        });
        new Thread(task).start();
        try {
            return task.get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            InterruptedIOException ioe = new InterruptedIOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        }
    }

    @Override
    public FileAttributes readAttributes(String path, boolean followLinks) throws IOException {
        ObjectMetadata metadata = getObjectMetadata(path);
        AccessControlList acl = getAccessControlList(path);
        return S3ObjectFileAttributes.builder()
                .withMetadata(metadata)
                .withAccessControlList(acl)
                .build();
    }

    @Override
    public void setMtime(String path, long time) throws IOException {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setLastModified(new Date(time));
            CopyObjectRequest request = new CopyObjectRequest(bucketName, path, bucketName, path)
                    .withNewObjectMetadata(metadata);
            channel.copyObject(request);
        } catch (AmazonServiceException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void setAtime(String path, long time) throws IOException {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.addUserMetadata("lastAccessTime", String.valueOf(time));
            CopyObjectRequest request = new CopyObjectRequest(bucketName, path, bucketName, path)
                    .withNewObjectMetadata(metadata);
            channel.copyObject(request);
        } catch (AmazonServiceException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void setCtime(String path, long time) throws IOException {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.addUserMetadata("creationTime", String.valueOf(time));
            CopyObjectRequest request = new CopyObjectRequest(bucketName, path, bucketName, path)
                    .withNewObjectMetadata(metadata);
            channel.copyObject(request);
        } catch (AmazonServiceException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void chown(String path, String id) throws IOException {
        try {
            AccessControlList access = channel.getObjectAcl(bucketName, path);
            access.getOwner().setId(id);
            access.getOwner().setDisplayName(id);
            channel.setObjectAcl(bucketName, path, access);
        } catch (AmazonServiceException ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void chmod(String path, List<AclEntry> acl) throws IOException {
        try {
            AccessControlList access = channel.getObjectAcl(bucketName, path);
            for (AclEntry entry : acl) {
                CanonicalGrantee grantee = new CanonicalGrantee(entry.principal().getName());
                switch (entry.type()) {
                    case ALLOW:
                        toPermissions(entry.permissions())
                                .forEach(p -> access.grantPermission(grantee, p));
                        break;
                    case DENY:
                        access.revokeAllPermissions(grantee);
                }
            }
            channel.setObjectAcl(bucketName, path, access);
        } catch (AmazonServiceException ex) {
            throw new IOException(ex);
        }
    }

    private Set<Permission> toPermissions(Set<AclEntryPermission> perms) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        for (AclEntryPermission perm : perms) {
            switch (perm) {
                case READ_DATA:
                    permissions.add(Permission.Read);
                    break;
                case WRITE_DATA:
                    permissions.add(Permission.Write);
                    break;
                case READ_ACL:
                    permissions.add(Permission.ReadAcp);
                    break;
                case WRITE_ACL:
                    permissions.add(Permission.WriteAcp);
                    break;
                case WRITE_OWNER:
                    permissions.add(Permission.FullControl);
            }
        }
        return permissions;
    }

    private ObjectMetadata getObjectMetadata(String path) throws IOException {
        try {
            return channel.getObjectMetadata(bucketName, path);
        } catch (AmazonServiceException ex) {
            if (ex.getStatusCode() == 404) {
                throw new IOException(ex);
            }
        }
        return new ObjectMetadata();
    }

    private AccessControlList getAccessControlList(String path) throws IOException {
        try {
            return channel.getObjectAcl(bucketName, path);
        } catch (AmazonServiceException ex) {
            if (ex.getStatusCode() == 404) {
                throw new IOException(ex);
            }
        }
        return new AccessControlList();
    }

    private void putObject(String path, byte[] bytes, ObjectMetadata metadata) throws IOException {
        try {
            channel.putObject(bucketName, path, new ByteArrayInputStream(bytes), metadata);
        } catch (AmazonServiceException ex) {
            int code = ex.getStatusCode();
            switch (code) {
                case 409:
                    throw new FileAlreadyExistsException("[" + path + "] Object already exists.");
                case 400:
                    throw new NotDirectoryException("[" + path + "] Parent not a folder.");
                default:
                    throw new IOException(ex);
            }
        }
    }

    @Override
    public void close() throws IOException {
        release();
    }

    @Override
    protected boolean validate() {
        return channel != null;
    }

    @Override
    protected void releaseResources() throws IOException {
        if (channel != null) {
            channel.shutdown();
        }
    }

    private static class S3OutputStream extends ByteArrayOutputStream {
        private final S3ObjectChannel channel;
        private final String path;
        private boolean flush = false;

        private S3OutputStream(S3ObjectChannel channel, String path) {
            this.channel = channel;
            this.path = path;
        }

        @Override
        public void flush() throws IOException {
            flush = true;
        }

        @Override
        public void close() throws IOException {
            if (!flush) {
                byte[] bytes = toByteArray();
                Date today = new Date();
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setLastModified(today);
                metadata.setContentLength(bytes.length);
                channel.putObject(path, bytes, metadata);
            }
        }
    }
}
