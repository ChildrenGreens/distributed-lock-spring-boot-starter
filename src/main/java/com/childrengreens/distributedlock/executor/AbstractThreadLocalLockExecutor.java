/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
