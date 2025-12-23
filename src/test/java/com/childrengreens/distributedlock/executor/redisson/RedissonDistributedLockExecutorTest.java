package com.childrengreens.distributedlock.executor.redisson;

import com.childrengreens.distributedlock.annotation.LockType;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedissonDistributedLockExecutorTest {
    @Test
    void tryLockWaitsIndefinitelyWhenWaitTimeNegativeWithLease() throws Exception {
        // Negative waitTime means immediate lock with lease.
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("k1")).thenReturn(lock);

        RedissonDistributedLockExecutor executor = new RedissonDistributedLockExecutor(client);
        boolean acquired = executor.tryLock("k1", LockType.REENTRANT, -1, 5, TimeUnit.SECONDS);

        assertThat(acquired).isTrue();
        verify(lock).lock(5, TimeUnit.SECONDS);
    }

    @Test
    void tryLockWaitsIndefinitelyWithoutLeaseWhenWaitTimeNegative() throws Exception {
        // Negative waitTime with no lease uses lock() variant.
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("k2")).thenReturn(lock);

        RedissonDistributedLockExecutor executor = new RedissonDistributedLockExecutor(client);
        boolean acquired = executor.tryLock("k2", LockType.REENTRANT, -1, 0, TimeUnit.SECONDS);

        assertThat(acquired).isTrue();
        verify(lock).lock();
    }

    @Test
    void tryLockUsesTryLockWithEffectiveLease() throws Exception {
        // Non-negative waitTime should use tryLock with effective lease.
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("k3")).thenReturn(lock);
        when(lock.tryLock(3, -1, TimeUnit.SECONDS)).thenReturn(true);

        RedissonDistributedLockExecutor executor = new RedissonDistributedLockExecutor(client);
        boolean acquired = executor.tryLock("k3", LockType.REENTRANT, 3, 0, TimeUnit.SECONDS);

        assertThat(acquired).isTrue();
        verify(lock).tryLock(3, -1, TimeUnit.SECONDS);
    }

    @Test
    void resolvesFairLockFromClient() throws Exception {
        // FAIR lock maps to Redisson fair lock.
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getFairLock("fair")).thenReturn(lock);
        when(lock.tryLock(1, -1, TimeUnit.SECONDS)).thenReturn(true);

        RedissonDistributedLockExecutor executor = new RedissonDistributedLockExecutor(client);
        boolean acquired = executor.tryLock("fair", LockType.FAIR, 1, 0, TimeUnit.SECONDS);

        assertThat(acquired).isTrue();
        verify(client).getFairLock("fair");
    }

    @Test
    void resolvesReadWriteLocksFromClient() throws Exception {
        // READ/WRITE lock types map to read-write lock.
        RedissonClient client = mock(RedissonClient.class);
        RReadWriteLock readWriteLock = mock(RReadWriteLock.class);
        RLock readLock = mock(RLock.class);
        RLock writeLock = mock(RLock.class);

        when(client.getReadWriteLock("rw")).thenReturn(readWriteLock);
        when(readWriteLock.readLock()).thenReturn(readLock);
        when(readWriteLock.writeLock()).thenReturn(writeLock);
        when(readLock.tryLock(1, -1, TimeUnit.SECONDS)).thenReturn(true);
        when(writeLock.tryLock(1, -1, TimeUnit.SECONDS)).thenReturn(true);

        RedissonDistributedLockExecutor executor = new RedissonDistributedLockExecutor(client);
        boolean readAcquired = executor.tryLock("rw", LockType.READ, 1, 0, TimeUnit.SECONDS);
        boolean writeAcquired = executor.tryLock("rw", LockType.WRITE, 1, 0, TimeUnit.SECONDS);

        assertThat(readAcquired).isTrue();
        assertThat(writeAcquired).isTrue();
        verify(readWriteLock).readLock();
        verify(readWriteLock).writeLock();
    }

    @Test
    void unlockUsesStoredLock() throws Exception {
        // Unlock uses the lock stored in thread local.
        RedissonClient client = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        when(client.getLock("k4")).thenReturn(lock);

        RedissonDistributedLockExecutor executor = new RedissonDistributedLockExecutor(client);
        executor.tryLock("k4", LockType.REENTRANT, -1, 0, TimeUnit.SECONDS);
        executor.unlock("k4", LockType.REENTRANT);

        verify(lock).unlock();
    }
}
