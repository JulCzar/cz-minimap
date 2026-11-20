package com.czminimap.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldIdentityTest {
    @Test
    void sanitizesDisplayNames() {
        assertEquals("my_world", WorldIdentity.sanitizeName("  My World!  "));
        assertEquals("world", WorldIdentity.sanitizeName("!!!"));
        assertEquals(32, WorldIdentity.sanitizeName("a".repeat(64)).length());
    }

    @Test
    void hashIsStableAndDoesNotContainTheInput() {
        String hash = WorldIdentity.hash("singleplayer:Example:12345");
        assertEquals(12, hash.length());
        assertTrue(hash.matches("[0-9a-f]{12}"));
        assertEquals(hash, WorldIdentity.hash("singleplayer:Example:12345"));
        assertNotEquals(hash, WorldIdentity.hash("singleplayer:Example:12346"));
        assertFalse(hash.contains("Example"));
    }

    @Test
    void modesProduceDistinctDirectoryNames() {
        String name = WorldIdentity.sanitizeName("My World");
        String identity = "singleplayer:My World:42";
        String hash = WorldIdentity.hash(identity);
        assertEquals("my_world_" + hash,
                WorldIdentity.storageId(name, identity, WorldIdentity.Mode.NAME_AND_HASH));
        assertEquals("w_" + hash,
                WorldIdentity.storageId(name, identity, WorldIdentity.Mode.HASH_ONLY));
        assertEquals("my_world",
                WorldIdentity.storageId(name, identity, WorldIdentity.Mode.NAME_ONLY));
    }

}
