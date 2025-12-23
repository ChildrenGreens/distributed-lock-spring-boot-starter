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
package com.childrengreens.distributedlock.executor.zookeeper;

import com.childrengreens.distributedlock.annotation.LockType;
import com.childrengreens.distributedlock.executor.AbstractThreadLocalLockExecutor;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;

import java.util.concurrent.TimeUnit;

/**
 * Curator-based lock executor that maps lock keys to Zookeeper paths.
 */
public class ZookeeperDistributedLockExecutor extends AbstractThreadLocalLockExecutor {
    private final CuratorFramework client;

    public ZookeeperDistributedLockExecutor(CuratorFramework client) {
        this.client = client;
    }

    @Override
    public boolean tryLock(String key, LockType lockType, long waitTime, long leaseTime, TimeUnit unit) throws Exception {
        InterProcessLock lock = resolveLock(key, lockType);
        boolean acquired;
        if (waitTime < 0) {
            lock.acquire();
            acquired = true;
        } else {
            acquired = lock.acquire(waitTime, unit);
        }
        if (acquired) {
            pushLock(key, lockType, lock);
        }
        return acquired;
    }

    @Override
    public void unlock(String key, LockType lockType) throws Exception {
        Object lock = popLock(key, lockType);
        if (lock instanceof InterProcessLock) {
            ((InterProcessLock) lock).release();
        }
    }

    private InterProcessLock resolveLock(String key, LockType lockType) {
        String path = normalizePath(key);
        return switch (lockType) {
            case READ -> readWriteLock(path).readLock();
            case WRITE -> readWriteLock(path).writeLock();
            case FAIR, REENTRANT -> new InterProcessMutex(client, path);
        };
    }

    private InterProcessReadWriteLock readWriteLock(String path) {
        return new InterProcessReadWriteLock(client, path);
    }

    private String normalizePath(String key) {
        String normalized = key.replace(':', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }
}
