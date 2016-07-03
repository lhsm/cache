package com.github.lhsm.aspect.cache.key;

import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

public final class CacheKey implements ICacheKey, Serializable {

    private static final long serialVersionUID = 1L;

    public static final ICacheKey EMPTY = new CacheKey();

    private final Object[] params;

    public CacheKey(Object... elements) {
        this.params = Arrays.copyOf(Objects.requireNonNull(elements), elements.length);
        System.arraycopy(elements, 0, this.params, 0, elements.length);
    }

    @Override
    public Object[] getParameters() {
        return Arrays.copyOf(params, params.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKey cacheKey = (CacheKey) o;
        return Arrays.equals(params, cacheKey.params);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(params);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("params", params)
                .toString();
    }

}
