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
package com.childrengreens.distributedlock.core;

import org.jspecify.annotations.NonNull;
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
    public void setBeanFactory(@NonNull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
