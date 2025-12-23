package com.childrengreens.distributedlock.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cache.interceptor.SimpleKeyGenerator;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SpelLockKeyResolverTest {
    static class SampleService {
        String handle(String name, int count) {
            return name + count;
        }
    }

    static class PrefixProvider {
        private final String prefix;

        PrefixProvider(String prefix) {
            this.prefix = prefix;
        }

        public String getPrefix() {
            return prefix;
        }
    }

    @Test
    void resolveKeyUsesDefaultWhenExpressionEmpty() throws Exception {
        // Default key is class + method + generated args key.
        SpelLockKeyResolver resolver = new SpelLockKeyResolver();
        Method method = SampleService.class.getDeclaredMethod("handle", String.class, int.class);
        Object[] args = {"alpha", 2};

        String resolved = resolver.resolveKey(method, args, new SampleService(), "");

        Object simpleKey = SimpleKeyGenerator.generateKey(args);
        String expected = SampleService.class.getName() + "." + method.getName() + ":" + simpleKey;
        assertThat(resolved).isEqualTo(expected);
    }

    @Test
    void resolveKeyUsesSpelExpression() throws Exception {
        // SpEL should resolve method arguments.
        SpelLockKeyResolver resolver = new SpelLockKeyResolver();
        Method method = SampleService.class.getDeclaredMethod("handle", String.class, int.class);

        String resolved = resolver.resolveKey(method, new Object[]{"beta", 3}, new SampleService(), "#p0 + '-' + #p1");

        assertThat(resolved).isEqualTo("beta-3");
    }

    @Test
    void resolveKeyCanReferenceBeans() throws Exception {
        // Bean references should be resolved via BeanFactory.
        SpelLockKeyResolver resolver = new SpelLockKeyResolver();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("prefixProvider", new PrefixProvider("svc"));
        resolver.setBeanFactory(beanFactory);
        Method method = SampleService.class.getDeclaredMethod("handle", String.class, int.class);

        String resolved = resolver.resolveKey(method, new Object[]{"gamma", 1}, new SampleService(),
                "@prefixProvider.prefix + ':' + #p0");

        assertThat(resolved).isEqualTo("svc:gamma");
    }

    @Test
    void resolveKeyReturnsLiteralNullWhenExpressionEvaluatesToNull() throws Exception {
        // Null evaluation should map to literal \"null\".
        SpelLockKeyResolver resolver = new SpelLockKeyResolver();
        Method method = SampleService.class.getDeclaredMethod("handle", String.class, int.class);

        String resolved = resolver.resolveKey(method, new Object[]{null, 1}, new SampleService(), "#p0");

        assertThat(resolved).isEqualTo("null");
    }
}
