package com.childrengreens.distributedlock.core;

import java.lang.reflect.Method;

/**
 * Resolves a lock key for a given method invocation.
 */
public interface LockKeyResolver {
    String resolveKey(Method method, Object[] args, Object target, String keyExpression);
}
