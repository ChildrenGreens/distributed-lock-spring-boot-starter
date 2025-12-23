package com.childrengreens.distributedlock.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Marks a method that should be executed under a distributed lock.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    /**
     * SpEL expression for the lock key. Empty means "use default key".
     */
    String key() default "";

    /**
     * Optional prefix for the lock key. Empty means "use global prefix".
     */
    String prefix() default "";

    /**
     * Time to wait for the lock before failing. Negative means "wait indefinitely".
     */
    long waitTime() default 0;

    /**
     * Lease duration for the lock. Non-positive means "use executor default".
     */
    long leaseTime() default -1;

    /**
     * Time unit for waitTime and leaseTime.
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * Lock implementation type.
     */
    LockType lockType() default LockType.REENTRANT;
}
