package com.childrengreens.distributedlock.core;

import com.childrengreens.distributedlock.annotation.LockType;

import java.util.concurrent.TimeUnit;

/**
 * Executes distributed lock acquisition and release against a backing store.
 */
public interface DistributedLockExecutor {
    boolean tryLock(String key, LockType lockType, long waitTime, long leaseTime, TimeUnit unit) throws Exception;

    void unlock(String key, LockType lockType) throws Exception;
}
