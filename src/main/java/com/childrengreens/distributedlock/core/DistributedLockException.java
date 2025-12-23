package com.childrengreens.distributedlock.core;

/**
 * Wraps lock acquisition or release failures.
 */
public class DistributedLockException extends RuntimeException {
    public DistributedLockException(String message) {
        super(message);
    }

    public DistributedLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
