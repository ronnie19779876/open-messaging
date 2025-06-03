package org.jdkxx.commons.filesystem.pool;

public interface PoolingObjectFactory<T extends PoolingObject<V>, V extends Exception> {
    /**
     * Creates a new {@link PoolingObject}.
     *
     * @return The created object.
     * @throws V If an error occurs while creating the object.
     */
    T newObject() throws V;
}
