package com.childrengreens.distributedlock.aop;

import com.childrengreens.distributedlock.annotation.DistributedLock;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcutAdvisor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;

/**
 * Pointcut advisor that targets methods annotated with {@link DistributedLock}.
 */
public class DistributedLockAdvisor extends StaticMethodMatcherPointcutAdvisor {
    public DistributedLockAdvisor(MethodInterceptor interceptor) {
        setAdvice(interceptor);
        setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        Class<?> userClass = targetClass != null ? targetClass : method.getDeclaringClass();
        Method specificMethod = AopUtils.getMostSpecificMethod(method, userClass);
        return AnnotatedElementUtils.hasAnnotation(specificMethod, DistributedLock.class)
                || AnnotatedElementUtils.hasAnnotation(method, DistributedLock.class);
    }
}
