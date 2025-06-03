package org.jdkxx.commons.filesystem.pool;

public interface PoolingObjectConsumer<T extends PoolingObject<X>, X extends Exception> {
    /**
     * Performs this operation on the given argument.
     *
     * @param object The object to operate on.
     * @throws X If an error occurs when performing this operation.
     */
    void accept(T object) throws X;
}
