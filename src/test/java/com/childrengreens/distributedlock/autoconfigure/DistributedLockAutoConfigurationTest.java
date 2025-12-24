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
    private static final String PROVIDER_REDISSON = "redisson";
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
        assertExecutorType(withRedissonClient(contextRunner), RedissonDistributedLockExecutor.class);
    }

    @Test
    void selectsRedissonExecutorWhenProviderSpecified() {
        // Explicit provider selection should win when multiple clients exist.
        ApplicationContextRunner runner = withProvider(contextRunner, PROVIDER_REDISSON);
        runner = withRedissonClient(runner);
        runner = withZookeeperClient(runner);
        assertExecutorType(runner, RedissonDistributedLockExecutor.class);
    }

    @Test
    void doesNotCreateExecutorWhenProviderDoesNotMatch() {
        // Provider selection should disable non-matching executors.
        ApplicationContextRunner runner = withProvider(contextRunner, PROVIDER_REDISSON);
        runner = withZookeeperClient(runner);
        assertNoExecutor(runner);
    }

    @Test
    void createsZookeeperExecutorWhenClientPresent() {
        // Zookeeper executor should be chosen when CuratorFramework is available.
        assertExecutorType(withZookeeperClient(contextRunner), ZookeeperDistributedLockExecutor.class);
    }

    @Test
    void createsEtcdExecutorWhenClientPresent() {
        // Etcd executor should be chosen when Client is available.
        assertExecutorType(withEtcdClient(contextRunner), EtcdDistributedLockExecutor.class);
    }

    @Test
    void respectsUserProvidedExecutor() {
        // Custom executor should override auto-configured ones.
        DistributedLockExecutor customExecutor = mock(DistributedLockExecutor.class);
        ApplicationContextRunner runner = contextRunner
                .withBean(DistributedLockExecutor.class, () -> customExecutor);
        runner = withRedissonClient(runner);
        runner.run(context -> assertThat(context.getBean(DistributedLockExecutor.class)).isSameAs(customExecutor));
    }

    private static ApplicationContextRunner withProvider(ApplicationContextRunner runner, String provider) {
        return runner.withPropertyValues("distributed.lock.provider=" + provider);
    }

    private static ApplicationContextRunner withRedissonClient(ApplicationContextRunner runner) {
        return runner.withBean(RedissonClient.class, () -> mock(RedissonClient.class));
    }

    private static ApplicationContextRunner withZookeeperClient(ApplicationContextRunner runner) {
        return runner.withBean(CuratorFramework.class, () -> mock(CuratorFramework.class));
    }

    private static ApplicationContextRunner withEtcdClient(ApplicationContextRunner runner) {
        return runner.withBean(Client.class, () -> mock(Client.class));
    }

    private static void assertExecutorType(ApplicationContextRunner runner,
                                           Class<? extends DistributedLockExecutor> executorType) {
        runner.run(context -> {
            assertThat(context).hasSingleBean(DistributedLockExecutor.class);
            assertThat(context.getBean(DistributedLockExecutor.class)).isInstanceOf(executorType);
        });
    }

    private static void assertNoExecutor(ApplicationContextRunner runner) {
        runner.run(context -> assertThat(context).doesNotHaveBean(DistributedLockExecutor.class));
    }
}
