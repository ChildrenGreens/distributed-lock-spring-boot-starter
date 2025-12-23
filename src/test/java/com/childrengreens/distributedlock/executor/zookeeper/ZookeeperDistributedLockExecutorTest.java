package com.childrengreens.distributedlock.executor.zookeeper;

import com.childrengreens.distributedlock.annotation.LockType;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZookeeperDistributedLockExecutorTest {
    private TestingServer server;
    private CuratorFramework client;
    private ZookeeperDistributedLockExecutor executor;

    @BeforeAll
    void setUp() throws Exception {
        // Start embedded Zookeeper for real lock behavior.
        server = new TestingServer();
        client = CuratorFrameworkFactory.newClient(server.getConnectString(), new ExponentialBackoffRetry(100, 3));
        client.start();
        executor = new ZookeeperDistributedLockExecutor(client);
    }

    @AfterAll
    void tearDown() throws Exception {
        // Cleanup embedded resources.
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void tryLockAndUnlockReentrant() throws Exception {
        // Reentrant lock should be acquired and released.
        boolean acquired = executor.tryLock("order:1", LockType.REENTRANT, 1, 0, TimeUnit.SECONDS);

        assertThat(acquired).isTrue();
        executor.unlock("order:1", LockType.REENTRANT);
    }

    @Test
    void tryLockReadWriteLocks() throws Exception {
        // Read/write locks should be acquired independently.
        boolean readAcquired = executor.tryLock("catalog:1", LockType.READ, 1, 0, TimeUnit.SECONDS);
        boolean writeAcquired = executor.tryLock("catalog:2", LockType.WRITE, 1, 0, TimeUnit.SECONDS);

        assertThat(readAcquired).isTrue();
        assertThat(writeAcquired).isTrue();
        executor.unlock("catalog:1", LockType.READ);
        executor.unlock("catalog:2", LockType.WRITE);
    }

    @Test
    void unlockWithoutLockIsNoop() throws Exception {
        // Unlock without a stored lock should not throw.
        executor.unlock("missing", LockType.REENTRANT);
    }

    @Test
    void normalizePathReplacesColonAndAddsSlash() throws Exception {
        // Path normalization should map colon to slash and add root slash.
        Method normalize = ZookeeperDistributedLockExecutor.class.getDeclaredMethod("normalizePath", String.class);
        normalize.setAccessible(true);

        String normalized = (String) normalize.invoke(executor, "app:lock");

        assertThat(normalized).isEqualTo("/app/lock");
    }
}
