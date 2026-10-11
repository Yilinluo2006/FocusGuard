package com.luoyilin.focusguard.network;

public final class BackupResponse {
    public transient long restoreOwner;
    public transient String restoreAuthorization;
    public String deviceId, deviceName, updatedAt, hash, payload;
    public long revision;
}
