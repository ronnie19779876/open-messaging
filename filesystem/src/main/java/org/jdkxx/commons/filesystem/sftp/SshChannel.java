package org.jdkxx.commons.filesystem.sftp;

import com.jcraft.jsch.*;
import org.jdkxx.commons.filesystem.FileEntry;
import org.jdkxx.commons.filesystem.api.FileAttributes;
import org.jdkxx.commons.filesystem.api.FileSystemChannel;
import org.jdkxx.commons.filesystem.config.FileSystemEnvironment;
import org.jdkxx.commons.filesystem.config.OpenOptions;
import org.jdkxx.commons.filesystem.exception.DefaultFileSystemExceptionFactory;
import org.jdkxx.commons.filesystem.exception.FileSystemExceptionFactory;
import org.jdkxx.commons.filesystem.pool.PoolingObject;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.OpenOption;
import java.util.*;

public class SshChannel extends PoolingObject<IOException> implements FileSystemChannel {
    private final ChannelSftp channel;
    private final FileSystemExceptionFactory exceptionFactory;

    SshChannel(String host, int port, JSch jsch, FileSystemEnvironment env) throws IOException {
        this.exceptionFactory = DefaultFileSystemExceptionFactory.INSTANCE;
        try {
            Session session = jsch.getSession(env.getUsername(), host, port);
            session.setPassword(env.getPassword());
            session.setTimeout(env.getTimeout());
            session.setConfig("StrictHostKeyChecking", "no");
            if (env.getProxy() != null) {
                session.setProxy(env.getProxy());
            }
            if (env.getConfig() != null) {
                session.setConfig(env.getConfig());
            }
            session.connect(env.getConnectTimeout());
            this.channel = (ChannelSftp) session.openChannel("sftp");
            this.channel.connect(env.getConnectTimeout());
        } catch (JSchException e) {
            throw asFileSystemException(e);
        }
    }

    @Override
    public String pwd() throws IOException {
        try {
            return channel.pwd();
        } catch (SftpException e) {
            throw asIOException(e);
        }
    }

    @Override
    public FileAttributes readAttributes(String path, boolean followLinks) throws IOException {
        try {
            SftpATTRS attrs = followLinks ? channel.stat(path) : channel.lstat(path);
            return new SftpFileAttributes(attrs);
        } catch (SftpException e) {
            throw exceptionFactory.createGetFileException(path, e);
        }
    }

    @Override
    public String readSymbolicLink(String path) throws IOException {
        try {
            return channel.readlink(path);
        } catch (SftpException e) {
            throw exceptionFactory.createReadLinkException(path, e);
        }
    }

    @Override
    public InputStream newInputStream(String path, OpenOptions options) throws IOException {
        assert options.read;
        try {
            InputStream in = channel.get(path);
            in = new SshInputStream(path, in, options.deleteOnClose);
            addReference(in);
            return in;
        } catch (SftpException e) {
            throw exceptionFactory.createNewInputStreamException(path, e);
        }
    }

    @Override
    public OutputStream newOutputStream(String path, OpenOptions options) throws IOException {
        assert options.write;
        int mode = options.append ? ChannelSftp.APPEND : ChannelSftp.OVERWRITE;
        try {
            OutputStream out = channel.put(path, mode);
            out = new SshOutputStream(path, out, options.deleteOnClose);
            addReference(out);
            return out;
        } catch (SftpException e) {
            throw exceptionFactory.createNewOutputStreamException(path, e, options.options);
        }
    }

    @Override
    public void mkdir(String path) throws IOException {
        try {
            String[] folders = path.split("/");
            String current = "";
            for (String folder : folders) {
                if (folder.isEmpty()) {
                    current += "/";
                } else {
                    current += folder + "/";
                }
                if (!fileExists(current)) {
                    channel.mkdir(current);
                }
            }
        } catch (SftpException e) {
            if (fileExists(path)) {
                throw new FileAlreadyExistsException(path);
            }
            throw exceptionFactory.createCreateDirectoryException(path, e);
        }
    }

    @Override
    public void delete(String path, boolean isDirectory) throws IOException {
        try {
            if (isDirectory) {
                channel.rmdir(path);
            } else {
                channel.rm(path);
            }
        } catch (SftpException e) {
            throw exceptionFactory.createDeleteException(path, e, isDirectory);
        }
    }

    @Override
    public void chown(String path, String uid) throws IOException {
        try {
            channel.chown(Integer.parseInt(uid), path);
        } catch (SftpException e) {
            throw exceptionFactory.createSetOwnerException(path, e);
        }
    }

    @Override
    public void changeGroup(String path, String gid) throws IOException {
        try {
            channel.chgrp(Integer.parseInt(gid), path);
        } catch (SftpException e) {
            throw exceptionFactory.createSetGroupException(path, e);
        }
    }

    @Override
    public void chmod(String path, int permissions) throws IOException {
        try {
            channel.chmod(permissions, path);
        } catch (SftpException e) {
            throw exceptionFactory.createSetPermissionsException(path, e);
        }
    }

