package com.laczek.whatsapptoolkit;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioTrimRangeTest {
    @Test public void acceptsValidRange() {
        assertTrue(AudioTrimRange.isValid(0, 1));
        assertTrue(AudioTrimRange.isValid(15, 90));
    }

    @Test public void rejectsNegativeStart() {
        assertFalse(AudioTrimRange.isValid(-1, 4));
    }

    @Test public void rejectsEmptyOrReversedRange() {
        assertFalse(AudioTrimRange.isValid(4, 4));
        assertFalse(AudioTrimRange.isValid(9, 4));
    }

    @Test public void convertsSecondsToMicroseconds() {
        assertEquals(2_000_000L, AudioTrimRange.startMicros(2));
        assertEquals(12_000_000L, AudioTrimRange.endMicros(12));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeMicrosecondInput() {
        AudioTrimRange.startMicros(-1);
    }
}
