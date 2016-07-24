package software.sitb.spring.cache.memcached;

/**
 * @author 田尘殇Sean sean.snow@live.com
 */
public class MemcachedConfig {
    /**
     * 设置连接池大小，即客户端个
     */
    private Integer connectionPoolSize;

    /**
     * 连接超时
     */
    private Integer connectTimeout;

    /**
     * 权重
     */
    private Integer weights;

    /**
     * 宕机报警
     */
    private Boolean failureMode;

    /**
     * 127.0.0.1:11211 192.168.1.2:11211
     */
    private String connectString;

    public String getConnectString() {
        return connectString;
    }

    public void setConnectString(String connectString) {
        this.connectString = connectString;
    }

    public Integer getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public void setConnectionPoolSize(Integer connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
    }

    public Boolean getFailureMode() {
        return failureMode;
    }

    public void setFailureMode(Boolean failureMode) {
        this.failureMode = failureMode;
    }

    public Integer getWeights() {
        return weights;
    }

    public void setWeights(Integer weights) {
        this.weights = weights;
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
}
