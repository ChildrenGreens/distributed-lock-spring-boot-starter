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
        SampleService target = new SampleService();
        Method method = SampleService.class.getDeclaredMethod("plain", String.class);
        MethodInvocation invocation = mock(MethodInvocation.class);
        DistributedLockExecutor executor = mock(DistributedLockExecutor.class);
        LockKeyResolver keyResolver = mock(LockKeyResolver.class);
        DistributedLockProperties properties = new DistributedLockProperties();

        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getThis()).thenReturn(target);
        when(invocation.getArguments()).thenReturn(new Object[]{"x"});
        when(invocation.proceed()).thenReturn("plain:x");

        DistributedLockInterceptor interceptor = new DistributedLockInterceptor(executor, keyResolver, properties);
        Object result = interceptor.invoke(invocation);

        assertThat(result).isEqualTo("plain:x");
        verify(executor, never()).tryLock(any(), any(), anyLong(), anyLong(), any());
        verify(executor, never()).unlock(any(), any());
    }

    @Test
    void invokeAcquiresAndReleasesLock() throws Throwable {
        // Happy path: lock then proceed then unlock.
        SampleService target = new SampleService();
        Method method = SampleService.class.getDeclaredMethod("locked", String.class);
        MethodInvocation invocation = mock(MethodInvocation.class);
        DistributedLockExecutor executor = mock(DistributedLockExecutor.class);
        LockKeyResolver keyResolver = mock(LockKeyResolver.class);
        DistributedLockProperties properties = new DistributedLockProperties();

        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getThis()).thenReturn(target);
        when(invocation.getArguments()).thenReturn(new Object[]{"y"});
        when(invocation.proceed()).thenReturn("locked:y");
        when(keyResolver.resolveKey(eq(method), any(Object[].class), eq(target), eq("#p0"))).thenReturn("core");
        when(executor.tryLock(eq("method:core"), eq(LockType.FAIR), eq(5L), eq(10L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        DistributedLockInterceptor interceptor = new DistributedLockInterceptor(executor, keyResolver, properties);
        Object result = interceptor.invoke(invocation);

        assertThat(result).isEqualTo("locked:y");
        verify(executor).unlock("method:core", LockType.FAIR);
    }

    @Test
    void invokeUsesGlobalPrefixWhenMethodPrefixMissing() throws Throwable {
        // Method-level prefix should fall back to global prefix.
        SampleService target = new SampleService();
        Method method = SampleService.class.getDeclaredMethod("defaultPrefix", String.class);
        MethodInvocation invocation = mock(MethodInvocation.class);
        DistributedLockExecutor executor = mock(DistributedLockExecutor.class);
        LockKeyResolver keyResolver = mock(LockKeyResolver.class);
        DistributedLockProperties properties = new DistributedLockProperties();
        properties.setPrefix("global");

        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getThis()).thenReturn(target);
        when(invocation.getArguments()).thenReturn(new Object[]{"z"});
        when(invocation.proceed()).thenReturn("default:z");
        when(keyResolver.resolveKey(eq(method), any(Object[].class), eq(target), eq("#p0"))).thenReturn("core");
        when(executor.tryLock(eq("global:core"), eq(LockType.REENTRANT), eq(0L), eq(-1L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        DistributedLockInterceptor interceptor = new DistributedLockInterceptor(executor, keyResolver, properties);
        Object result = interceptor.invoke(invocation);

        assertThat(result).isEqualTo("default:z");
        verify(executor).unlock("global:core", LockType.REENTRANT);
    }

    @Test
    void invokeThrowsWhenLockNotAcquired() throws Throwable {
        // Failed acquisition should surface as exception.
        SampleService target = new SampleService();
        Method method = SampleService.class.getDeclaredMethod("locked", String.class);
        MethodInvocation invocation = mock(MethodInvocation.class);
        DistributedLockExecutor executor = mock(DistributedLockExecutor.class);
        LockKeyResolver keyResolver = mock(LockKeyResolver.class);
        DistributedLockProperties properties = new DistributedLockProperties();

        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getThis()).thenReturn(target);
        when(invocation.getArguments()).thenReturn(new Object[]{"x"});
        when(keyResolver.resolveKey(eq(method), any(Object[].class), eq(target), eq("#p0"))).thenReturn("core");
        when(executor.tryLock(eq("method:core"), eq(LockType.FAIR), eq(5L), eq(10L), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        DistributedLockInterceptor interceptor = new DistributedLockInterceptor(executor, keyResolver, properties);

        assertThatThrownBy(() -> interceptor.invoke(invocation))
                .isInstanceOf(DistributedLockException.class)
                .hasMessageContaining("Failed to acquire lock for key: method:core");
        verify(executor, never()).unlock(any(), any());
    }

    @Test
    void invokeWrapsExceptionsFromExecutor() throws Throwable {
        // Executor exceptions are wrapped with lock context.
        SampleService target = new SampleService();
        Method method = SampleService.class.getDeclaredMethod("locked", String.class);
        MethodInvocation invocation = mock(MethodInvocation.class);
        DistributedLockExecutor executor = mock(DistributedLockExecutor.class);
        LockKeyResolver keyResolver = mock(LockKeyResolver.class);
        DistributedLockProperties properties = new DistributedLockProperties();

        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getThis()).thenReturn(target);
        when(invocation.getArguments()).thenReturn(new Object[]{"x"});
        when(keyResolver.resolveKey(eq(method), any(Object[].class), eq(target), eq("#p0"))).thenReturn("core");
        when(executor.tryLock(eq("method:core"), eq(LockType.FAIR), eq(5L), eq(10L), eq(TimeUnit.SECONDS)))
                .thenThrow(new IllegalStateException("boom"));

        DistributedLockInterceptor interceptor = new DistributedLockInterceptor(executor, keyResolver, properties);

        assertThatThrownBy(() -> interceptor.invoke(invocation))
                .isInstanceOf(DistributedLockException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void invokeAlwaysUnlocksEvenWhenProceedThrows() throws Throwable {
        // Unlock should still run on invocation failure.
        SampleService target = new SampleService();
        Method method = SampleService.class.getDeclaredMethod("locked", String.class);
        MethodInvocation invocation = mock(MethodInvocation.class);
        DistributedLockExecutor executor = mock(DistributedLockExecutor.class);
        LockKeyResolver keyResolver = mock(LockKeyResolver.class);
        DistributedLockProperties properties = new DistributedLockProperties();

        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getThis()).thenReturn(target);
        when(invocation.getArguments()).thenReturn(new Object[]{"x"});
        when(keyResolver.resolveKey(eq(method), any(Object[].class), eq(target), eq("#p0"))).thenReturn("core");
        when(executor.tryLock(eq("method:core"), eq(LockType.FAIR), eq(5L), eq(10L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(invocation.proceed()).thenThrow(new IllegalArgumentException("fail"));

        DistributedLockInterceptor interceptor = new DistributedLockInterceptor(executor, keyResolver, properties);

        assertThatThrownBy(() -> interceptor.invoke(invocation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fail");
        verify(executor).unlock("method:core", LockType.FAIR);
    }

    @Test
    void invokeSwallowsUnlockExceptions() throws Throwable {
        // Unlock failures are logged, not propagated.
        SampleService target = new SampleService();
        Method method = SampleService.class.getDeclaredMethod("locked", String.class);
        MethodInvocation invocation = mock(MethodInvocation.class);
        DistributedLockExecutor executor = mock(DistributedLockExecutor.class);
        LockKeyResolver keyResolver = mock(LockKeyResolver.class);
        DistributedLockProperties properties = new DistributedLockProperties();

        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getThis()).thenReturn(target);
        when(invocation.getArguments()).thenReturn(new Object[]{"x"});
        when(invocation.proceed()).thenReturn("locked:x");
        when(keyResolver.resolveKey(eq(method), any(Object[].class), eq(target), eq("#p0"))).thenReturn("core");
        when(executor.tryLock(eq("method:core"), eq(LockType.FAIR), eq(5L), eq(10L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        doThrow(new IllegalStateException("unlock"))
                .when(executor).unlock("method:core", LockType.FAIR);

        DistributedLockInterceptor interceptor = new DistributedLockInterceptor(executor, keyResolver, properties);
        Object result = interceptor.invoke(invocation);

        assertThat(result).isEqualTo("locked:x");
    }
}
