package com.childrengreens.distributedlock.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for distributed locking.
 */
@ConfigurationProperties(prefix = "distributed.lock")
public class DistributedLockProperties {
    private String prefix = "lock";

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
