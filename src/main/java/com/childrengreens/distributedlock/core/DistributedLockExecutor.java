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
package com.childrengreens.distributedlock.core;

import com.childrengreens.distributedlock.annotation.LockType;

import java.util.concurrent.TimeUnit;

/**
 * Executes distributed lock acquisition and release against a backing store.
 */
public interface DistributedLockExecutor {
    /**
     * Attempts to acquire a lock for the given key.
     */
    boolean tryLock(String key, LockType lockType, long waitTime, long leaseTime, TimeUnit unit) throws Exception;

    /**
     * Releases a previously acquired lock.
     */
    void unlock(String key, LockType lockType) throws Exception;
}
