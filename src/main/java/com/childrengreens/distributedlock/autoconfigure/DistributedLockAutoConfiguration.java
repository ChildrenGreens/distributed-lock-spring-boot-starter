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
import com.childrengreens.distributedlock.aop.DistributedLockInterceptor;
import com.childrengreens.distributedlock.core.DistributedLockExecutor;
import com.childrengreens.distributedlock.core.LockKeyResolver;
import com.childrengreens.distributedlock.core.SpelLockKeyResolver;
import com.childrengreens.distributedlock.executor.etcd.EtcdDistributedLockExecutor;
import com.childrengreens.distributedlock.executor.redisson.RedissonDistributedLockExecutor;
import com.childrengreens.distributedlock.executor.zookeeper.ZookeeperDistributedLockExecutor;
import io.etcd.jetcd.Client;
import org.apache.curator.framework.CuratorFramework;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configures distributed lock infrastructure and executors.
 */
@AutoConfiguration
@EnableConfigurationProperties(DistributedLockProperties.class)
public class DistributedLockAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public LockKeyResolver lockKeyResolver() {
        return new SpelLockKeyResolver();
    }

    @Bean
    @ConditionalOnBean(DistributedLockExecutor.class)
    @ConditionalOnMissingBean
    public DistributedLockAdvisor distributedLockAdvisor(DistributedLockExecutor executor,
                                                         LockKeyResolver lockKeyResolver,
                                                         DistributedLockProperties properties) {
        DistributedLockInterceptor interceptor = new DistributedLockInterceptor(executor, lockKeyResolver, properties);
        return new DistributedLockAdvisor(interceptor);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnBean(RedissonClient.class)
    static class RedissonExecutorConfiguration {
        @Bean
        @ConditionalOnMissingBean(DistributedLockExecutor.class)
        public DistributedLockExecutor redissonDistributedLockExecutor(RedissonClient client) {
            return new RedissonDistributedLockExecutor(client);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(CuratorFramework.class)
    @ConditionalOnBean(CuratorFramework.class)
    static class ZookeeperExecutorConfiguration {
        @Bean
        @ConditionalOnMissingBean(DistributedLockExecutor.class)
        public DistributedLockExecutor zookeeperDistributedLockExecutor(CuratorFramework client) {
            return new ZookeeperDistributedLockExecutor(client);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Client.class)
    @ConditionalOnBean(Client.class)
    static class EtcdExecutorConfiguration {
        @Bean
        @ConditionalOnMissingBean(DistributedLockExecutor.class)
        public DistributedLockExecutor etcdDistributedLockExecutor(Client client) {
            return new EtcdDistributedLockExecutor(client);
        }
    }
}
