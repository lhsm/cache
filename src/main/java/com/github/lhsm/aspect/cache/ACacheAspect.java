package com.github.lhsm.aspect.cache;

import com.github.lhsm.aspect.cache.key.ICacheKey;
import com.github.lhsm.aspect.cache.key.KeyGenerator;
import com.github.lhsm.cache.jmx.GuavaCacheMXBean;
import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheBuilderSpec;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.runtime.internal.AroundClosure;
import org.aspectj.runtime.reflect.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.cache.annotation.CachePut;
import javax.cache.annotation.CacheRemove;
import javax.cache.annotation.CacheRemoveAll;
import javax.cache.annotation.CacheResult;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class ACacheAspect {

    private Properties cacheProperties;

    private final static Logger LOGGER = LoggerFactory.getLogger(ACacheAspect.class);

    private final ConcurrentHashMap<String, LoadingCache<ICacheKey, Optional<Object>>> caches = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Resource
    public void setCacheProperties(Properties cacheProperties) {
        this.cacheProperties = cacheProperties;
    }

    public Properties getCacheProperties() {
        return cacheProperties;
    }

    public CacheBuilderSpec getCacheSpec(String cacheName) {
        return CacheBuilderSpec.parse(
                cacheProperties != null && cacheProperties.getProperty(cacheName) != null
                        ? cacheProperties.getProperty(cacheName)
                        : "maximumSize=0"
        );
    }

    protected ExecutorService getExecutor() {
        return executor;
    }

    protected static void registerBean(String cacheName, Cache<ICacheKey, Optional<Object>> cache) {
        new GuavaCacheMXBean(cacheName, cache);
    }

    protected Object getCachedResult(final CacheResult methodAnnotation, final ProceedingJoinPoint joinPoint) throws Throwable {
        String cacheName = methodAnnotation.cacheName();

        LoadingCache<ICacheKey, Optional<Object>> cache = getCache(cacheName, joinPoint);

        ICacheKey key = KeyGenerator.generate(joinPoint.getArgs());

        try {
            Object value = cache.getUnchecked(key).orNull();
            LOGGER.trace("Got for key {} value {}", new Object[]{cacheName, key, value});

            return value;
        } catch (UncheckedExecutionException e) {
            LOGGER.warn(
                    "Exception occurred while getting value for key {} from cache {} {}",
                    new Object[]{key, cacheName, e}
            );
            throw e.getCause();
        }
    }

    protected void removeCache(final CacheRemove methodAnnotation, final JoinPoint joinPoint) {
        String cacheName = methodAnnotation.cacheName();

        if (caches.containsKey(cacheName)) {
            ICacheKey key = KeyGenerator.generate(joinPoint.getArgs());
            caches.get(cacheName).invalidate(key);
            LOGGER.debug("Cache {} invalidated for key {}", cacheName, key);
        }
    }

    protected void removeCacheAll(final CacheRemoveAll methodAnnotation) {
        String cacheName = methodAnnotation.cacheName();

        if (caches.containsKey(cacheName)) {
            caches.get(cacheName).invalidateAll();
            LOGGER.debug("Cache {} invalidated all", cacheName);
        }
    }

    protected Object updateCache(final CachePut methodAnnotation, final ProceedingJoinPoint joinPoint) throws Throwable {
        String cacheName = methodAnnotation.cacheName();
        ICacheKey key = KeyGenerator.generate(joinPoint.getArgs());

        Cache<ICacheKey, Optional<Object>> cache = getCache(cacheName, joinPoint);

        Object value = joinPoint.proceed(key.getParameters());

        cache.put(key, Optional.fromNullable(value));

        LOGGER.debug("Cache {} updated for key {} with value {}", new Object[]{cacheName, key, value});

        return value;
    }

    protected LoadingCache<ICacheKey, Optional<Object>> getCache(final String cacheName, final ProceedingJoinPoint joinPoint) {
        if (caches.containsKey(cacheName)) {
            return caches.get(cacheName);
        }

        try {
            caches.computeIfAbsent(cacheName, cache -> buildCache(cacheName, joinPoint));
        } catch (Exception e) {
            LOGGER.error("Can't get cache {} with join point {} {}", new Object[]{cacheName, joinPoint, e});
        }

        return null;
    }

    private LoadingCache<ICacheKey, Optional<Object>> buildCache(String cacheName, final ProceedingJoinPoint joinPoint) {
        try {
            final AroundClosure aroundClosure = getAroundClosure(joinPoint);

            CacheLoader<ICacheKey, Optional<Object>> loader = new CacheLoader<ICacheKey, Optional<Object>>() {
                public Optional<Object> load(ICacheKey key) throws Exception {
                    try {
                        // TODO Убрать фикс после исправления дефекта в библиотеке aspectjrt
                        Object value = copyProceedingJoinPoint(joinPoint, aroundClosure).proceed(
                                key.getParameters());

                        LOGGER.debug("Cache {} loaded for key {} value {}", new Object[]{cacheName, key, value});
                        return Optional.fromNullable(value);
                    } catch (Exception e) {
                        LOGGER.warn("Cache {} could not load value for key {} {}", new Object[]{cacheName, key, e});
                        throw e;
                    } catch (Throwable t) {
                        LOGGER.warn("Cache {} could not load value for key {} {}", new Object[]{cacheName, key, t});
                        throw new RuntimeException(t);
                    }
                }

                public ListenableFuture<Optional<Object>> reload(final ICacheKey key, final Optional<Object> prevValue) {
                    ListenableFutureTask<Optional<Object>> task = ListenableFutureTask
                            .create(() -> {
                                try {
                                    // TODO Убрать фикс после исправления дефекта в библиотеке aspectjrt
                                    Object value = copyProceedingJoinPoint(joinPoint, aroundClosure).proceed(
                                            key.getParameters());

                                    LOGGER.debug(
                                            "Cache {} asynchronously reload for key {} value {}",
                                            new Object[]{cacheName, key, value}
                                    );
                                    return Optional.fromNullable(value);
                                } catch (Throwable t) {
                                    LOGGER.warn(
                                            "Cache {} could not asynchronously reload value for {} {}",
                                            new Object[]{cacheName, key, t}
                                    );
                                    return prevValue;
                                }
                            });

                    getExecutor().execute(task);

                    return task;
                }
            };

            CacheBuilderSpec spec = getCacheSpec(cacheName);

            LoadingCache<ICacheKey, Optional<Object>> cache = CacheBuilder.from(spec).recordStats().build(loader);

            registerBean(cacheName, cache);

            LOGGER.info("Cache {} from spec {} has been built", cacheName, spec);

            return cache;
        } catch (Exception e) {
            LOGGER.error("Can't get cache {} with join point {} {}", new Object[]{cacheName, joinPoint, e});
        }

        return null;
    }

    /**
     * Копирует join point для поддержки многопоточности. Подробнее MOBAPP-334. Убрать фикс после исправления дефекта в
     * библиотеке aspectjrt
     *
     * @param joinPoint
     * @param aroundClosure
     * @return копию join point
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     * @throws NoSuchMethodException
     * @throws SecurityException
     * @deprecated
     */
    @Deprecated
    private static ProceedingJoinPoint copyProceedingJoinPoint(ProceedingJoinPoint joinPoint, AroundClosure aroundClosure)
            throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        AroundClosure arc = aroundClosure.getClass().getConstructor(Object[].class)
                .newInstance((Object) updateState(aroundClosure.getState(), joinPoint));

        return arc.linkClosureAndJoinPoint(aroundClosure.getFlags());
    }

    /**
     * Получает around closure из join point через рефлексию
     *
     * @param joinPoint
     * @return around closure заданного join point
     * @throws NoSuchFieldException
     * @throws SecurityException
     * @throws IllegalArgumentException
     * @throws IllegalAccessException
     * @deprecated
     */
    @Deprecated
    private static AroundClosure getAroundClosure(ProceedingJoinPoint joinPoint) throws NoSuchFieldException,
            SecurityException, IllegalArgumentException, IllegalAccessException {
        Field privateField = joinPoint.getClass().getDeclaredField("arc");
        privateField.setAccessible(true);
        return (AroundClosure) privateField.get(joinPoint);
    }

    /**
     * Обновляет join point в списке состояний
     *
     * @param state
     * @param joinPoint
     * @return обновленый список состояний
     * @see AroundClosure#linkClosureAndJoinPoint(int)
     * @deprecated
     */
    @Deprecated
    private static Object[] updateState(Object[] state, ProceedingJoinPoint joinPoint) {
        Object[] newState = Arrays.copyOf(state, state.length);
        for (int i = 0; i < newState.length; i++) {
            if (newState[i] instanceof ProceedingJoinPoint) {
                newState[i] = Factory.makeJP(joinPoint.getStaticPart(), joinPoint.getThis(),
                        joinPoint.getTarget(), joinPoint.getArgs());
            }
        }
        return newState;
    }

}
