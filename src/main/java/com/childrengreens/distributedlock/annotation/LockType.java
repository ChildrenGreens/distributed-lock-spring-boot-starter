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
package com.childrengreens.distributedlock.annotation;

/**
 * Supported lock flavors across backing stores.
 */
public enum LockType {
    /** Reentrant lock (default). */
    REENTRANT,
    /** Fair lock when supported by the backend. */
    FAIR,
    /** Read lock from a read-write lock. */
    READ,
    /** Write lock from a read-write lock. */
    WRITE
}