    @Override
    public void storeFile(String path, InputStream local, Collection<? extends OpenOption> openOptions) throws IOException {
        try {
            channel.put(local, path);
        } catch (SftpException e) {
            throw exceptionFactory.createNewOutputStreamException(path, e, openOptions);
        }
    }

    @Override
    public boolean exists(String path) {
        return false;
    }

    @Override
    public void rename(String source, String target) throws IOException {
        try {
            channel.rename(source, target);
        } catch (SftpException e) {
            throw exceptionFactory.createMoveException(source, target, e);
        }
    }

    @Override
    public List<FileEntry> listFiles(String path) throws IOException {
        List<FileEntry> entries = new ArrayList<>();
        try {
            Vector<ChannelSftp.LsEntry> lsEntries = channel.ls(path);
            for (ChannelSftp.LsEntry entry : lsEntries) {
                entries.add(new SftpFileEntry(entry.getLongname(), entry.getAttrs()));
            }
        } catch (SftpException e) {
            throw exceptionFactory.createListFilesException(path, e);
        }
        return entries;
    }

    @Override
    public void setMtime(String path, long time) throws IOException {
        try {
            channel.setMtime(path, (int) time);
        } catch (SftpException e) {
            throw exceptionFactory.createSetModificationTimeException(path, e);
        }
    }

    @Override
    public void setAtime(String path, long time) throws IOException {
    }

    @Override
    public void setCtime(String path, long time) throws IOException {
    }

    @Override
    public void close() throws IOException {
        release();
    }

    @Override
    protected boolean validate() {
        return channel != null && channel.isConnected();
    }

    @Override
    protected void releaseResources() throws IOException {
        if (channel != null) {
            channel.disconnect();
            try {
                channel.getSession().disconnect();
            } catch (JSchException e) {
                throw asIOException(e);
            }
        }
    }

    SftpStatVFS statVFS(String path) throws IOException {
        try {
            return channel.statVFS(path);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_OP_UNSUPPORTED) {
                throw new UnsupportedOperationException(e);
            }
            // reuse the exception handling for get file
            throw exceptionFactory.createGetFileException(path, e);
        }
    }

    private final class SshInputStream extends InputStream {
        private final String path;
        private final InputStream in;
        private final boolean deleteOnClose;

        private boolean open = true;

        private SshInputStream(String path, InputStream in, boolean deleteOnClose) {
            this.path = path;
            this.in = in;
            this.deleteOnClose = deleteOnClose;
        }

        @Override
        public int read() throws IOException {
            return in.read();
        }

        @Override
        public int read(byte @NotNull [] b) throws IOException {
            return in.read(b);
        }

        @Override
        public int read(byte @NotNull [] b, int off, int len) throws IOException {
            return in.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return in.skip(n);
        }

        @Override
        public int available() throws IOException {
            return in.available();
        }

        @Override
        public void close() throws IOException {
            if (open) {
                try {
                    in.close();
                } finally {
                    // always finalize the stream, to prevent pool starvation
                    // set open to false as well, to prevent finalizing the stream twice
                    open = false;
                    removeReference(this);
                }
                if (deleteOnClose) {
                    delete(path, false);
                }
            }
        }

        @Override
        public synchronized void mark(int limit) {
            in.mark(limit);
        }

        @Override
        public synchronized void reset() throws IOException {
            in.reset();
        }

        @Override
        public boolean markSupported() {
            return in.markSupported();
        }
    }

    private final class SshOutputStream extends OutputStream {
        private final String path;
        private final OutputStream out;
        private final boolean deleteOnClose;

        private boolean open = true;

        private SshOutputStream(String path, OutputStream out, boolean deleteOnClose) {
            this.path = path;
            this.out = out;
            this.deleteOnClose = deleteOnClose;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
        }

        @Override
        public void write(byte @NotNull [] b) throws IOException {
            out.write(b);
        }

        @Override
        public void write(byte @NotNull [] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            if (open) {
                try {
                    out.close();
                } finally {
                    // always finalize the stream, to prevent pool starvation
                    // set open to false as well, to prevent finalizing the stream twice
                    open = false;
                    removeReference(this);
                }
                if (deleteOnClose) {
                    delete(path, false);
                }
            }
        }
    }

    private IOException asIOException(Exception e) throws IOException {
        if (e instanceof IOException) {
            throw (IOException) e;
        }
        FileSystemException exception = new FileSystemException(null, null, e.getMessage());
        exception.initCause(e);
        throw exception;
    }

    private FileSystemException asFileSystemException(Exception e) throws FileSystemException {
        if (e instanceof FileSystemException) {
            throw (FileSystemException) e;
        }
        FileSystemException exception = new FileSystemException(null, null, e.getMessage());
        exception.initCause(e);
        throw exception;
    }

    private boolean fileExists(String path) {
        try {
            channel.stat(path);
            return true;
        } catch (SftpException e) {
            // the file actually may exist, but throw the original exception instead
            return false;
        }
    }
}
