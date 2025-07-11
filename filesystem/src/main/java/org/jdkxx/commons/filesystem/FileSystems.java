package org.jdkxx.commons.filesystem;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class FileSystems<S extends FileSystem> {
    private final FileSystemFactory<? extends S> factory;

    /*
     * When a file system has been added, it is present in fileSystems as a FileSystemRegistration.
     * Such a FileSystemRegistration comes in two states:
     * - initially its file system is still being created, and it therefore only has a lock
     * - after the file system has been created, the lock is exchanged for the created file system
     *
     * This allows the creation of file systems to be moved outside global locking and only use locking for the file system itself.
     *
     * The locks will be read locks, linked to a write lock that will be acquired for the duration of invocations to add. That means that once such a
     * lock is successfully acquired, the write lock has been released and the file system will have been created.
     *
     * Locking strategy:
     * - The fileSystems map is both guarded by the fileSystems map itself
     * - synchronized blocks are used inside locks, but within synchronized blocks no locks are used
     * - synchronized blocks contain only short-lived and non-blocking logic
     * - all access to FileSystemRegistration instances is done from within synchronized blocks
     */
    private final Map<URI, FileSystemRegistration<S>> fileSystems;

    /**
     * Creates a new {@link FileSystem} map.
     *
     * @param factory The factory to use to create new {@link FileSystem} instances.
     */
    public FileSystems(FileSystemFactory<? extends S> factory) {
        this.factory = Objects.requireNonNull(factory);
        fileSystems = new HashMap<>();
    }

    /**
     * Adds a new file system. It is created using the factory provided in the constructor.
     *
     * @param uri The URI representing the file system.
     * @param env A map of provider-specific properties to configure the file system.
     * @return The new file system.
     * @throws NullPointerException             If the given URI or map is {@code null}.
     * @throws FileSystemAlreadyExistsException If a file system has already been added for the given URI.
     * @throws IOException                      If the file system could not be created.
     * @see FileSystemProvider#newFileSystem(URI, Map)
     */
    public S add(URI uri, Map<String, ?> env) throws IOException {
        Objects.requireNonNull(uri);
        Objects.requireNonNull(env);

        ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        Lock lock = readWriteLock.writeLock();
        lock.lock();
        try {
            addLock(uri, readWriteLock.readLock());
            S fileSystem = createFileSystem(uri, env);
            addNewFileSystem(uri, fileSystem);
            return fileSystem;
        } finally {
            lock.unlock();
        }
    }

    private void addLock(URI uri, Lock lock) {
        synchronized (fileSystems) {
            if (fileSystems.containsKey(uri)) {
                throw new FileSystemAlreadyExistsException(uri.toString());
            }
            fileSystems.put(uri, new FileSystemRegistration<>(lock));
        }
    }

    private S createFileSystem(URI uri, Map<String, ?> env) throws IOException {
        try {
            return factory.create(uri, env);
        } catch (final Exception e) {
            // A lock has been added as part of addLock; remove it again so add can be called again with the same URI.
            removeLock(uri);
            throw e;
        }
    }

    private void addNewFileSystem(URI uri, S fileSystem) {
        synchronized (fileSystems) {
            // This method is called while the write lock is acquired, so nothing can have removed the entry yet.
            fileSystems.get(uri).setFileSystem(fileSystem);
        }
    }

    private void removeLock(URI uri) {
        synchronized (fileSystems) {
            fileSystems.remove(uri);
        }
    }

    /**
     * Returns a previously added file system.
     *
     * @param uri The URI representing the file system.
     * @return The file system represented by the given URI.
     * @throws NullPointerException        If the given URI is {@code null}.
     * @throws FileSystemNotFoundException If no file system has been added for the given URI.
     * @see FileSystemProvider#getFileSystem(URI)
     */
    public S get(URI uri) {
        Objects.requireNonNull(uri);

        Lock lock;

        synchronized (fileSystems) {
            FileSystemRegistration<S> registration = fileSystems.get(uri);
            if (registration == null) {
                throw new FileSystemNotFoundException(uri.toString());
            }
            if (registration.fileSystem != null) {
                return registration.fileSystem;
            }
            // There is a registration but without a file system, so the write lock is still acquired.
            lock = registration.lock;
        }

        lock.lock();
        try {
            return getFileSystem(uri);
        } finally {
            lock.unlock();
        }
    }

    private S getFileSystem(URI uri) {
        synchronized (fileSystems) {
            /*
             * The write lock has been released, so fileSystems.get(uri) either is null or has a file system.
             * It will only be null in case the file system has been removed between getting a reference to the read lock and acquiring it.
             */
            FileSystemRegistration<S> registration = fileSystems.get(uri);
            if (registration == null) {
                throw new FileSystemNotFoundException(uri.toString());
            }
            return registration.fileSystem;
        }
    }

    /**
     * Removes a previously added file system. This method should be called when a file system returned by {@link #add(URI, Map)} is closed.
     * <p>
     * If no file system had been added for the given URI, or if it already had been removed, no error will be thrown.
     *
     * @param uri The URI representing the file system.
     * @return {@code true} if a file system was added for the given URI, or {@code false} otherwise.
     * @throws NullPointerException If the given URI is {@code null}.
     * @see FileSystem#close()
     */
    public boolean remove(URI uri) {
        Objects.requireNonNull(uri);

        Lock lock;

        synchronized (fileSystems) {
            /*
             * If the URI is mapped to a registration with a file system, that mapping must be removed.
             * If the URI is mapped to a registration with a lock, that mapping must not be removed at this point, as the lock is still needed.
             * Instead, it must be removed after the lock can be acquired.
             *
             * Because it's more likely to remove file systems *after* they have been completely added, remove the registration and re-add it
             * if it has a lock instead of a file system.
             */
            FileSystemRegistration<S> registration = fileSystems.remove(uri);
            if (registration == null) {
                return false;
            }
            if (registration.fileSystem != null) {
                return true;
            }
            // There is a registration but without a file system, so the write lock is still acquired.
            fileSystems.put(uri, registration);
            lock = registration.lock;
        }

        lock.lock();
        try {
            // add has finished, so fileSystem.get(uri) will be null or have a file system
            return removeFileSystem(uri);
        } finally {
            lock.unlock();
        }
    }

    private boolean removeFileSystem(URI uri) {
        synchronized (fileSystems) {
            /*
             * The write lock has been released, so the registration can be removed.
             * It's possible that another concurrent removal is finalized before this call.
             */
            return fileSystems.remove(uri) != null;
        }
    }

    /**
     * Returns the URIs of the currently added file systems. The result is a snapshot of the current state; it will not be updated if a file system
     * is added or removed.
     * <p>
     * Note that the URIs of file systems that are still being created as part of {@link #add(URI, Map)} will be included in the result.
     *
     * @return A set with the URIs of the currently added file systems.
     */
    public NavigableSet<URI> uris() {
        synchronized (fileSystems) {
            return new TreeSet<>(fileSystems.keySet());
        }
    }

    /**
     * A factory for a file system.
     *
     * @param <S> The type of file system to create.
     * @author Rob Spoor
     * @since 2.1
     */
    public interface FileSystemFactory<S extends FileSystem> {
        /**
         * Creates a new file system.
         *
         * @param uri The URI representing the file system.
         * @param env A map of provider-specific properties to configure the file system.
         * @return The created file system.
         * @throws IOException If the file system could not be created.
         * @see FileSystemProvider#newFileSystem(URI, Map)
         * @see FileSystems#add(URI, Map)
         */
        S create(URI uri, Map<String, ?> env) throws IOException;
    }

    private static final class FileSystemRegistration<S extends FileSystem> {
        private S fileSystem;
        private Lock lock;

        private FileSystemRegistration(Lock lock) {
            this.fileSystem = null;
            this.lock = lock;
        }

        private void setFileSystem(S fileSystem) {
            this.fileSystem = fileSystem;
            this.lock = null;
        }
    }
}
