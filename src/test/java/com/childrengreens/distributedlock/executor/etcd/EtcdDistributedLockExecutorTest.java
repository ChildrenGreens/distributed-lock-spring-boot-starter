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
package com.childrengreens.distributedlock.executor.etcd;

import com.childrengreens.distributedlock.annotation.LockType;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Lock;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.lease.LeaseRevokeResponse;
import io.etcd.jetcd.lock.LockResponse;
import io.etcd.jetcd.lock.UnlockResponse;
import io.etcd.jetcd.support.CloseableClient;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EtcdDistributedLockExecutorTest {
    private static final String KEY = "k";
    private static final ByteSequence LOCK_KEY = ByteSequence.from(KEY, StandardCharsets.UTF_8);

    @Test
    void tryLockAcquiresAndUnlocks() throws Exception {
        // Successful lock should revoke lease on unlock.
        EtcdFixture fixture = fixture(12L, 1L);
        LockResponse lockResponse = mock(LockResponse.class);
        UnlockResponse unlockResponse = mock(UnlockResponse.class);
        LeaseRevokeResponse revokeResponse = mock(LeaseRevokeResponse.class);
        CloseableClient keepAliveClient = mock(CloseableClient.class);

        when(lockResponse.getKey()).thenReturn(LOCK_KEY);
        when(fixture.lockClient.lock(eq(LOCK_KEY), eq(12L))).thenReturn(CompletableFuture.completedFuture(lockResponse));
        when(fixture.lockClient.unlock(eq(LOCK_KEY))).thenReturn(CompletableFuture.completedFuture(unlockResponse));
        when(fixture.leaseClient.keepAlive(eq(12L), any(StreamObserver.class))).thenReturn(keepAliveClient);
        when(fixture.leaseClient.revoke(12L)).thenReturn(CompletableFuture.completedFuture(revokeResponse));

        EtcdDistributedLockExecutor executor = new EtcdDistributedLockExecutor(fixture.client);
        boolean acquired = executor.tryLock(KEY, LockType.REENTRANT, -1, 1, TimeUnit.SECONDS);

        assertThat(acquired).isTrue();
        executor.unlock(KEY, LockType.REENTRANT);
        verify(keepAliveClient).close();
        verify(fixture.lockClient).unlock(LOCK_KEY);
        verify(fixture.leaseClient).revoke(12L);
    }

    @Test
    void tryLockKeepsLeaseAliveWhileWaiting() throws Exception {
        // Wait time longer than lease time should enable keep-alive and refresh after acquire.
        EtcdFixture fixture = fixture(21L, 1L);
        LockResponse lockResponse = mock(LockResponse.class);
        CloseableClient keepAliveClient = mock(CloseableClient.class);

        when(fixture.leaseClient.keepAlive(eq(21L), any(StreamObserver.class))).thenReturn(keepAliveClient);
        when(lockResponse.getKey()).thenReturn(LOCK_KEY);
        when(fixture.lockClient.lock(eq(LOCK_KEY), eq(21L))).thenReturn(CompletableFuture.completedFuture(lockResponse));

        EtcdDistributedLockExecutor executor = new EtcdDistributedLockExecutor(fixture.client);
        boolean acquired = executor.tryLock(KEY, LockType.REENTRANT, 5, 1, TimeUnit.SECONDS);

        assertThat(acquired).isTrue();
        verify(keepAliveClient).close();
        verify(fixture.leaseClient).keepAliveOnce(21L);
    }

    @Test
    void tryLockReturnsFalseOnTimeout() throws Exception {
        // Timeout should cancel the lock attempt and revoke lease.
        EtcdFixture fixture = fixture(9L, 30L);
        CompletableFuture<LockResponse> future = mock(CompletableFuture.class);

        when(fixture.lockClient.lock(any(ByteSequence.class), eq(9L))).thenReturn(future);
        when(fixture.leaseClient.revoke(9L)).thenReturn(CompletableFuture.completedFuture(mock(LeaseRevokeResponse.class)));
        when(future.get(1, TimeUnit.SECONDS)).thenThrow(new TimeoutException("timeout"));

        EtcdDistributedLockExecutor executor = new EtcdDistributedLockExecutor(fixture.client);
        boolean acquired = executor.tryLock(KEY, LockType.REENTRANT, 1, 0, TimeUnit.SECONDS);

        assertThat(acquired).isFalse();
        verify(future).cancel(true);
        verify(fixture.leaseClient).revoke(9L);
    }

    @Test
    void tryLockReInterruptsWhenInterrupted() throws Exception {
        // Interrupted waits should preserve interrupt status.
        EtcdFixture fixture = fixture(7L, 30L);
        CompletableFuture<LockResponse> future = mock(CompletableFuture.class);

        when(fixture.lockClient.lock(any(ByteSequence.class), eq(7L))).thenReturn(future);
        when(fixture.leaseClient.revoke(7L)).thenReturn(CompletableFuture.completedFuture(mock(LeaseRevokeResponse.class)));
        when(future.get(1, TimeUnit.SECONDS)).thenThrow(new InterruptedException("interrupt"));

        EtcdDistributedLockExecutor executor = new EtcdDistributedLockExecutor(fixture.client);

        assertThatThrownBy(() -> executor.tryLock(KEY, LockType.REENTRANT, 1, 0, TimeUnit.SECONDS))
                .isInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    @Test
    void tryLockUsesDefaultLeaseWhenNonPositive() throws Exception {
        // Non-positive leaseTime uses executor default.
        EtcdFixture fixture = fixture(3L, 30L);
        LockResponse lockResponse = mock(LockResponse.class);
        CloseableClient keepAliveClient = mock(CloseableClient.class);

        when(fixture.lockClient.lock(any(ByteSequence.class), eq(3L))).thenReturn(CompletableFuture.completedFuture(lockResponse));
        when(fixture.leaseClient.keepAlive(eq(3L), any(StreamObserver.class))).thenReturn(keepAliveClient);

        EtcdDistributedLockExecutor executor = new EtcdDistributedLockExecutor(fixture.client);
        executor.tryLock(KEY, LockType.REENTRANT, -1, 0, TimeUnit.SECONDS);

        verify(keepAliveClient).close();
        verify(fixture.leaseClient).grant(30L);
    }

    @Test
    void tryLockUsesMinimumOneSecondLease() throws Exception {
        // Sub-second leases should be rounded up to 1 second.
        EtcdFixture fixture = fixture(5L, 1L);
        LockResponse lockResponse = mock(LockResponse.class);
        CloseableClient keepAliveClient = mock(CloseableClient.class);

        when(fixture.lockClient.lock(any(ByteSequence.class), eq(5L))).thenReturn(CompletableFuture.completedFuture(lockResponse));
        when(fixture.leaseClient.keepAlive(eq(5L), any(StreamObserver.class))).thenReturn(keepAliveClient);

        EtcdDistributedLockExecutor executor = new EtcdDistributedLockExecutor(fixture.client);
        executor.tryLock(KEY, LockType.REENTRANT, -1, 500, TimeUnit.MILLISECONDS);

        verify(keepAliveClient).close();
        verify(fixture.leaseClient).grant(1L);
    }

    private static EtcdFixture fixture(long leaseId, long leaseSeconds) {
        Client client = mock(Client.class);
        Lock lockClient = mock(Lock.class);
        Lease leaseClient = mock(Lease.class);
        LeaseGrantResponse grantResponse = mock(LeaseGrantResponse.class);

        when(client.getLockClient()).thenReturn(lockClient);
        when(client.getLeaseClient()).thenReturn(leaseClient);
        when(grantResponse.getID()).thenReturn(leaseId);
        when(leaseClient.grant(leaseSeconds)).thenReturn(CompletableFuture.completedFuture(grantResponse));
        return new EtcdFixture(client, lockClient, leaseClient);
    }

    private static final class EtcdFixture {
        private final Client client;
        private final Lock lockClient;
        private final Lease leaseClient;

        private EtcdFixture(Client client, Lock lockClient, Lease leaseClient) {
            this.client = client;
            this.lockClient = lockClient;
            this.leaseClient = leaseClient;
        }
    }
}
