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
package com.childrengreens.distributedlock.aop;

import com.childrengreens.distributedlock.annotation.DistributedLock;
import com.childrengreens.distributedlock.annotation.LockType;
import com.childrengreens.distributedlock.autoconfigure.DistributedLockProperties;
import com.childrengreens.distributedlock.core.DistributedLockException;
import com.childrengreens.distributedlock.core.DistributedLockExecutor;
import com.childrengreens.distributedlock.core.LockKeyResolver;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributedLockInterceptorTest {
    private static final String KEY_EXPRESSION = "#p0";
    private static final String RESOLVED_KEY = "core";
    private static final String METHOD_PREFIX = "method";
    private static final String METHOD_LOCK_KEY = METHOD_PREFIX + ":" + RESOLVED_KEY;
    private static final String GLOBAL_PREFIX = "global";
    private static final String GLOBAL_LOCK_KEY = GLOBAL_PREFIX + ":" + RESOLVED_KEY;
    private static final long WAIT_TIME = 5L;
    private static final long LEASE_TIME = 10L;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    static class SampleService {
        @DistributedLock(key = "#p0", prefix = "method", waitTime = 5, leaseTime = 10, timeUnit = TimeUnit.SECONDS,
                lockType = LockType.FAIR)
        String locked(String name) {
            return "locked:" + name;
        }

        @DistributedLock(key = "#p0")
        String defaultPrefix(String name) {
            return "default:" + name;
        }

        String plain(String name) {
            return "plain:" + name;
        }
    }

    @Test
    void invokeSkipsWhenNoAnnotation() throws Throwable {
        // No annotation means no lock interaction.
        InvocationFixture fixture = fixtureFor("plain", "x");
        fixture.whenProceedReturn("plain:x");

        Object result = fixture.interceptor().invoke(fixture.invocation);

        assertThat(result).isEqualTo("plain:x");
        verify(fixture.executor, never()).tryLock(any(), any(), anyLong(), anyLong(), any());
        verify(fixture.executor, never()).unlock(any(), any());
    }

    @Test
    void invokeAcquiresAndReleasesLock() throws Throwable {
        // Happy path: lock then proceed then unlock.
        InvocationFixture fixture = fixtureFor("locked", "y");
        fixture.whenProceedReturn("locked:y");
        fixture.stubResolvedKey(KEY_EXPRESSION, RESOLVED_KEY);
        when(fixture.executor.tryLock(eq(METHOD_LOCK_KEY), eq(LockType.FAIR), eq(WAIT_TIME), eq(LEASE_TIME), eq(TIME_UNIT)))
                .thenReturn(true);

        Object result = fixture.interceptor().invoke(fixture.invocation);

        assertThat(result).isEqualTo("locked:y");
        verify(fixture.executor).unlock(METHOD_LOCK_KEY, LockType.FAIR);
    }

    @Test
    void invokeUsesGlobalPrefixWhenMethodPrefixMissing() throws Throwable {
        // Method-level prefix should fall back to global prefix.
        InvocationFixture fixture = fixtureFor("defaultPrefix", "z");
        fixture.properties.setPrefix(GLOBAL_PREFIX);
        fixture.whenProceedReturn("default:z");
        fixture.stubResolvedKey(KEY_EXPRESSION, RESOLVED_KEY);
        when(fixture.executor.tryLock(eq(GLOBAL_LOCK_KEY), eq(LockType.REENTRANT), eq(0L), eq(-1L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        Object result = fixture.interceptor().invoke(fixture.invocation);

        assertThat(result).isEqualTo("default:z");
        verify(fixture.executor).unlock(GLOBAL_LOCK_KEY, LockType.REENTRANT);
    }

    @Test
    void invokeThrowsWhenLockNotAcquired() throws Throwable {
        // Failed acquisition should surface as exception.
        InvocationFixture fixture = fixtureFor("locked", "x");
        fixture.stubResolvedKey(KEY_EXPRESSION, RESOLVED_KEY);
        when(fixture.executor.tryLock(eq(METHOD_LOCK_KEY), eq(LockType.FAIR), eq(WAIT_TIME), eq(LEASE_TIME), eq(TIME_UNIT)))
                .thenReturn(false);

        assertThatThrownBy(() -> fixture.interceptor().invoke(fixture.invocation))
                .isInstanceOf(DistributedLockException.class)
                .hasMessageContaining("Failed to acquire lock for key: " + METHOD_LOCK_KEY);
        verify(fixture.executor, never()).unlock(any(), any());
    }

    @Test
    void invokeWrapsExceptionsFromExecutor() throws Throwable {
        // Executor exceptions are wrapped with lock context.
        InvocationFixture fixture = fixtureFor("locked", "x");
        fixture.stubResolvedKey(KEY_EXPRESSION, RESOLVED_KEY);
        when(fixture.executor.tryLock(eq(METHOD_LOCK_KEY), eq(LockType.FAIR), eq(WAIT_TIME), eq(LEASE_TIME), eq(TIME_UNIT)))
                .thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> fixture.interceptor().invoke(fixture.invocation))
                .isInstanceOf(DistributedLockException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void invokeAlwaysUnlocksEvenWhenProceedThrows() throws Throwable {
        // Unlock should still run on invocation failure.
        InvocationFixture fixture = fixtureFor("locked", "x");
        fixture.stubResolvedKey(KEY_EXPRESSION, RESOLVED_KEY);
        when(fixture.executor.tryLock(eq(METHOD_LOCK_KEY), eq(LockType.FAIR), eq(WAIT_TIME), eq(LEASE_TIME), eq(TIME_UNIT)))
                .thenReturn(true);
        fixture.whenProceedThrow(new IllegalArgumentException("fail"));

        assertThatThrownBy(() -> fixture.interceptor().invoke(fixture.invocation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fail");
        verify(fixture.executor).unlock(METHOD_LOCK_KEY, LockType.FAIR);
    }

    @Test
    void invokeSwallowsUnlockExceptions() throws Throwable {
        // Unlock failures are logged, not propagated.
        InvocationFixture fixture = fixtureFor("locked", "x");
        fixture.whenProceedReturn("locked:x");
        fixture.stubResolvedKey(KEY_EXPRESSION, RESOLVED_KEY);
        when(fixture.executor.tryLock(eq(METHOD_LOCK_KEY), eq(LockType.FAIR), eq(WAIT_TIME), eq(LEASE_TIME), eq(TIME_UNIT)))
                .thenReturn(true);
        doThrow(new IllegalStateException("unlock"))
                .when(fixture.executor).unlock(METHOD_LOCK_KEY, LockType.FAIR);

        Object result = fixture.interceptor().invoke(fixture.invocation);

        assertThat(result).isEqualTo("locked:x");
    }

    private static InvocationFixture fixtureFor(String methodName, String arg) throws Exception {
        SampleService target = new SampleService();
        Method method = SampleService.class.getDeclaredMethod(methodName, String.class);
        MethodInvocation invocation = mock(MethodInvocation.class);
        DistributedLockExecutor executor = mock(DistributedLockExecutor.class);
        LockKeyResolver keyResolver = mock(LockKeyResolver.class);
        DistributedLockProperties properties = new DistributedLockProperties();

        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getThis()).thenReturn(target);
        when(invocation.getArguments()).thenReturn(new Object[]{arg});
        return new InvocationFixture(target, method, invocation, executor, keyResolver, properties);
    }

    private static final class InvocationFixture {
        private final SampleService target;
        private final Method method;
        private final MethodInvocation invocation;
        private final DistributedLockExecutor executor;
        private final LockKeyResolver keyResolver;
        private final DistributedLockProperties properties;

        private InvocationFixture(SampleService target, Method method, MethodInvocation invocation,
                                  DistributedLockExecutor executor, LockKeyResolver keyResolver,
                                  DistributedLockProperties properties) {
            this.target = target;
            this.method = method;
            this.invocation = invocation;
            this.executor = executor;
            this.keyResolver = keyResolver;
            this.properties = properties;
        }

        private DistributedLockInterceptor interceptor() {
            return new DistributedLockInterceptor(executor, keyResolver, properties);
        }

        private void stubResolvedKey(String expression, String resolved) {
            when(keyResolver.resolveKey(eq(method), any(Object[].class), eq(target), eq(expression)))
                    .thenReturn(resolved);
        }

        private void whenProceedReturn(Object value) throws Throwable {
            when(invocation.proceed()).thenReturn(value);
        }

        private void whenProceedThrow(Throwable throwable) throws Throwable {
            when(invocation.proceed()).thenThrow(throwable);
        }
    }
}
