package com.cappielloantonio.tempo.service;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.source.ShuffleOrder;

import java.util.Random;

/**
 * A shuffle order where tracks inserted into the middle of the queue play straight after
 * the track before them, so "Play next" means next even with shuffle on. The default order
 * scatters inserted tracks at random. A first fill is shuffled at random and tracks added
 * to the end of the queue go to the end of the order.
 */
@UnstableApi
public final class PlayNextShuffleOrder implements ShuffleOrder {
    private final Random random;
    // shuffled[i] is the queue index played i-th; positions[q] is where queue index q sits in it.
    private final int[] shuffled;
    private final int[] positions;

    public PlayNextShuffleOrder() {
        this(new int[0], new Random());
    }

    /** An order with the given queue indices, in play order. */
    public PlayNextShuffleOrder(int[] shuffled) {
        this(shuffled.clone(), new Random());
    }

    private PlayNextShuffleOrder(int[] shuffled, Random random) {
        this.shuffled = shuffled;
        this.random = random;
        positions = new int[shuffled.length];
        for (int i = 0; i < shuffled.length; i++) positions[shuffled[i]] = i;
    }

    @Override
    public int getLength() {
        return shuffled.length;
    }

    @Override
    public int getNextIndex(int index) {
        int position = positions[index] + 1;
        return position < shuffled.length ? shuffled[position] : C.INDEX_UNSET;
    }

    @Override
    public int getPreviousIndex(int index) {
        int position = positions[index] - 1;
        return position >= 0 ? shuffled[position] : C.INDEX_UNSET;
    }

    @Override
    public int getLastIndex() {
        return shuffled.length > 0 ? shuffled[shuffled.length - 1] : C.INDEX_UNSET;
    }

    @Override
    public int getFirstIndex() {
        return shuffled.length > 0 ? shuffled[0] : C.INDEX_UNSET;
    }

    @Override
    public ShuffleOrder cloneAndInsert(int insertionIndex, int insertionCount) {
        int[] inserted = new int[insertionCount];
        for (int i = 0; i < insertionCount; i++) inserted[i] = insertionIndex + i;

        if (shuffled.length == 0) {
            for (int i = insertionCount - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int swap = inserted[i];
                inserted[i] = inserted[j];
                inserted[j] = swap;
            }
            return new PlayNextShuffleOrder(inserted, random);
        }

        // Where the new tracks go in the order: after the track before them in the
        // queue, or at the end when they're appended.
        int at = insertionIndex == 0 || insertionIndex >= shuffled.length
                ? shuffled.length
                : positions[insertionIndex - 1] + 1;

        int[] result = new int[shuffled.length + insertionCount];
        for (int i = 0, out = 0; i <= shuffled.length; i++) {
            if (i == at) {
                System.arraycopy(inserted, 0, result, out, insertionCount);
                out += insertionCount;
            }
            if (i < shuffled.length) {
                int index = shuffled[i];
                result[out++] = index >= insertionIndex ? index + insertionCount : index;
            }
        }
        return new PlayNextShuffleOrder(result, random);
    }

    @Override
    public ShuffleOrder cloneAndRemove(int indexFrom, int indexToExclusive) {
        int removed = indexToExclusive - indexFrom;
        int[] result = new int[shuffled.length - removed];
        int out = 0;
        for (int index : shuffled) {
            if (index >= indexFrom && index < indexToExclusive) continue;
            result[out++] = index >= indexToExclusive ? index - removed : index;
        }
        return new PlayNextShuffleOrder(result, random);
    }

    @Override
    public ShuffleOrder cloneAndClear() {
        return new PlayNextShuffleOrder(new int[0], random);
    }
}
