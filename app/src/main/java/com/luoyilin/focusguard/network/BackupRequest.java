package com.luoyilin.focusguard.network;

public final class BackupRequest {
    public final String deviceName, payload;
    public final long expectedRevision;
    public BackupRequest(String name, long revision, String payload) {
        this.deviceName = name; this.expectedRevision = revision; this.payload = payload;
    }
}
