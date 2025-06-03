package org.jdkxx.commons.filesystem.principal;

import java.io.IOException;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;

public class DefaultUserPrincipal implements UserPrincipal {
    private final String name;

    private DefaultUserPrincipal(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        UserPrincipal other = (UserPrincipal) o;
        return name.equals(other.getName());
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + name + "]";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends UserPrincipalLookupService {
        private String name;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public UserPrincipal build() {
            return new DefaultUserPrincipal(name);
        }

        @Override
        public UserPrincipal lookupPrincipalByName(String name) throws IOException {
            return new DefaultUserPrincipal(name);
        }

        @Override
        public GroupPrincipal lookupPrincipalByGroupName(String group) throws IOException {
            return DefaultGroupPrincipal.builder()
                    .group(group)
                    .build();
        }
    }
}
