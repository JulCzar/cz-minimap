package com.czminimap.cartography;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Region-local palette. Index -1 means "no appearance stored" (for example, known air). */
final class VoxelPalette {
    private final List<VoxelAppearance> appearances = new ArrayList<>();
    private final Map<VoxelAppearance, Integer> indices = new HashMap<>();

    int indexOf(VoxelAppearance appearance) {
        if (appearance == null) {
            return -1;
        }
        Integer existing = indices.get(appearance);
        if (existing != null) {
            return existing;
        }
        int index = appearances.size();
        appearances.add(appearance);
        indices.put(appearance, index);
        return index;
    }

    VoxelAppearance get(int index) {
        return index < 0 || index >= appearances.size() ? null : appearances.get(index);
    }

    List<VoxelAppearance> freeze() {
        return List.copyOf(appearances);
    }
}
