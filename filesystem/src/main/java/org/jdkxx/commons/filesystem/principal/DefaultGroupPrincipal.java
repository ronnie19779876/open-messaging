package org.jdkxx.commons.filesystem.principal;

import java.nio.file.attribute.GroupPrincipal;

public class DefaultGroupPrincipal implements GroupPrincipal {
    private final String group;

    private DefaultGroupPrincipal(String group) {
        this.group = group;
    }

    @Override
    public String getName() {
        return group;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        GroupPrincipal other = (GroupPrincipal) o;
        return group.equals(other.getName());
    }

    @Override
    public int hashCode() {
        return group.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + group + "]";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String group;

        public Builder group(String group) {
            this.group = group;
            return this;
        }

        public GroupPrincipal build() {
            return new DefaultGroupPrincipal(group);
        }
    }
}
