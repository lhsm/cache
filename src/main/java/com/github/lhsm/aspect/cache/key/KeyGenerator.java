package com.github.lhsm.aspect.cache.key;

import java.util.Objects;

public class KeyGenerator {

    public static ICacheKey generate(Object... params) {
        Objects.requireNonNull(params);

        if (params.length == 0) {
            return CacheKey.EMPTY;
        }

        if (params.length == 1 && params[0] != null) {
            return new SingleParameterCacheKey(params[0]);
        }

        return new CacheKey(params);
    }

}
