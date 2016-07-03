package com.github.lhsm.aspect.cache;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

import javax.cache.annotation.CacheRemove;
import javax.cache.annotation.CacheRemoveAll;
import javax.cache.annotation.CacheResult;

@Aspect
public class ServiceBaseCacheAspect extends ACacheAspect {

    @Pointcut("execution(public * *(..))")
    static void cachedMethod() {
    }

    @Around("cachedMethod() && @annotation(methodAnnotation)")
    public Object cacheResult(final CacheResult methodAnnotation, final ProceedingJoinPoint joinPoint) throws Throwable {
        return getCachedResult(methodAnnotation, joinPoint);
    }

    @Before("cachedMethod() && @annotation(methodAnnotation)")
    public void cacheRemove(final CacheRemove methodAnnotation, final JoinPoint joinPoint) {
        removeCache(methodAnnotation, joinPoint);
    }

    @Before("cachedMethod() && @annotation(methodAnnotation)")
    public void cacheRemoveAll(final CacheRemoveAll methodAnnotation) {
        removeCacheAll(methodAnnotation);
    }

}
