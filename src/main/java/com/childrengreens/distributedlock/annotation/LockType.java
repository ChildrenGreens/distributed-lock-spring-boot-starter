package com.childrengreens.distributedlock.annotation;

/**
 * Supported lock flavors across backing stores.
 */
public enum LockType {
    REENTRANT,
    FAIR,
    READ,
    WRITE
}
