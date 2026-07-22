package dev.oluso;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class ServerContext {
    private final String hostname;
    private final String os;
    private final String arch;
    private final long pid;
    private final String javaVersion;

    private ServerContext(String hostname, String os, String arch, long pid, String javaVersion) {
        this.hostname = hostname;
        this.os = os;
        this.arch = arch;
        this.pid = pid;
        this.javaVersion = javaVersion;
    }

    static ServerContext capture() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "unknown";
        }

        long pid;
        try {
            pid = ProcessHandle.current().pid();
        } catch (Exception e) {
            pid = -1;
        }

        return new ServerContext(
                hostname,
                System.getProperty("os.name", "unknown") + " " + System.getProperty("os.version", ""),
                System.getProperty("os.arch", "unknown"),
                pid,
                System.getProperty("java.version", "unknown"));
    }

    public String getHostname() {
        return hostname;
    }

    public String getOs() {
        return os;
    }

    public String getArch() {
        return arch;
    }

    public long getPid() {
        return pid;
    }

    public String getJavaVersion() {
        return javaVersion;
    }
}
