package com.cappielloantonio.tempo.service;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import androidx.media3.common.C;
import androidx.media3.exoplayer.source.ShuffleOrder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PlayNextShuffleOrderTest {
    private static int[] order(ShuffleOrder order) {
        List<Integer> out = new ArrayList<>();
        for (int i = order.getFirstIndex(); i != C.INDEX_UNSET; i = order.getNextIndex(i)) out.add(i);
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    @Test
    public void playNextGoesStraightAfterTheCurrentTrack() {
        ShuffleOrder order = new PlayNextShuffleOrder().cloneAndInsert(0, 5);
        int[] before = order(order);
        assertEquals(5, before.length);

        // Playing queue index 2; "Play next" inserts two tracks at index 3.
        ShuffleOrder after = order.cloneAndInsert(3, 2);
        assertEquals(3, after.getNextIndex(2));
        assertEquals(4, after.getNextIndex(3));

        // Everything else keeps its relative order, with indices from 3 shifted by 2.
        List<Integer> rest = new ArrayList<>();
        for (int i : order(after)) if (i != 3 && i != 4) rest.add(i >= 5 ? i - 2 : i);
        assertArrayEquals(before, rest.stream().mapToInt(Integer::intValue).toArray());
    }

    @Test
    public void appendedTracksGoLastAndRemovalShiftsIndices() {
        ShuffleOrder order = new PlayNextShuffleOrder().cloneAndInsert(0, 4).cloneAndInsert(4, 1);
        assertEquals(4, order.getLastIndex());

        ShuffleOrder removed = order.cloneAndRemove(1, 3);
        assertEquals(3, removed.getLength());
        int[] left = order(removed);
        java.util.Arrays.sort(left);
        assertArrayEquals(new int[]{0, 1, 2}, left);
        assertEquals(2, removed.getLastIndex());
    }
}
