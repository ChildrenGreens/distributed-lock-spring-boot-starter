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
package com.childrengreens.distributedlock.executor.redisson;

import com.childrengreens.distributedlock.annotation.LockType;
import com.childrengreens.distributedlock.executor.AbstractThreadLocalLockExecutor;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * Redisson-based lock executor with support for different lock types.
 */
public class RedissonDistributedLockExecutor extends AbstractThreadLocalLockExecutor {
    private final RedissonClient redissonClient;

    public RedissonDistributedLockExecutor(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public boolean tryLock(String key, LockType lockType, long waitTime, long leaseTime, TimeUnit unit) throws Exception {
        RLock lock = resolveLock(key, lockType);
        boolean acquired;
        if (waitTime < 0) {
            if (leaseTime > 0) {
                lock.lock(leaseTime, unit);
            } else {
                lock.lock();
            }
            acquired = true;
        } else {
            long effectiveLease = leaseTime > 0 ? leaseTime : -1;
            acquired = lock.tryLock(waitTime, effectiveLease, unit);
        }
        if (acquired) {
            pushLock(key, lockType, lock);
        }
        return acquired;
    }

    @Override
    public void unlock(String key, LockType lockType) {
        Object lock = popLock(key, lockType);
        if (lock instanceof RLock) {
            ((RLock) lock).unlock();
        }
    }

    private RLock resolveLock(String key, LockType lockType) {
        return switch (lockType) {
            case FAIR -> redissonClient.getFairLock(key);
            case READ -> readWriteLock(key).readLock();
            case WRITE -> readWriteLock(key).writeLock();
            case REENTRANT -> redissonClient.getLock(key);
        };
    }

    private RReadWriteLock readWriteLock(String key) {
        return redissonClient.getReadWriteLock(key);
    }
}
