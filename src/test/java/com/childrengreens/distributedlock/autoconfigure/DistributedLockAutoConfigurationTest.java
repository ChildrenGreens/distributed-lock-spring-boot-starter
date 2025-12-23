package com.childrengreens.distributedlock.autoconfigure;

import com.childrengreens.distributedlock.aop.DistributedLockAdvisor;
import com.childrengreens.distributedlock.core.DistributedLockExecutor;
import com.childrengreens.distributedlock.core.LockKeyResolver;
import com.childrengreens.distributedlock.executor.etcd.EtcdDistributedLockExecutor;
import com.childrengreens.distributedlock.executor.redisson.RedissonDistributedLockExecutor;
import com.childrengreens.distributedlock.executor.zookeeper.ZookeeperDistributedLockExecutor;
import io.etcd.jetcd.Client;
import org.apache.curator.framework.CuratorFramework;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DistributedLockAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DistributedLockAutoConfiguration.class));

    @Test
    void providesLockKeyResolverByDefault() {
        // Default beans should be registered without user configuration.
        contextRunner.run(context -> assertThat(context).hasSingleBean(LockKeyResolver.class));
    }

    @Test
    void doesNotCreateAdvisorWithoutExecutor() {
        // Advisor should not exist until an executor is present.
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(DistributedLockAdvisor.class));
    }

    @Test
    void createsAdvisorWhenExecutorPresent() {
        // Advisor should be created when an executor bean exists.
        contextRunner.withBean(DistributedLockExecutor.class, () -> mock(DistributedLockExecutor.class))
                .run(context -> assertThat(context).hasSingleBean(DistributedLockAdvisor.class));
    }

    @Test
    void createsRedissonExecutorWhenClientPresent() {
        // Redisson executor should be chosen when RedissonClient is available.
        contextRunner.withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedLockExecutor.class);
                    assertThat(context.getBean(DistributedLockExecutor.class))
                            .isInstanceOf(RedissonDistributedLockExecutor.class);
                });
    }

    @Test
    void createsZookeeperExecutorWhenClientPresent() {
        // Zookeeper executor should be chosen when CuratorFramework is available.
        contextRunner.withBean(CuratorFramework.class, () -> mock(CuratorFramework.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedLockExecutor.class);
                    assertThat(context.getBean(DistributedLockExecutor.class))
                            .isInstanceOf(ZookeeperDistributedLockExecutor.class);
                });
    }

    @Test
    void createsEtcdExecutorWhenClientPresent() {
        // Etcd executor should be chosen when Client is available.
        contextRunner.withBean(Client.class, () -> mock(Client.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedLockExecutor.class);
                    assertThat(context.getBean(DistributedLockExecutor.class))
                            .isInstanceOf(EtcdDistributedLockExecutor.class);
                });
    }

    @Test
    void respectsUserProvidedExecutor() {
        // Custom executor should override auto-configured ones.
        DistributedLockExecutor customExecutor = mock(DistributedLockExecutor.class);
        contextRunner
                .withBean(DistributedLockExecutor.class, () -> customExecutor)
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> assertThat(context.getBean(DistributedLockExecutor.class)).isSameAs(customExecutor));
    }
}
