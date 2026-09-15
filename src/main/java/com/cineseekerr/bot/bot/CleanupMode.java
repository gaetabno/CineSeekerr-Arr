package com.cineseekerr.bot.bot;

/** Whether Transmission should preserve or delete its downloaded payload. */
public enum CleanupMode {
    CLEAR("clear", false),
    DELETE_DATA("delete", true);

    private final String callbackValue;
    private final boolean deleteData;

    CleanupMode(String callbackValue, boolean deleteData) {
        this.callbackValue = callbackValue;
        this.deleteData = deleteData;
    }

    public String callbackValue() {
        return callbackValue;
    }

    public boolean deleteData() {
        return deleteData;
    }

    public static CleanupMode fromCallback(String value) {
        for (CleanupMode mode : values()) {
            if (mode.callbackValue.equals(value)) return mode;
        }
        return null;
    }
}
