package com.czminimap.discovery;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExplorationTrackerTest {
    @Test
    void spiralCoversEveryColumnInsideRadiusExactlyOnce() {
        ExplorationTracker tracker = new ExplorationTracker();
        tracker.update(10, -10, 2, 4);

        Set<String> visited = new HashSet<>();
        for (int i = 0; i < 25; i++) {
            int[] column = tracker.nextSurfaceColumn();
            assertNotNull(column);
            assertEquals(true, Math.max(Math.abs(column[0] - 10), Math.abs(column[1] + 10)) <= 2);
            visited.add(column[0] + ":" + column[1]);
        }

        assertEquals(25, visited.size());
        assertNull(tracker.nextSurfaceColumn());
    }

    @Test
    void frontierStaysInsideRadiusAndIgnoresDuplicates() {
        ExplorationTracker tracker = new ExplorationTracker();
        tracker.update(0, 0, 4, 4);
        tracker.seed(0, 0, 0);

        tracker.enqueueAir(3, 0, 0);
        tracker.enqueueAir(3, 0, 0);
        tracker.enqueueAir(5, 0, 0);

        assertEquals(8, countQueued(tracker));
    }

    @Test
    void movementBeyondThresholdRebasesTheSweep() {
        ExplorationTracker tracker = new ExplorationTracker();
        tracker.update(0, 0, 2, 4);
        tracker.nextSurfaceColumn();

        tracker.update(20, 0, 2, 4);

        int[] column = tracker.nextSurfaceColumn();
        assertNotNull(column);
        assertEquals(20, column[0]);
        assertEquals(0, column[1]);
    }

    private static int countQueued(ExplorationTracker tracker) {
        int count = 0;
        while (tracker.pollAir() != null) {
            count++;
        }
        return count;
    }
}
