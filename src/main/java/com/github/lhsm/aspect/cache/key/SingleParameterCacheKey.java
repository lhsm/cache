package com.github.lhsm.aspect.cache.key;

import com.google.common.base.MoreObjects;

import java.io.Serializable;
import java.util.Objects;

public final class SingleParameterCacheKey implements ICacheKey, Serializable {

    private static final long serialVersionUID = 1L;

    private final Object parameter;

    public SingleParameterCacheKey(Object parameter) {
        this.parameter = Objects.requireNonNull(parameter);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SingleParameterCacheKey that = (SingleParameterCacheKey) o;
        return Objects.equals(parameter, that.parameter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameter);
    }

    @Override
    public Object[] getParameters() {
        return new Object[]{parameter};
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("parameter", parameter)
                .toString();
    }

}
