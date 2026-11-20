package com.czminimap.core;

import java.util.Objects;

/** Local identity of one world or server. Never contains credentials or a raw server address. */
public record WorldKey(String storageId, String displayName) {
    public WorldKey {
        Objects.requireNonNull(storageId, "storageId");
        Objects.requireNonNull(displayName, "displayName");
        if (storageId.isBlank()) {
            throw new IllegalArgumentException("storageId must not be blank");
        }
    }
}
