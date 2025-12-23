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
package com.childrengreens.distributedlock.aop;

import com.childrengreens.distributedlock.annotation.DistributedLock;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DistributedLockAdvisorTest {
    interface Contract {
        @DistributedLock(key = "#p0")
        void run(String value);
    }

    static class ContractImpl implements Contract {
        @Override
        public void run(String value) {
        }
    }

    static class DirectAnnotation {
        @DistributedLock(key = "#p0")
        void execute(String value) {
        }
    }

    static class Plain {
        void execute(String value) {
        }
    }

    @Test
    void matchesAnnotatedInterfaceMethod() throws Exception {
        // Interface-level annotations should match the target method.
        Method method = Contract.class.getMethod("run", String.class);
        DistributedLockAdvisor advisor = new DistributedLockAdvisor(mock(MethodInterceptor.class));

        assertThat(advisor.matches(method, ContractImpl.class)).isTrue();
    }

    @Test
    void matchesAnnotatedMethod() throws Exception {
        // Direct annotations on concrete methods should match.
        Method method = DirectAnnotation.class.getDeclaredMethod("execute", String.class);
        DistributedLockAdvisor advisor = new DistributedLockAdvisor(mock(MethodInterceptor.class));

        assertThat(advisor.matches(method, DirectAnnotation.class)).isTrue();
    }

    @Test
    void doesNotMatchWhenNoAnnotation() throws Exception {
        // Plain methods should not be advised.
        Method method = Plain.class.getDeclaredMethod("execute", String.class);
        DistributedLockAdvisor advisor = new DistributedLockAdvisor(mock(MethodInterceptor.class));

        assertThat(advisor.matches(method, Plain.class)).isFalse();
    }
}
