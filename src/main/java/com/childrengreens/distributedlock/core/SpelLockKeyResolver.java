package com.childrengreens.distributedlock.core;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * Resolves lock keys using SpEL, falling back to a deterministic key.
 */
public class SpelLockKeyResolver implements LockKeyResolver, BeanFactoryAware {
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();
    private BeanFactory beanFactory;

    @Override
    public String resolveKey(Method method, Object[] args, Object target, String keyExpression) {
        if (!StringUtils.hasText(keyExpression)) {
            Object key = SimpleKeyGenerator.generateKey(args);
            return method.getDeclaringClass().getName() + "." + method.getName() + ":" + key;
        }
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(target, method, args, nameDiscoverer);
        if (beanFactory != null) {
            context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        }
        Object value = parser.parseExpression(keyExpression).getValue(context);
        return value == null ? "null" : value.toString();
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
