package org.jdkxx.commons.filesystem.exception;

import com.jcraft.jsch.SftpException;

import java.nio.file.FileSystemException;
import java.nio.file.OpenOption;
import java.util.Collection;

public interface FileSystemExceptionFactory {
    /**
     * Creates a {@code FileSystemException} that indicates a file or directory cannot be retrieved.
     *
     * @param file      A string identifying the file or directory.
     * @param exception The {@link SftpException} that was thrown.
     * @return The created {@code FileSystemException}.
     */
    FileSystemException createGetFileException(String file, SftpException exception);

    /**
     * Creates a {@code FileSystemException} that indicates a symbolic link cannot be read.
     *
     * @param link      A string identifying the symbolic link.
     * @param exception The {@link SftpException} that was thrown.
     * @return The created {@code FileSystemException}.
     */
    FileSystemException createReadLinkException(String link, SftpException exception);

    /**
     * Creates a {@code FileSystemException} that indicates the contents of a directory cannot be retrieved.
     *
     * @param directory A string identifying the directory.
     * @param exception The {@link SftpException} that was thrown.
     * @return The created {@code FileSystemException}.
     */
    FileSystemException createListFilesException(String directory, SftpException exception);

    /**
     * Creates a {@code FileSystemException} that indicates a directory cannot be used as the current working directory.
     *
     * @param directory A string identifying the directory.
     * @param exception The {@link SftpException} that was thrown.
     * @return The created {@code FileSystemException}.
     */
    FileSystemException createChangeWorkingDirectoryException(String directory, SftpException exception);

    /**
     * Creates a {@code FileSystemException} that indicates a directory cannot be created.
     *
     * @param directory A string identifying the directory.
     * @param exception The {@link SftpException} that was thrown.
     * @return The created {@code FileSystemException}.
     */
    FileSystemException createCreateDirectoryException(String directory, SftpException exception);

    /**
     * Creates a {@code FileSystemException} that indicates a file or directory cannot be deleted.
     *
     * @param file        A string identifying the file or directory.
     * @param exception   The {@link SftpException} that was thrown.
     * @param isDirectory {@code true} if a directory cannot be deleted, or {@code false} if a file cannot be deleted.
     * @return The created {@code FileSystemException}.
     */
    FileSystemException createDeleteException(String file, SftpException exception, boolean isDirectory);

    /**
     * Creates a {@code FileSystemException} that indicates a file cannot be opened for reading.
     *
     * @param file      A string identifying the file.
     * @param exception The {@link SftpException} that was thrown.
     * @return The created {@code FileSystemException}.
     */
    FileSystemException createNewInputStreamException(String file, SftpException exception);

    /**
     * Creates a {@code FileSystemException} that indicates a file cannot be opened for writing.
     *
     * @param file      A string identifying the file.
     * @param exception The {@link SftpException} that was thrown.
     * @param options   The open options used to open the file.
     * @return The created {@code FileSystemException}.
     */
    FileSystemException createNewOutputStreamException(String file, SftpException exception, Collection<? extends OpenOption> options);

    /**
     * Creates a {@code FileSystemException} that indicates a file or directory cannot be copied.
     *
     * @param file      A string identifying the file or directory to be copied.
     * @param other     A string identifying the file or directory to be copied to.
     * @param exception The {@link SftpException} that was thrown.
     * @return The created {@code FileSystemException}.
     */
    FileSystemException createCopyException(String file, String other, SftpException exception);

    /**
     * Creates a {@code FileSystemException} that indicates a file or directory cannot be moved.
     *
     * @param file      A string identifying the file or directory to be moved.
     * @param other     A string identifying the file or directory to be moved to.
     * @param exception The {@link SftpException} that was thrown.
     * @return The created {@code FileSystemException}.
     */
    FileSystemException createMoveException(String file, String other, SftpException exception);

    /**
     * Creates a {@code FileSystemException} that indicates the owner of a file or directory cannot be set.
     *
     * @param file      A string identifying the file or directory to set the owner of.
     * @param exception The {@link SftpException} that was thrown.
     * @return The created {@code FileSystemException}.
     */
    FileSystemException createSetOwnerException(String file, SftpException exception);

    /**
     * Creates a {@code FileSystemException} that indicates the group of a file or directory cannot be set.
     *
     * @param file      A string identifying the file or directory to set the group of.
     * @param exception The {@link SftpException} that was thrown.
     * @return The created {@code FileSystemException}.
     */
    FileSystemException createSetGroupException(String file, SftpException exception);

    /**
     * Creates a {@code FileSystemException} that indicates the permissions of a file or directory cannot be set.
     *
     * @param file      A string identifying the file or directory to set the permissions of.
     * @param exception The {@link SftpException} that was thrown.
     * @return The created {@code FileSystemException}.
     */
    FileSystemException createSetPermissionsException(String file, SftpException exception);

    /**
     * Creates a {@code FileSystemException} that indicates the modification time of a file or directory cannot be set.
     *
     * @param file      A string identifying the file or directory to set the modification time of.
     * @param exception The {@link SftpException} that was thrown.
     * @return The created {@code FileSystemException}.
     */
    FileSystemException createSetModificationTimeException(String file, SftpException exception);
}
