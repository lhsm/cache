package com.github.lhsm.aspect.cache.key;

import javax.cache.annotation.GeneratedCacheKey;


public interface ICacheKey extends GeneratedCacheKey {

    Object[] getParameters();

}
