package de.igslandstuhl.database;

public class RegistryLockedException extends RuntimeException {
    public RegistryLockedException() {
        super("Registry already locked");
    }
    public RegistryLockedException(String message) {
        super(message);
    }
    public RegistryLockedException(Throwable cause) {
        super(cause);
    }
    public RegistryLockedException(String message, Throwable cause) {
        super(message, cause);
    }
    public RegistryLockedException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
