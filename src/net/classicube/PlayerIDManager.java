package net.classicube;

import java.util.BitSet;

public class PlayerIDManager {
    private final BitSet usedIds;
    private static final int MAX_PLAYERS = 255;

    public PlayerIDManager() {
        // Initialize BitSet to track used IDs (0-255)
        usedIds = new BitSet(MAX_PLAYERS);
    }

    public synchronized byte getNextAvailableId() throws NoAvailableIDException {
        // Find first unused ID
        int nextId = usedIds.nextClearBit(0);

        // Check if we've exceeded the maximum
        if (nextId >= MAX_PLAYERS) {
            throw new NoAvailableIDException("No player IDs available (maximum " + MAX_PLAYERS + " reached)");
        }

        // Mark this ID as used
        usedIds.set(nextId);
        return (byte) nextId;
    }

    public synchronized void releaseId(byte id) {
        // Convert negative byte to positive int (0-255 range)
        int positiveId = id & 0xFF;

        // Only clear if within valid range
        if (positiveId < MAX_PLAYERS) {
            usedIds.clear(positiveId);
        }
    }

    public synchronized boolean isIdAvailable(byte id) {
        int positiveId = id & 0xFF;
        return positiveId < MAX_PLAYERS && !usedIds.get(positiveId);
    }

    public synchronized int getActiveIdCount() {
        return usedIds.cardinality();
    }

    public static class NoAvailableIDException extends Exception {
        public NoAvailableIDException(String message) {
            super(message);
        }
    }
}