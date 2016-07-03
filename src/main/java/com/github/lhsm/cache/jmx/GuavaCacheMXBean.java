package com.github.lhsm.cache.jmx;

import com.google.common.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.Objects;

public class GuavaCacheMXBean implements CacheMXBean {

    private final static Logger LOGGER = LoggerFactory.getLogger(GuavaCacheMXBean.class);

    @SuppressWarnings("rawtypes")
    private final Cache cache;

    public GuavaCacheMXBean(String cacheName, @SuppressWarnings("rawtypes") Cache cache) {
        this.cache = Objects.requireNonNull(cache);
        try {
            String name = String.format("%s:type=Cache,name=%s", this.cache.getClass().getPackage().getName(), cacheName);
            ObjectName mxBeanName = new ObjectName(name);
            if (!ManagementFactory.getPlatformMBeanServer().isRegistered(mxBeanName)) {
                ManagementFactory.getPlatformMBeanServer().registerMBean(this, new ObjectName(name));
            }
        } catch (MalformedObjectNameException
                | InstanceAlreadyExistsException
                | MBeanRegistrationException
                | NotCompliantMBeanException ex) {
            LOGGER.warn("Error while registering mbean {} {}", cacheName, ex);
        }
    }

    @Override
    public long getRequestCount() {
        return cache.stats().requestCount();
    }

    @Override
    public long getHitCount() {
        return cache.stats().hitCount();
    }

    @Override
    public double getHitRate() {
        return cache.stats().hitRate();
    }

    @Override
    public long getMissCount() {
        return cache.stats().missCount();
    }

    @Override
    public double getMissRate() {
        return cache.stats().missRate();
    }

    @Override
    public long getLoadCount() {
        return cache.stats().loadCount();
    }

    @Override
    public long getLoadSuccessCount() {
        return cache.stats().loadSuccessCount();
    }

    @Override
    public long getLoadExceptionCount() {
        return cache.stats().loadExceptionCount();
    }

    @Override
    public double getLoadExceptionRate() {
        return cache.stats().loadExceptionRate();
    }

    @Override
    public long getTotalLoadTime() {
        return cache.stats().totalLoadTime();
    }

    @Override
    public double getAverageLoadPenalty() {
        return cache.stats().averageLoadPenalty();
    }

    @Override
    public long getEvictionCount() {
        return cache.stats().evictionCount();
    }

    @Override
    public long getSize() {
        return cache.size();
    }

    @Override
    public void cleanUp() {
        cache.cleanUp();
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }

}