package com.childrengreens.distributedlock.aop;

import com.childrengreens.distributedlock.annotation.DistributedLock;
import com.childrengreens.distributedlock.core.DistributedLockException;
import com.childrengreens.distributedlock.core.DistributedLockExecutor;
import com.childrengreens.distributedlock.core.LockKeyResolver;
import com.childrengreens.distributedlock.autoconfigure.DistributedLockProperties;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * Intercepts annotated methods and wraps them with lock acquisition and release.
 */
public class DistributedLockInterceptor implements MethodInterceptor {
    private static final Logger log = LoggerFactory.getLogger(DistributedLockInterceptor.class);

    private final DistributedLockExecutor executor;
    private final LockKeyResolver keyResolver;
    private final DistributedLockProperties properties;

    public DistributedLockInterceptor(DistributedLockExecutor executor,
                                      LockKeyResolver keyResolver,
                                      DistributedLockProperties properties) {
        this.executor = executor;
        this.keyResolver = keyResolver;
        this.properties = properties;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Object target = invocation.getThis();
        Class<?> targetClass = target != null ? target.getClass() : method.getDeclaringClass();
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
        DistributedLock lock = AnnotatedElementUtils.findMergedAnnotation(specificMethod, DistributedLock.class);
        if (lock == null) {
            lock = AnnotatedElementUtils.findMergedAnnotation(method, DistributedLock.class);
        }
        if (lock == null) {
            return invocation.proceed();
        }
        String coreKey = keyResolver.resolveKey(specificMethod, invocation.getArguments(), target, lock.key());
        String lockKey = buildLockKey(lock, coreKey);
        boolean acquired;
        try {
            acquired = executor.tryLock(lockKey, lock.lockType(), lock.waitTime(), lock.leaseTime(), lock.timeUnit());
        } catch (Exception ex) {
            throw new DistributedLockException("Failed to acquire lock for key: " + lockKey, ex);
        }
        if (!acquired) {
            throw new DistributedLockException("Failed to acquire lock for key: " + lockKey);
        }
        try {
            return invocation.proceed();
        } finally {
            try {
                executor.unlock(lockKey, lock.lockType());
            } catch (Exception ex) {
                log.warn("Failed to release lock for key: {}", lockKey, ex);
            }
        }
    }

    private String buildLockKey(DistributedLock lock, String coreKey) {
        String prefix = StringUtils.hasText(lock.prefix()) ? lock.prefix() : properties.getPrefix();
        if (StringUtils.hasText(prefix)) {
            return prefix + ":" + coreKey;
        }
        return coreKey;
    }
}
