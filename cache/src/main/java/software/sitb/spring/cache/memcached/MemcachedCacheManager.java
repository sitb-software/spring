package software.sitb.spring.cache.memcached;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author 田尘殇Sean sean.snow@live.com
 */
public class MemcachedCacheManager implements CacheManager {
    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>(16);

    private boolean dynamic = true;

    private MemcachedBuilder memcachedBuilder;

    public MemcachedCacheManager(MemcachedBuilder memcachedBuilder) {
        this(memcachedBuilder, null);
    }

    public MemcachedCacheManager(MemcachedBuilder memcachedBuilder, List<String> cacheNames) {
        this.memcachedBuilder = memcachedBuilder;
        setCacheNames(cacheNames);
    }

    /**
     * Return the cache associated with the given name.
     *
     * @param name the cache identifier (must not be {@code null})
     * @return the associated cache, or {@code null} if none found
     */
    @Override
    public Cache getCache(String name) {
        Cache cache = this.cacheMap.get(name);
        if (cache == null && this.dynamic) {
            synchronized (this.cacheMap) {
                cache = this.cacheMap.get(name);
                if (cache == null) {
                    cache = createCache(name);
                    this.cacheMap.put(name, cache);
                }
            }
        }
        return cache;
    }

    /**
     * Return a collection of the cache names known by this manager.
     *
     * @return the names of all caches known by the cache manager
     */
    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(this.cacheMap.keySet());
    }

    /**
     * Specify the set of cache names for this CacheManager's 'static' mode.
     * <p>The number of caches and their names will be fixed after a call to this method,
     * with no creation of further cache regions at runtime.
     * <p>Calling this with a {@code null} collection argument resets the
     * mode to 'dynamic', allowing for further creation of caches again.
     *
     * @param cacheNames 缓存名字集合
     */
    public void setCacheNames(Collection<String> cacheNames) {
        if (cacheNames != null) {
            for (String name : cacheNames) {
                this.cacheMap.put(name, createCache(name));
            }
            this.dynamic = false;
        } else {
            this.dynamic = true;
        }
    }

    /**
     * 创建一个缓存对象
     *
     * @param name 缓存名字
     * @return Cache
     */
    private Cache createCache(String name) {
        MemcachedCache cache = new MemcachedCache();
        cache.setName(name);
        cache.setClient(this.memcachedBuilder.builder());
        return cache;
    }
}
