package org.jdkxx.commons.filesystem.exception;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;

import java.nio.file.*;
import java.util.Collection;

public class DefaultFileSystemExceptionFactory implements FileSystemExceptionFactory {
    public static final DefaultFileSystemExceptionFactory INSTANCE = new DefaultFileSystemExceptionFactory();

    @Override
    public FileSystemException createGetFileException(String file, SftpException exception) {
        return asFileSystemException(file, null, exception);
    }

    /**
     * {@inheritDoc}
     * <p>
     * If the {@link SftpException#id id} of the {@link SftpException} is not {@link ChannelSftp#SSH_FX_NO_SUCH_FILE} or
     * {@link ChannelSftp#SSH_FX_PERMISSION_DENIED}, this default implementation does not return a {@link FileSystemException}, but a
     * {@link NotLinkException} instead.
     */
    @Override
    public FileSystemException createReadLinkException(String link, SftpException exception) {
        if (exception.id == ChannelSftp.SSH_FX_NO_SUCH_FILE || exception.id == ChannelSftp.SSH_FX_PERMISSION_DENIED) {
            return asFileSystemException(link, null, exception);
        }
        final FileSystemException result = new NotLinkException(link, null, exception.getMessage());
        result.initCause(exception);
        return result;
    }

    @Override
    public FileSystemException createListFilesException(String directory, SftpException exception) {
        return asFileSystemException(directory, null, exception);
    }

    @Override
    public FileSystemException createChangeWorkingDirectoryException(String directory, SftpException exception) {
        return asFileSystemException(directory, null, exception);
    }

    @Override
    public FileSystemException createCreateDirectoryException(String directory, SftpException exception) {
        return asFileSystemException(directory, null, exception);
    }

    @Override
    public FileSystemException createDeleteException(String file, SftpException exception, boolean isDirectory) {
        return asFileSystemException(file, null, exception);
    }

    @Override
    public FileSystemException createNewInputStreamException(String file, SftpException exception) {
        return asFileSystemException(file, null, exception);
    }

    @Override
    public FileSystemException createNewOutputStreamException(String file, SftpException exception, Collection<? extends OpenOption> options) {
        return asFileSystemException(file, null, exception);
    }

    @Override
    public FileSystemException createCopyException(String file, String other, SftpException exception) {
        return asFileSystemException(file, other, exception);
    }

    @Override
    public FileSystemException createMoveException(String file, String other, SftpException exception) {
        return asFileSystemException(file, other, exception);
    }

    @Override
    public FileSystemException createSetOwnerException(String file, SftpException exception) {
        return asFileSystemException(file, null, exception);
    }

    @Override
    public FileSystemException createSetGroupException(String file, SftpException exception) {
        return asFileSystemException(file, null, exception);
    }

    @Override
    public FileSystemException createSetPermissionsException(String file, SftpException exception) {
        return asFileSystemException(file, null, exception);
    }

    @Override
    public FileSystemException createSetModificationTimeException(String file, SftpException exception) {
        return asFileSystemException(file, null, exception);
    }

    private FileSystemException asFileSystemException(String file, String other, SftpException e) {
        final FileSystemException exception;
        switch (e.id) {
            case ChannelSftp.SSH_FX_NO_SUCH_FILE:
                exception = new NoSuchFileException(file, other, e.getMessage());
                break;
            case ChannelSftp.SSH_FX_PERMISSION_DENIED:
                exception = new AccessDeniedException(file, other, e.getMessage());
                break;
            default:
                exception = new FileSystemException(file, other, e.getMessage());
                break;
        }
        exception.initCause(e);
        return exception;
    }
}
