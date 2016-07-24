# Spring Cache 自定义功能封装

## Memcached 实现的 Spring Cache （Memcached Spring Cache）
使用memcached实现Spring Cache 功能（Memcached implementation of Spring Cache）


### 使用方法 (How use)

    @Configuration
    public class ApplicationCacheConfiguration {
    
        @Bean
        public MemcachedCacheManager cacheManager() {
            MemcachedConfig config = new MemcachedConfig();
            config.setConnectString("127.0.0.1:11211,127.0.0.1:11211");
            config.setConnectionPoolSize(50);
            config.setConnectTimeout(0);
            config.setFailureMode(true);
            config.setWeights(3);
            MemcachedBuilder builder = new MemcachedBuilder(config);
            return new MemcachedCacheManager(builder);
        }
    
        @Bean
        public Cache cache() {
            return cacheManager().getCache("default");
        }
    
    }
    
    @Service
    @CacheConfig(cacheNames = "myCache")
    public class App{
    
        @Autowired
        private Cache cache;
    
        @Cacheable
        public void findOne(Long id){
            // ... some code
        }
        
        @CacheEvict(allEntries = true)
        public void update(){
            // ... some code
        }
        
        public void custom(){
            cache.put("user","I'm Sean");
            String name = cache.get("user", String.class);
            
            MemcachedCache memcachedCache = (MemcachedCache) cache;
            // set timeout
            memcachedCache.put("checkValue", "123456", 600);
        }
        
    }
    
    
    