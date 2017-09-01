package software.sitb.spring.cache.memcached;

import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.MemcachedClientBuilder;
import net.rubyeye.xmemcached.XMemcachedClientBuilder;
import net.rubyeye.xmemcached.command.BinaryCommandFactory;
import net.rubyeye.xmemcached.utils.AddrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * @author 田尘殇Sean sean.snow@live.com
 */
public class MemcachedBuilder {

    private static final Logger log = LoggerFactory.getLogger(MemcachedBuilder.class);

    private MemcachedClientBuilder builder;

    private MemcachedConfig config;

    private List<InetSocketAddress> addresses;

    public MemcachedBuilder(MemcachedConfig config) {
        this.config = config;
        init();
    }

    /**
     * 初始化Builder工具
     */
    private void init() {

        if (builder != null)
            return;

        addresses = AddrUtil.getAddresses(config.getConnectString());
        int[] weights = new int[addresses.size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = config.getWeights();
        }
        builder = new XMemcachedClientBuilder(addresses, weights);
        builder.setConnectionPoolSize(config.getConnectionPoolSize());
        builder.setConnectTimeout(config.getConnectTimeout());
        builder.setFailureMode(config.getFailureMode());
        builder.setCommandFactory(new BinaryCommandFactory());
    }


    /**
     * 创建一个Memcached 客户端
     *
     * @return MemcachedClient 如果发生异常返回Null
     */
    public MemcachedClient builder() {
        try {
            return builder.build();
        } catch (IOException e) {
            log.error("创建MemcachedClient失败", e);
        }
        return null;
    }

    /**
     * 关闭 MemcachedClient
     *
     * @param client MemcachedClient
     */
    public void close(MemcachedClient client) {
        if (null != client) {
            try {
                client.shutdown();
            } catch (IOException e) {
                log.error("客户端关闭失败", e);
            }
        }
    }

    public MemcachedConfig getConfig() {
        return config;
    }

    public void setConfig(MemcachedConfig config) {
        this.config = config;
    }

    public List<InetSocketAddress> getAddresses() {
        return addresses;
    }
}
