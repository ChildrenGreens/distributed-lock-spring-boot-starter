package com.childrengreens.distributedlock.executor;

import com.childrengreens.distributedlock.annotation.LockType;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractThreadLocalLockExecutorTest {
    static class TestExecutor extends AbstractThreadLocalLockExecutor {
        @Override
        public boolean tryLock(String key, LockType lockType, long waitTime, long leaseTime, TimeUnit unit) {
            return false;
        }

        @Override
        public void unlock(String key, LockType lockType) {
        }

        void push(String key, LockType lockType, Object lock) {
            pushLock(key, lockType, lock);
        }

        Object pop(String key, LockType lockType) {
            return popLock(key, lockType);
        }
    }

    @Test
    void popReturnsLastPushedLock() {
        // Locks should be popped in LIFO order.
        TestExecutor executor = new TestExecutor();
        executor.push("order", LockType.REENTRANT, "first");
        executor.push("order", LockType.REENTRANT, "second");

        assertThat(executor.pop("order", LockType.REENTRANT)).isEqualTo("second");
        assertThat(executor.pop("order", LockType.REENTRANT)).isEqualTo("first");
        assertThat(executor.pop("order", LockType.REENTRANT)).isNull();
    }

    @Test
    void lockHoldersAreThreadLocal() throws Exception {
        // Locks are stored per-thread, not shared.
        TestExecutor executor = new TestExecutor();
        executor.push("job", LockType.FAIR, "main");

        AtomicReference<Object> otherThreadPop = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            otherThreadPop.set(executor.pop("job", LockType.FAIR));
            latch.countDown();
        });
        thread.start();
        latch.await(5, TimeUnit.SECONDS);

        assertThat(otherThreadPop.get()).isNull();
        assertThat(executor.pop("job", LockType.FAIR)).isEqualTo("main");
    }
}
