package org.jdkxx.commons.filesystem.pool;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class Pool<T extends PoolingObject<V>, V extends Exception> {
    private static final Supplier<NoSuchElementException> DEFAULT_ERROR_SUPPLIER = NoSuchElementException::new;

    private final PoolConfig config;
    private final PoolingObjectFactory<T, V> factory;
    private final Deque<T> idleObjects;
    private int size;
    private final Lock lock;
    private final Condition notEmptyOrInactive;
    private final AtomicBoolean active;
    private final CountDownLatch shutdownLatch;

    /**
     * Creates a new pool.
     *
     * @param config  The configuration to use.
     * @param factory The object factory to use.
     * @throws NullPointerException If the given configuration, factory or logger is {@code null}.
     * @throws V                    If the given config requests a positive initial size, and one or more objects could not be created.
     */
    public Pool(PoolConfig config, PoolingObjectFactory<T, V> factory) throws V {
        this.config = Objects.requireNonNull(config);
        this.factory = Objects.requireNonNull(factory);

        idleObjects = new ArrayDeque<>(config.maxSize());
        size = 0;

        lock = new ReentrantLock();
        notEmptyOrInactive = lock.newCondition();

        fillPool();

        active = new AtomicBoolean(true);
        shutdownLatch = new CountDownLatch(1);
    }

    private void fillPool() throws V {
        int initialSize = config.initialSize();
        List<T> objects = new ArrayList<>(initialSize);
        try {
            for (int i = 0; i < initialSize; i++) {
                objects.add(createObject());
            }
        } catch (Exception e) {
            // Creating the initial objects failed, clean up all resources
            for (T object : objects) {
                try {
                    discard(object);
                } catch (Exception e2) {
                    e.addSuppressed(e2);
                }
            }
            throw cast(e);
        }

        lock.lock();
        try {
            idleObjects.addAll(objects);
            size = objects.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Acquires an object. This method will block until an object is available or the maximum wait time, as defined in the configuration used to
     * create this pool, expires. If the configured maximum wait time is negative, this method will block until an object is available.
     *
     * @return The acquired object.
     * @throws V                      If an error occurs while acquiring an object.
     * @throws NoSuchElementException If the maximum wait time expires before an object could be acquired.
     * @throws InterruptedException   If the current thread is interrupted while acquiring an object.
     * @throws IllegalStateException  If this pool has {@linkplain #shutdown shut down}.
     */
    public T acquire() throws V, InterruptedException {
        return acquire(DEFAULT_ERROR_SUPPLIER);
    }

    /**
     * Acquires an object. This method will block until an object is available or the maximum wait time expires.
     *
     * @param maxWaitTime The maximum wait time.
     *                    If {@code null} or {@linkplain Duration#isNegative() negative}, this method will block until an object is available.
     * @return The acquired object.
     * @throws V                      If an error occurs while acquiring an object.
     * @throws NoSuchElementException If the maximum wait time expires before an object could be acquired.
     * @throws InterruptedException   If the current thread is interrupted while acquiring an object.
     * @throws IllegalStateException  If this pool has {@linkplain #shutdown shut down}.
     */
    public T acquire(Duration maxWaitTime) throws V, InterruptedException {
        return acquire(maxWaitTime, DEFAULT_ERROR_SUPPLIER);
    }

    /**
     * Acquires an object. This method will block until an object is available or the maximum wait time expires.
     *
     * @param maxWaitTime The maximum wait time. If negative, this method will block until an object is available.
     * @param timeUnit    The time unit for the maximum wait time.
     * @return The acquired object.
     * @throws NullPointerException   If the given time unit is {@code null}.
     * @throws V                      If an error occurs while acquiring an object.
     * @throws NoSuchElementException If the maximum wait time expires before an object could be acquired.
     * @throws InterruptedException   If the current thread is interrupted while acquiring an object.
     * @throws IllegalStateException  If this pool has {@linkplain #shutdown shut down}.
     */
    public T acquire(long maxWaitTime, TimeUnit timeUnit) throws V, InterruptedException {
        return acquire(maxWaitTime, timeUnit, DEFAULT_ERROR_SUPPLIER);
    }

    /**
     * Acquires an object. This method will block until an object is available or the maximum wait time, as defined in the configuration used to
     * create this pool, expires. If the configured maximum wait time is negative, this method will block until an object is available.
     *
     * @param <E>           The type of exception to throw if the maximum wait time expires.
     * @param errorSupplier A supplier for the exception to throw if the maximum wait time expires.
     * @return The acquired object.
     * @throws NullPointerException  If the given supplier is {@code null} and the maximum wait time expires.
     * @throws V                     If an error occurs while acquiring an object.
     * @throws E                     If the maximum wait time expires before an object could be acquired.
     * @throws InterruptedException  If the current thread is interrupted while acquiring an object.
     * @throws IllegalStateException If this pool has {@linkplain #shutdown shut down}.
     */
    public <E extends Exception> T acquire(Supplier<E> errorSupplier) throws V, E, InterruptedException {
        long maxWaitTimeInNanos = config.maxWaitTimeInNanos();
        return acquireBlocking(maxWaitTimeInNanos, errorSupplier);
    }

    /**
     * Acquires an object. This method will block until an object is available or the maximum wait time expires.
     *
     * @param <E>           The type of exception to throw if the maximum wait time expires.
     * @param maxWaitTime   The maximum wait time.
     *                      If {@code null} or {@linkplain Duration#isNegative() negative}, this method will block until an object is available.
     * @param errorSupplier A supplier for the exception to throw if the maximum wait time expires.
     * @return The acquired object.
     * @throws NullPointerException  If the given supplier is {@code null} and the maximum wait time expires.
     * @throws V                     If an error occurs while acquiring an object.
     * @throws E                     If the maximum wait time expires before an object could be acquired.
     * @throws InterruptedException  If the current thread is interrupted while acquiring an object.
     * @throws IllegalStateException If this pool has {@linkplain #shutdown shut down}.
     */
    public <E extends Exception> T acquire(Duration maxWaitTime, Supplier<E> errorSupplier) throws V, E, InterruptedException {
        long maxWaitTimeInNanos = maxWaitTime != null ? maxWaitTime.toNanos() : -1;
        return acquireBlocking(maxWaitTimeInNanos, errorSupplier);
    }

    /**
     * Acquires an object. This method will block until an object is available or the maximum wait time expires.
     *
     * @param <E>           The type of exception to throw if the maximum wait time expires.
     * @param maxWaitTime   The maximum wait time. If negative, this method will block until an object is available.
     * @param timeUnit      The time unit for the maximum wait time.
     * @param errorSupplier A supplier for the exception to throw if the maximum wait time expires.
     * @return The acquired object.
     * @throws NullPointerException  If the given time unit is {@code null}.
     *                               Or if the given supplier is {@code null} and the maximum wait time expires.
     * @throws V                     If an error occurs while acquiring an object.
     * @throws E                     If the maximum wait time expires before an object could be acquired.
     * @throws InterruptedException  If the current thread is interrupted while acquiring an object.
     * @throws IllegalStateException If this pool has {@linkplain #shutdown shut down}.
     */
    public <E extends Exception> T acquire(long maxWaitTime, TimeUnit timeUnit, Supplier<E> errorSupplier) throws V, E, InterruptedException {
        long maxWaitTimeInNanos = timeUnit.toNanos(maxWaitTime);
        return acquireBlocking(maxWaitTimeInNanos, errorSupplier);
    }

    private <E extends Exception> T acquireBlocking(long maxWaitTimeInNanos, Supplier<E> errorSupplier) throws V, E, InterruptedException {
        checkActive();

        ObjectSupplier<T, V> supplier;

        lock.lock();
        try {
            supplier = acquireBlocking(maxWaitTimeInNanos);
        } finally {
            lock.unlock();
        }

        if (supplier != null) {
            return supplier.get();
        }

        checkActive();

        throw errorSupplier.get();
    }

    private ObjectSupplier<T, V> acquireBlocking(long maxWaitTimeInNanos) throws InterruptedException {
        ObjectSupplier<T, V> supplier = findAvailableObject();
        if (supplier != null) {
            return supplier;
        }

        // There are no available idle objects, and there is no space in the pool to create one.
        // Wait for an idle object to become available or for space in the pool to create one.

        awaitPoolNotEmpty(maxWaitTimeInNanos);

        // Either !idleObjects.isEmpty(), or size < maxSize, or this pool is no longer active, or the max wait time expired

        checkActive();

        supplier = findAvailableObject();
        return supplier;

        // The max wait time expired.
    }

    /**
     * Acquires an object if possible. This method will not block if no objects are available.
     *
     * @return An {@link Optional} describing the acquired object, or {@link Optional#empty()} if no object was available.
     * @throws V                     If an error occurs while acquiring an object.
     * @throws IllegalStateException If this pool has {@linkplain #shutdown shut down}.
     */
    public Optional<T> acquireNow() throws V {
        checkActive();

        ObjectSupplier<T, V> supplier;

        lock.lock();
        try {
            supplier = findAvailableObject();
        } finally {
            lock.unlock();
        }

        return supplier != null
                ? Optional.of(supplier.get())
                : Optional.empty();
    }

    /**
     * Acquires an object if possible. This method will not block if no objects are available; instead, a new object will be created that will
     * <em>not</em> be returned to the pool. This can be used in cases where an object is necessary right now, and blocking could lead to deadlock or
     * similar issues.
     *
     * @return The acquired or created object.
     * @throws V                     If an error occurs while acquiring an object.
     * @throws IllegalStateException If this pool has {@linkplain #shutdown shut down}.
     */
    public T acquireOrCreate() throws V {
        T object = acquireNow().orElse(null);
        if (object == null) {
            object = factory.newObject();
            object.acquired();
            // The call to object.acquired() allows it to release resources once it has been released
            // Don't log acquired; the object is not part of the pool
        }
        return object;
    }

    private ObjectSupplier<T, V> findAvailableObject() {
        T object = findValidObject(true);
        if (object != null) {
            // object is valid
            return () -> {
                object.acquired();
                return object;
            };
        }
        if (size < config.maxSize()) {
            // Increase size now so other threads won't be able to exceed the maximum size.
            // If an error occurs when creating the object, the size will be automatically decreased again.
            size++;
            return () -> {
                try {
                    T newObject = createObject();
                    newObject.acquired();
                    return newObject;

                } catch (Exception e) {
                    decreaseSize();
                    throw cast(e);
                }
            };
        }
        return null;
    }

    private T findValidObject(boolean signalIfNotEmpty) {
        T object;
        boolean removedObjects = false;
        while ((object = idleObjects.poll()) != null) {
            if (!object.validate()) {
                size--;
                discardQuietly(object);
                removedObjects = true;
            } else if (config.maxIdleTimeExceeded(object)) {
                size--;
                discardQuietly(object);
                removedObjects = true;
            } else {
                return object;
            }
        }
        // No valid objects in the pool

        if (signalIfNotEmpty && removedObjects) {
            // New objects can possibly be created
            notEmptyOrInactive.signalAll();
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private T createObject() throws V {
        T object = factory.newObject();
        object.setPool((Pool<PoolingObject<V>, V>) this);
        return object;
    }

    private void awaitPoolNotEmpty(long maxWaitTimeInNanos) throws InterruptedException {
        if (maxWaitTimeInNanos < 0) {
            awaitPoolNotEmptyWithoutTimeout();
        } else {
            awaitPoolNotEmptyWithTimeout(maxWaitTimeInNanos);
        }
    }

    private void awaitPoolNotEmptyWithoutTimeout() throws InterruptedException {
        while (idleObjects.isEmpty() && size >= config.maxSize() && isActive()) {
            notEmptyOrInactive.await();
        }
    }

    private void awaitPoolNotEmptyWithTimeout(long maxWaitTimeInNanos) throws InterruptedException {
        long nanos = maxWaitTimeInNanos;
        while (idleObjects.isEmpty() && size >= config.maxSize() && isActive()) {
            if (nanos <= 0) {
                // Let the caller check again for pool.isEmpty() and poolSize < maxPoolSize
                return;
            }
            nanos = notEmptyOrInactive.awaitNanos(nanos);
        }
    }

    private void decreaseSize() {
        lock.lock();
        try {
            size--;
            notEmptyOrInactive.signalAll();

        } finally {
            lock.unlock();
        }
    }

    void returnToPool(T object) {
        assert object.referenceCount() == 0;

        lock.lock();
        try {
            if (isActive()) {
                object.resetIdleSince();
                idleObjects.add(object);
            } else {
                size--;
                discardQuietly(object);
            }
            // Either idleObjects has had an object added or size has been decreased.
            notEmptyOrInactive.signalAll();

        } finally {
            lock.unlock();
        }
    }

    private void discard(T object) throws V {
        try {
            object.releaseResources();
        } catch (Exception e) {
            throw cast(e);
        } finally {
            object.clearPool();
        }
    }

    private void discardQuietly(T object) {
        try {
            discard(object);
        } catch (Exception e) {
            // the exception has already been reported
        }
    }

    /**
     * Runs an operation on all idle objects. These will be acquired in bulk, after which the operation is run on them sequentially. Afterward, each
     * object will be returned to the pool.
     * <p>
     * Any object that is no longer {@linkplain PoolingObject#validate() valid} or has been idle too long will be removed from the pool before
     * running the operation on idle objects. As a result, all arguments to the operation will be valid.
     *
     * @param action The operation to run.
     * @throws V                    If an error occurs while running the operation on any of the idle objects.
     * @throws NullPointerException If the given operation is {@code null}.
     */
    public void forAllIdleObjects(PoolingObjectConsumer<T, V> action) throws V {
        Objects.requireNonNull(action);

        List<T> objects = drainIdleObjects();

        Exception exception = null;
        for (T object : objects) {
            try {
                action.accept(object);
            } catch (Exception e) {
                exception = add(exception, e);
            } finally {
                returnToPool(object);
            }
        }
        if (exception != null) {
            throw cast(exception);
        }
    }

    private List<T> drainIdleObjects() {
        lock.lock();
        try {
            List<T> objects = new ArrayList<>(idleObjects.size());

            int oldSize = size;

            T object;
            while ((object = findValidObject(false)) != null) {
                objects.add(object);
            }

            if (size < oldSize) {
                // A.k.a. removedObjects in findValidObject; new objects can possibly be created
                notEmptyOrInactive.signalAll();
            }

            return objects;

        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns whether this pool is active.
     *
     * @return {@code true} if this pool is active, or {@code false} if it has {@linkplain #shutdown() shut down}.
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * Shuts down this pool. For all idle objects the resources are {@linkplain PoolingObject#releaseResources()} released, and it will no longer be
     * possible to acquire new objects. Any already acquired objects will remain valid until they are returned to the pool.
     *
     * @throws V If an error occurs while releasing resources for any of the idle objects.
     */
    public void shutdown() throws V {
        if (active.compareAndSet(true, false)) {
            try {
                releaseIdleResources();
            } finally {
                lock.lock();
                try {
                    notEmptyOrInactive.signalAll();
                } finally {
                    lock.unlock();
                }

                shutdownLatch.countDown();
            }
        }
    }

    private void releaseIdleResources() throws V {
        List<T> objects = drainIdleObjectsForRelease();

        Exception exception = null;
        for (T object : objects) {
            try {
                discard(object);
            } catch (Exception e) {
                exception = add(exception, e);
            }
        }
        if (exception != null) {
            throw cast(exception);
        }
    }

    private List<T> drainIdleObjectsForRelease() {
        lock.lock();
        try {
            List<T> objects = new ArrayList<>(idleObjects.size());

            T object;
            while ((object = idleObjects.poll()) != null) {
                // No need to validate, the object will not go back to the pool
                objects.add(object);
                size--;
            }

            // No need to signal notEmptyOrInactive; this method is part of the shutdown call which will perform the signal
            return objects;

        } finally {
            lock.unlock();
        }
    }

    boolean awaitShutdown(long maxWaitTime, TimeUnit timeUnit) throws InterruptedException {
        return shutdownLatch.await(maxWaitTime, timeUnit);
    }

    @SuppressWarnings("unchecked")
    private V cast(Exception exception) {
        // Exception will either be compatible with V or an unchecked exception. That means this unsafe cast is allowed.
        return (V) exception;
    }

    private Exception add(Exception existing, Exception e) {
        if (existing == null) {
            return e;
        }
        existing.addSuppressed(e);
        return existing;
    }

    private void checkActive() {
        if (!isActive()) {
            throw new IllegalStateException();
        }
    }

    /**
     * Creates a new pool that throws no exceptions.
     *
     * @param <T>      The type of objects in the pool.
     * @param config   The configuration to use.
     * @param supplier A supplier to serve as an object factory.
     * @return The created pool.
     * @throws NullPointerException If the given configuration, supplier or logger is {@code null}.
     */
    public static <T extends PoolingObject<None>> Pool<T, None> throwingNone(PoolConfig config, Supplier<T> supplier) {
        Objects.requireNonNull(supplier);
        return new Pool<>(config, supplier::get);
    }

    private interface ObjectSupplier<T extends PoolingObject<V>, V extends Exception> {
        T get() throws V;
    }
}
