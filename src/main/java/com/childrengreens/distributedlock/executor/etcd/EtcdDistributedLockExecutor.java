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
import com.childrengreens.distributedlock.executor.AbstractThreadLocalLockExecutor;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Lock;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.lease.LeaseKeepAliveResponse;
import io.etcd.jetcd.lock.LockResponse;
import io.etcd.jetcd.support.CloseableClient;
import io.grpc.stub.StreamObserver;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Etcd-based lock executor that manages a lease per lock acquisition.
 */
public class EtcdDistributedLockExecutor extends AbstractThreadLocalLockExecutor {
    private static final long DEFAULT_LEASE_SECONDS = 30;
    private static final StreamObserver<LeaseKeepAliveResponse> NOOP_KEEP_ALIVE_OBSERVER =
            new StreamObserver<>() {
                @Override
                public void onNext(LeaseKeepAliveResponse value) {
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onCompleted() {
                }
            };

    private final Lock lockClient;
    private final Lease leaseClient;

    public EtcdDistributedLockExecutor(Client client) {
        this.lockClient = client.getLockClient();
        this.leaseClient = client.getLeaseClient();
    }

    @Override
    public boolean tryLock(String key, LockType lockType, long waitTime, long leaseTime, TimeUnit unit) throws Exception {
        long leaseSeconds = resolveLeaseSeconds(leaseTime, unit);
        long leaseId = grantLease(leaseSeconds);
        CloseableClient keepAliveClient = startKeepAliveIfNeeded(leaseId, waitTime, unit, leaseSeconds);
        ByteSequence lockName = ByteSequence.from(key, StandardCharsets.UTF_8);
        CompletableFuture<LockResponse> future = lockClient.lock(lockName, leaseId);
        try {
            LockResponse response = waitTime < 0 ? future.get() : future.get(waitTime, unit);
            closeKeepAlive(keepAliveClient);
            keepAliveClient = null;
            refreshLease(leaseId);
            pushLock(key, lockType, new EtcdLockHolder(response.getKey(), leaseId));
            return true;
        } catch (TimeoutException ex) {
            future.cancel(true);
            closeKeepAlive(keepAliveClient);
            revokeLease(leaseId);
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            closeKeepAlive(keepAliveClient);
            revokeLease(leaseId);
            throw ex;
        } catch (ExecutionException ex) {
            closeKeepAlive(keepAliveClient);
            revokeLease(leaseId);
            throw ex;
        }
    }

    @Override
    public void unlock(String key, LockType lockType) throws Exception {
        Object holder = popLock(key, lockType);
        if (!(holder instanceof EtcdLockHolder lockHolder)) {
            return;
        }
        lockClient.unlock(lockHolder.lockKey()).get();
        revokeLease(lockHolder.leaseId());
    }

    private long grantLease(long leaseSeconds) throws ExecutionException, InterruptedException {
        LeaseGrantResponse response = leaseClient.grant(leaseSeconds).get();
        return response.getID();
    }

    private void revokeLease(long leaseId) throws ExecutionException, InterruptedException {
        leaseClient.revoke(leaseId).get();
    }

    private CloseableClient startKeepAliveIfNeeded(long leaseId, long waitTime, TimeUnit unit, long leaseSeconds) {
        if (shouldKeepAlive(waitTime, unit, leaseSeconds)) {
            return leaseClient.keepAlive(leaseId, NOOP_KEEP_ALIVE_OBSERVER);
        }
        return null;
    }

    private boolean shouldKeepAlive(long waitTime, TimeUnit unit, long leaseSeconds) {
        if (waitTime < 0) {
            return true;
        }
        long waitMillis = unit.toMillis(waitTime);
        long leaseMillis = TimeUnit.SECONDS.toMillis(leaseSeconds);
        return waitMillis >= leaseMillis;
    }

    private void closeKeepAlive(CloseableClient keepAliveClient) {
        if (keepAliveClient != null) {
            keepAliveClient.close();
        }
    }

    private void refreshLease(long leaseId) {
        leaseClient.keepAliveOnce(leaseId);
    }

    private long resolveLeaseSeconds(long leaseTime, TimeUnit unit) {
        if (leaseTime <= 0) {
            return DEFAULT_LEASE_SECONDS;
        }
        long seconds = unit.toSeconds(leaseTime);
        return Math.max(seconds, 1);
    }

    private record EtcdLockHolder(ByteSequence lockKey, long leaseId) {
    }
}
