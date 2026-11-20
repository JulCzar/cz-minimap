package com.czminimap.client;

import com.czminimap.core.DimensionKey;
import com.czminimap.core.WorldIdentity;
import com.czminimap.core.WorldKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;

import java.util.Locale;

/** Resolves cartographic identities from client-visible session metadata. */
public final class WorldIdentityResolver {
    private static final String DEFAULT_PORT = "25565";

    private WorldIdentityResolver() {
    }

    public static WorldKey resolveWorld(Minecraft minecraft, WorldIdentity.Mode mode) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            throw new IllegalStateException("World identity cannot be resolved without a client level");
        }

        String displayName;
        String identity;
        if (minecraft.getSingleplayerServer() != null) {
            var worldData = minecraft.getSingleplayerServer().getWorldData();
            displayName = worldData.getLevelName();
            identity = "singleplayer:" + displayName + ":" + worldData.worldGenOptions().seed();
        } else {
            ServerData server = minecraft.getCurrentServer();
            String address = server == null ? "local" : server.ip;
            displayName = server == null ? address : server.name;
            identity = "multiplayer:" + normalizedAddress(address);
        }

        String name = WorldIdentity.sanitizeName(displayName);
        return new WorldKey(WorldIdentity.storageId(name, identity, mode), name);
    }

    public static DimensionKey resolveDimension(ClientLevel level) {
        return new DimensionKey(level.dimension().location(), level.getMinBuildHeight(), level.getMaxBuildHeight());
    }

    private static String normalizedAddress(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("[")) {
            int closing = value.indexOf(']');
            if (closing >= 0) {
                String host = value.substring(1, closing);
                String port = value.length() > closing + 1 && value.charAt(closing + 1) == ':'
                        ? value.substring(closing + 2)
                        : DEFAULT_PORT;
                return host + ":" + port;
            }
        }
        int colon = value.lastIndexOf(':');
        if (colon < 0 || value.indexOf(':') != colon) {
            return value + ":" + DEFAULT_PORT;
        }
        return value;
    }
}
