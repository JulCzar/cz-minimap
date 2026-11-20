package com.czminimap.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Pure identity normalization shared by the client resolver and the storage layer. */
public final class WorldIdentity {
    public enum Mode {
        NAME_AND_HASH,
        HASH_ONLY,
        NAME_ONLY
    }

    private static final int HASH_HEX_LENGTH = 12;
    private static final int MAX_NAME_LENGTH = 32;

    private WorldIdentity() {
    }

    public static String sanitizeName(String raw) {
        String cleaned = raw.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");
        if (cleaned.isEmpty()) {
            cleaned = "world";
        }
        return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH) : cleaned;
    }

    public static String hash(String normalizedIdentity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedIdentity.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(HASH_HEX_LENGTH);
            for (int i = 0; i < digest.length && result.length() < HASH_HEX_LENGTH; i++) {
                result.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xFF));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    public static String storageId(String displayName, String normalizedIdentity, Mode mode) {
        String name = sanitizeName(displayName);
        return switch (mode) {
            case NAME_AND_HASH -> name + "_" + hash(normalizedIdentity);
            case HASH_ONLY -> "w_" + hash(normalizedIdentity);
            case NAME_ONLY -> name;
        };
    }
}
