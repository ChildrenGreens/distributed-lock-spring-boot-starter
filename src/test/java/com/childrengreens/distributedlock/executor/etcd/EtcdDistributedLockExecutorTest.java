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
    @Test
    void tryLockAcquiresAndUnlocks() throws Exception {
        // Successful lock should revoke lease on unlock.
        Client client = mock(Client.class);
        Lock lockClient = mock(Lock.class);
        Lease leaseClient = mock(Lease.class);
        LeaseGrantResponse grantResponse = mock(LeaseGrantResponse.class);
        LockResponse lockResponse = mock(LockResponse.class);
        UnlockResponse unlockResponse = mock(UnlockResponse.class);
        LeaseRevokeResponse revokeResponse = mock(LeaseRevokeResponse.class);

        when(client.getLockClient()).thenReturn(lockClient);
        when(client.getLeaseClient()).thenReturn(leaseClient);
        when(grantResponse.getID()).thenReturn(12L);
        when(leaseClient.grant(1L)).thenReturn(CompletableFuture.completedFuture(grantResponse));

        ByteSequence lockKey = ByteSequence.from("k", StandardCharsets.UTF_8);
        when(lockResponse.getKey()).thenReturn(lockKey);
        when(lockClient.lock(eq(lockKey), eq(12L))).thenReturn(CompletableFuture.completedFuture(lockResponse));
        when(lockClient.unlock(eq(lockKey))).thenReturn(CompletableFuture.completedFuture(unlockResponse));
        when(leaseClient.revoke(12L)).thenReturn(CompletableFuture.completedFuture(revokeResponse));

        EtcdDistributedLockExecutor executor = new EtcdDistributedLockExecutor(client);
        boolean acquired = executor.tryLock("k", LockType.REENTRANT, -1, 1, TimeUnit.SECONDS);

        assertThat(acquired).isTrue();
        executor.unlock("k", LockType.REENTRANT);
        verify(lockClient).unlock(lockKey);
        verify(leaseClient).revoke(12L);
    }

    @Test
    void tryLockReturnsFalseOnTimeout() throws Exception {
        // Timeout should cancel the lock attempt and revoke lease.
        Client client = mock(Client.class);
        Lock lockClient = mock(Lock.class);
        Lease leaseClient = mock(Lease.class);
        LeaseGrantResponse grantResponse = mock(LeaseGrantResponse.class);
        CompletableFuture<LockResponse> future = mock(CompletableFuture.class);

        when(client.getLockClient()).thenReturn(lockClient);
        when(client.getLeaseClient()).thenReturn(leaseClient);
        when(grantResponse.getID()).thenReturn(9L);
        when(leaseClient.grant(30L)).thenReturn(CompletableFuture.completedFuture(grantResponse));
        when(lockClient.lock(any(ByteSequence.class), eq(9L))).thenReturn(future);
        when(leaseClient.revoke(9L)).thenReturn(CompletableFuture.completedFuture(mock(LeaseRevokeResponse.class)));
        when(future.get(1, TimeUnit.SECONDS)).thenThrow(new TimeoutException("timeout"));

        EtcdDistributedLockExecutor executor = new EtcdDistributedLockExecutor(client);
        boolean acquired = executor.tryLock("k", LockType.REENTRANT, 1, 0, TimeUnit.SECONDS);

        assertThat(acquired).isFalse();
        verify(future).cancel(true);
        verify(leaseClient).revoke(9L);
    }

    @Test
    void tryLockReInterruptsWhenInterrupted() throws Exception {
        // Interrupted waits should preserve interrupt status.
        Client client = mock(Client.class);
        Lock lockClient = mock(Lock.class);
        Lease leaseClient = mock(Lease.class);
        LeaseGrantResponse grantResponse = mock(LeaseGrantResponse.class);
        CompletableFuture<LockResponse> future = mock(CompletableFuture.class);

        when(client.getLockClient()).thenReturn(lockClient);
        when(client.getLeaseClient()).thenReturn(leaseClient);
        when(grantResponse.getID()).thenReturn(7L);
        when(leaseClient.grant(30L)).thenReturn(CompletableFuture.completedFuture(grantResponse));
        when(lockClient.lock(any(ByteSequence.class), eq(7L))).thenReturn(future);
        when(leaseClient.revoke(7L)).thenReturn(CompletableFuture.completedFuture(mock(LeaseRevokeResponse.class)));
        when(future.get(1, TimeUnit.SECONDS)).thenThrow(new InterruptedException("interrupt"));

        EtcdDistributedLockExecutor executor = new EtcdDistributedLockExecutor(client);

        assertThatThrownBy(() -> executor.tryLock("k", LockType.REENTRANT, 1, 0, TimeUnit.SECONDS))
                .isInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    @Test
    void tryLockUsesDefaultLeaseWhenNonPositive() throws Exception {
        // Non-positive leaseTime uses executor default.
        Client client = mock(Client.class);
        Lock lockClient = mock(Lock.class);
        Lease leaseClient = mock(Lease.class);
        LeaseGrantResponse grantResponse = mock(LeaseGrantResponse.class);
        LockResponse lockResponse = mock(LockResponse.class);

        when(client.getLockClient()).thenReturn(lockClient);
        when(client.getLeaseClient()).thenReturn(leaseClient);
        when(grantResponse.getID()).thenReturn(3L);
        when(leaseClient.grant(30L)).thenReturn(CompletableFuture.completedFuture(grantResponse));
        when(lockClient.lock(any(ByteSequence.class), eq(3L))).thenReturn(CompletableFuture.completedFuture(lockResponse));

        EtcdDistributedLockExecutor executor = new EtcdDistributedLockExecutor(client);
        executor.tryLock("k", LockType.REENTRANT, -1, 0, TimeUnit.SECONDS);

        verify(leaseClient).grant(30L);
    }

    @Test
    void tryLockUsesMinimumOneSecondLease() throws Exception {
        // Sub-second leases should be rounded up to 1 second.
        Client client = mock(Client.class);
        Lock lockClient = mock(Lock.class);
        Lease leaseClient = mock(Lease.class);
        LeaseGrantResponse grantResponse = mock(LeaseGrantResponse.class);
        LockResponse lockResponse = mock(LockResponse.class);

        when(client.getLockClient()).thenReturn(lockClient);
        when(client.getLeaseClient()).thenReturn(leaseClient);
        when(grantResponse.getID()).thenReturn(5L);
        when(leaseClient.grant(1L)).thenReturn(CompletableFuture.completedFuture(grantResponse));
        when(lockClient.lock(any(ByteSequence.class), eq(5L))).thenReturn(CompletableFuture.completedFuture(lockResponse));

        EtcdDistributedLockExecutor executor = new EtcdDistributedLockExecutor(client);
        executor.tryLock("k", LockType.REENTRANT, -1, 500, TimeUnit.MILLISECONDS);

        verify(leaseClient).grant(1L);
    }
}
