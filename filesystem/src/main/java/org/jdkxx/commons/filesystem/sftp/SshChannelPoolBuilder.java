package org.jdkxx.commons.filesystem.sftp;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import org.jdkxx.commons.filesystem.config.FileSystemEnvironment;
import org.jdkxx.commons.filesystem.pool.Pool;
import org.jdkxx.commons.filesystem.pool.PoolConfig;

import java.io.IOException;
import java.nio.file.FileSystemException;

public final class SshChannelPoolBuilder {
    private final JSch jsch;
    private String host;
    private int port = 22;
    private FileSystemEnvironment environment;
    private PoolConfig poolConfig;

    /**
     * constructor
     */
    public SshChannelPoolBuilder() {
        this.jsch = new JSch();
    }

    public SshChannelPoolBuilder withIdentity(String identity) throws FileSystemException {
        if (identity != null && !identity.isEmpty()) {
            try {
                this.jsch.addIdentity(identity);
            } catch (JSchException ex) {
                throw asFileSystemException(ex);
            }
        }
        return this;
    }

    public SshChannelPoolBuilder withIdentity(String identity, String passphrase) throws FileSystemException {
        if (identity != null && !identity.isEmpty()) {
            try {
                this.jsch.addIdentity(identity, passphrase);
            } catch (JSchException ex) {
                throw asFileSystemException(ex);
            }
        }
        return this;
    }

    public SshChannelPoolBuilder withKnownHosts(String filename) throws FileSystemException {
        if (filename != null && !filename.isEmpty()) {
            try {
                this.jsch.setKnownHosts(filename);
            } catch (JSchException ex) {
                throw asFileSystemException(ex);
            }
        }
        return this;
    }

    public SshChannelPoolBuilder withEnvironment(FileSystemEnvironment environment) {
        this.environment = environment;
        return this;
    }

    public SshChannelPoolBuilder withHost(String host) {
        this.host = host;
        return this;
    }

    public SshChannelPoolBuilder withPort(int port) {
        this.port = port;
        return this;
    }

    FileSystemException asFileSystemException(Exception e) throws FileSystemException {
        if (e instanceof FileSystemException) {
            throw (FileSystemException) e;
        }
        FileSystemException exception = new FileSystemException(null, null, e.getMessage());
        exception.initCause(e);
        throw exception;
    }

    public Pool<SshChannel, IOException> build() throws IOException {
        PoolConfig config = this.poolConfig != null ? this.poolConfig : SftpPoolConfig.defaultConfig().config();
        return new Pool<>(config, () -> new SshChannel(host, port, jsch, environment));
    }
}
