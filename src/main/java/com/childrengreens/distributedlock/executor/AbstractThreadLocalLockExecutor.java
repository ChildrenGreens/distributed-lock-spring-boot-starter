package com.childrengreens.distributedlock.executor;

import com.childrengreens.distributedlock.annotation.LockType;
import com.childrengreens.distributedlock.core.DistributedLockExecutor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks acquired locks per thread to support balanced unlocks.
 */
public abstract class AbstractThreadLocalLockExecutor implements DistributedLockExecutor {
    private final ThreadLocal<Map<String, Deque<Object>>> lockHolders = ThreadLocal.withInitial(HashMap::new);

    protected void pushLock(String key, LockType lockType, Object lock) {
        Map<String, Deque<Object>> holders = lockHolders.get();
        String holderKey = holderKey(key, lockType);
        holders.computeIfAbsent(holderKey, ignored -> new ArrayDeque<>()).push(lock);
    }

    protected Object popLock(String key, LockType lockType) {
        Map<String, Deque<Object>> holders = lockHolders.get();
        String holderKey = holderKey(key, lockType);
        Deque<Object> stack = holders.get(holderKey);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Object lock = stack.pop();
        if (stack.isEmpty()) {
            holders.remove(holderKey);
        }
        if (holders.isEmpty()) {
            lockHolders.remove();
        }
        return lock;
    }

    private String holderKey(String key, LockType lockType) {
        return key + "::" + lockType.name();
    }
}
