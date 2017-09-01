package software.sitb.spring.cache.memcached;

import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.exception.MemcachedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.util.Assert;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

/**
 * Memcached实现 Spring Cache
 *
 * @author 田尘殇Sean sean.snow@live.com
 */
public class MemcachedCache implements Cache {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemcachedCache.class);

    private static final int TIMEOUT = 86400;

    private static final String PREFIX = "@@keys@@";

    private static final String SPLIT = "@_______________@";

    private MemcachedClient client;

    /**
     * 创建一个Memcached存储工具
     *
     * @param client CacheDefault 客户端
     */
    public MemcachedCache(MemcachedClient client, String name) {
        Assert.notNull(name, "Name must not be null");
        this.client = client;
        client.setName(name);
        try {
            if(null == client.get(PREFIX + name)){
                client.set(PREFIX+ name,Integer.MAX_VALUE,"");
            }
        } catch (TimeoutException | InterruptedException | MemcachedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return the cache name.
     */
    @Override
    public String getName() {
        return client.getName();
    }

    /**
     * Return the the underlying native cache provider.
     */
    @Override
    public Object getNativeCache() {
        return getClient();
    }

    @Override
    public ValueWrapper get(Object key) {
        Object obj = null;
        try {
            obj = getClient().get(getKey(key));
        } catch (TimeoutException | InterruptedException | MemcachedException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return toWrapper(obj);
    }

    /**
     * Return the value to which this cache maps the specified key,
     * generically specifying a type that return value will be cast to.
     * <p>Note: This variant of {@code get} does not allow for differentiating
     * between a cached {@code null} value and no cache entry found at all.
     * Use the standard {@link #get(Object)} variant for that purpose instead.
     *
     * @param key  the key whose associated value is to be returned
     * @param type the required type of the returned value (may be
     *             {@code null} to bypass a type check; in case of a {@code null}
     *             value found in the cache, the specified type is irrelevant)
     * @param <T>  返回结果类型
     * @return the value to which this cache maps the specified key
     * (which may be {@code null} itself), or also {@code null} if
     * the cache contains no mapping for this key
     * @throws IllegalStateException if a cache entry has been found
     *                               but failed to match the specified type
     * @see #get(Object)
     * @since 4.0
     */
    @Override
    public <T> T get(Object key, Class<T> type) {
        T value = null;
        try {
            value = getClient().get(getKey(key));
        } catch (TimeoutException | InterruptedException | MemcachedException e) {
            LOGGER.error(e.getMessage(), e);
        }
        if (value != null && type != null && !type.isInstance(value)) {
            throw new IllegalStateException("Cached value is not of required type [" + type.getName() + "]: " + value);
        }
        return value;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        try {
            T value = client.get(getKey(key));
            if(null == value){
                value = valueLoader.call();
                put(key, value);
            }
            return value;
        } catch (Throwable e) {
            throw new ValueRetrievalException(key,valueLoader,e);
        }
    }

    /**
     * Associate the specified value with the specified key in this cache.
     * <p>If the cache previously contained a mapping for this key, the old
     * value is replaced by the specified value.
     *
     * @param key   the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     */
    @Override
    public void put(Object key, Object value) {
        put(key, value, TIMEOUT);
    }

    /**
     * @param key     the key with which the specified value is to be associated
     * @param value   the value to be associated with the specified key
     * @param timeout timeout 单位秒
     */
    public void put(Object key, Object value, int timeout) {
        try {
            if (null == value)
                return;
            if (value instanceof Collection && ((Collection) value).size() == 0) {
                return;
            }
            if (value instanceof Object[] && ((Object[]) value).length == 0) {
                return;
            }
            getClient().set(getKey(key), timeout, value);
        } catch (TimeoutException | InterruptedException | MemcachedException e) {
            LOGGER.error(e.getMessage(), e);
        }

    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        try {
            Object existingValue = getClient().get(getKey(key));
            if (null == existingValue) {
                getClient().set((String) key, TIMEOUT, value);
                return null;
            } else
                return toWrapper(existingValue);
        } catch (TimeoutException | InterruptedException | MemcachedException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Evict the mapping for this key from this cache if it is present.
     *
     * @param key the key whose mapping is to be removed from the cache
     */
    @Override
    public void evict(Object key) {
        try {
            getClient().delete(getKey(key));
        } catch (TimeoutException | InterruptedException | MemcachedException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * Remove all mappings from the cache.
     */
    @Override
    public void clear() {
        try {
            String keyStr = getClient().get(PREFIX + this.getName());
            if(null == keyStr){
                return;
            }
            String[] keys = keyStr.split(SPLIT);
            for (String key : keys){
                getClient().delete(key);
            }
        } catch (TimeoutException | InterruptedException | MemcachedException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }


    private ValueWrapper toWrapper(Object value) {
        return (value == null ? null : new SimpleValueWrapper(value));
    }


    public MemcachedClient getClient() {
        return client;
    }

    public void setClient(MemcachedClient client) {
        this.client = client;
    }

    /**
     * 把对象转换为String类型的key，
     * 通过计算对象的MD5
     *
     * @param obj key
     * @return MD5 String
     */
    private String getKey(Object obj) throws InterruptedException, MemcachedException, TimeoutException {
        if (null == obj)
            return null;
        String key = this.getName() + "_" + obj.toString();
        getClient().append(PREFIX + this.getName(), key+SPLIT);
        return key;
    }
}
