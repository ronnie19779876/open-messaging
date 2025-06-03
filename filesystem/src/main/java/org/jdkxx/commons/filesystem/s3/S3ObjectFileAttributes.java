package org.jdkxx.commons.filesystem.s3;

import com.amazonaws.services.s3.model.*;
import org.jdkxx.commons.filesystem.api.FileAttributes;

import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class S3ObjectFileAttributes implements FileAttributes {
    private final ObjectMetadata metadata;
    private final AccessControlList access;

    private S3ObjectFileAttributes(ObjectMetadata metadata, AccessControlList access) {
        this.metadata = metadata;
        this.access = access;
    }

    @Override
    public boolean isDir() {
        return getSize() <= 0;
    }

    @Override
    public long getMTime() {
        return metadata.getLastModified().getTime();
    }

    @Override
    public long getLastAccessTime() {
        String value = metadata.getUserMetaDataOf("lastAccessTime");
        if (value != null && !value.isEmpty()) {
            return Long.parseLong(value);
        }
        return -1;
    }

    @Override
    public long getCreationTime() {
        String value = metadata.getUserMetaDataOf("creationTime");
        if (value != null && !value.isEmpty()) {
            return Long.parseLong(value);
        }
        return -1;
    }

    public Owner getOwner() {
        return access.getOwner();
    }

    @Override
    public long getSize() {
        return metadata.getContentLength();
    }

    @Override
    public Set<PosixFilePermission> getPermissions() {
        Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
        List<Grant> grants = access.getGrantsAsList();
        for (Grant grant : grants) {
            Grantee grantee = grant.getGrantee();
            Permission permission = grant.getPermission();
            if (grantee.getIdentifier().equals(getOwner().getId())) {
                switch (permission) {
                    case FullControl:
                        permissions.add(PosixFilePermission.OWNER_READ);
                        permissions.add(PosixFilePermission.OWNER_WRITE);
                        break;
                    case Read:
                        permissions.add(PosixFilePermission.OWNER_READ);
                        break;
                    case Write:
                        permissions.add(PosixFilePermission.OWNER_WRITE);
                        break;
                }
            }
        }
        return permissions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ObjectMetadata metadata;
        private AccessControlList access;

        public Builder withMetadata(ObjectMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder withAccessControlList(AccessControlList access) {
            this.access = access;
            return this;
        }

        public S3ObjectFileAttributes build() {
            return new S3ObjectFileAttributes(metadata, access);
        }
    }
}
