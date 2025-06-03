package org.jdkxx.commons.filesystem;

import org.jdkxx.commons.filesystem.config.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.util.Iterator;
import java.util.NoSuchElementException;

public abstract class AbstractDirectoryStream<T> implements DirectoryStream<T> {

    private final Filter<? super T> filter;

    private boolean open = true;
    private Iterator<T> iterator = null;

    /**
     * Creates a new {@code DirectoryStream}.
     *
     * @param filter The optional filter to use.
     */
    public AbstractDirectoryStream(Filter<? super T> filter) {
        this.filter = filter;
    }

    @Override
    public synchronized void close() throws IOException {
        open = false;
    }

    private synchronized boolean isOpen() {
        return open;
    }

    @Override
    public synchronized @NotNull Iterator<T> iterator() {
        if (!open) {
            throw Messages.directoryStream().closed();
        }
        if (iterator != null) {
            throw Messages.directoryStream().iteratorAlreadyReturned();
        }
        iterator = new Iterator<T>() {
            private T next = null;
            private State state = State.UNSPECIFIED;

            @Override
            public boolean hasNext() {
                if (state == State.UNSPECIFIED) {
                    next = getNextElement();
                    state = next != null ? State.ACTIVE : State.ENDED;
                }
                return state == State.ACTIVE;
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                T result = next;
                next = null;
                state = State.UNSPECIFIED;
                return result;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
        setupIteration();
        return iterator;
    }

    private T getNextElement() {
        while (isOpen()) {
            try {
                T next = getNext();
                if (next == null) {
                    return null;
                }
                if (filter == null || filter.accept(next)) {
                    return next;
                }
            } catch (IOException e) {
                throw new DirectoryIteratorException(e);
            }
        }
        return null;
    }

    /**
     * Performs the necessary steps to set up iteration. The default implementation does nothing.
     */
    protected void setupIteration() {
        // does nothing
    }

    /**
     * Returns the next element in iteration.
     *
     * @return The next element in iteration, or {@code null} if there is no more next element.
     * @throws IOException If the next element could not be retrieved.
     */
    protected abstract T getNext() throws IOException;

    private enum State {
        /**
         * Indicates a lookahead iterator is still active (i.e., there is a next element).
         */
        ACTIVE,
        /**
         * Indicates a lookahead iterator has ended (i.e., there is no next element).
         */
        ENDED,
        /**
         * Indicates it's not known whether a lookahead iterator has a next element or not.
         */
        UNSPECIFIED,
    }
}
