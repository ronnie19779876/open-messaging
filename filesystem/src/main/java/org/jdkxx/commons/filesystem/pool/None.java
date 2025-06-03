package org.jdkxx.commons.filesystem.pool;

public final class None extends RuntimeException {
    private None() {
        throw new IllegalStateException("cannot create instances of " + getClass().getName());
    }
}
