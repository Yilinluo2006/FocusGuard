package com.luoyilin.focusguard;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class BackupFingerprint {
    static String sha256(String payload) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) result.append(Character.forDigit((value & 255) >> 4, 16)).append(Character.forDigit(value & 15, 16));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
