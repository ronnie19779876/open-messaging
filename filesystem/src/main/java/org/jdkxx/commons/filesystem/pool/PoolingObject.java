package org.jdkxx.commons.filesystem.pool;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public abstract class PoolingObject<T extends Exception> {
    private static final AtomicLong OBJECT_COUNTER = new AtomicLong();
    private final long objectId;
    private final Set<Object> references;
    private Pool<PoolingObject<T>, T> pool;
    private long idleSince;

    /**
     * Creates a new pooling object.
     */
    protected PoolingObject() {
        objectId = OBJECT_COUNTER.incrementAndGet();
        references = Collections.newSetFromMap(new IdentityHashMap<>());
    }

    long objectId() {
        return objectId;
    }

    void setPool(Pool<PoolingObject<T>, T> pool) {
        this.pool = pool;
        resetIdleSince();
    }

    void clearPool() {
        pool = null;
    }

    boolean isPooled() {
        return pool != null;
    }

    int referenceCount() {
        return references.size();
    }

    long idleSince() {
        return idleSince;
    }

    void resetIdleSince() {
        idleSince = System.nanoTime();
    }

    /**
     * Adds a reference to this object. An object will only be returned to the pool it was acquired from if all references to the object are removed.
     * This allows objects to return other (closeable) objects like {@link InputStream} or {@link OutputStream}. These should be added as reference,
     * and {@linkplain #removeReference(Object) removed} when they are no longer needed (e.g., when they are closed).
     *
     * @param reference The non-{@code null} reference to add.
     * @throws NullPointerException If the given reference is {@code null}.
     */
    protected final void addReference(Object reference) {
        Objects.requireNonNull(reference);
        references.add(reference);
    }

    /**
     * Removes a reference to this object.
     * If no more references remain, this object will be returned to the pool it was acquired from. If this object is not associated with a pool,
     * {@link #releaseResources()} will be called instead.
     *
     * @param reference The non-{@code null} reference to remove.
     * @throws NullPointerException If the given reference is {@code null}.
     * @throws T                    If an exception is thrown when calling {@link #releaseResources()}.
     * @see #addReference(Object)
     */
    protected final void removeReference(Object reference) throws T {
        Objects.requireNonNull(reference);
        if (references.remove(reference)) {
            if (references.isEmpty()) {
                if (pool != null) {
                    pool.returnToPool(this);
                } else {
                    releaseResources();
                }
            }
        }
    }

    /**
     * Checks whether this object is still valid.
     * Invalid object will be removed from the pool instead of being returned from {@link Pool#acquire()} or {@link Pool#acquireNow()}.
     * They will also have their {@linkplain #releaseResources() resources released}.
     *
     * @return {@code true} if this object is still valid, or {@code false} otherwise.
     */
    protected abstract boolean validate();

    /**
     * Releases any resources associated with this object.
     *
     * @throws T If the resources could not be released.
     */
    protected abstract void releaseResources() throws T;

    void acquired() {
        addReference(this);
    }

    /**
     * Releases this object. If no more {@linkplain #addReference(Object) references} remain, this object will be returned to the pool it was acquired
     * from. If this object is not associated with a pool, {@link #releaseResources()} will be called instead.
     *
     * @throws T If an exception is thrown when calling {@link #releaseResources()}.
     */
    protected void release() throws T {
        removeReference(this);
    }
}
