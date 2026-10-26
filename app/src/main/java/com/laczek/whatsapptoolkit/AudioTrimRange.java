package com.laczek.whatsapptoolkit;

/** Validates the user-selected time range for an audio clip. */
public final class AudioTrimRange {
    private AudioTrimRange() {}

    public static boolean isValid(int startSeconds, int endSeconds) {
        return startSeconds >= 0 && endSeconds > startSeconds;
    }

    public static long startMicros(int startSeconds) {
        if (startSeconds < 0) throw new IllegalArgumentException("startSeconds must be non-negative");
        return startSeconds * 1_000_000L;
    }

    public static long endMicros(int endSeconds) {
        if (endSeconds < 0) throw new IllegalArgumentException("endSeconds must be non-negative");
        return endSeconds * 1_000_000L;
    }
}
