# Spring Cloud openfeign 客户端扩展

* 支持多个 RequestBody


## E

### feign client 配置

```java
 import feign.codec.Encoder;
 import feign.codec.ErrorDecoder;
 import feign.codec.ExceptionErrorDecoder;
 import feign.codec.PageableEncoder;
 import org.springframework.beans.factory.ObjectFactory;
 import org.springframework.beans.factory.annotation.Autowired;
 import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
 import org.springframework.cloud.netflix.feign.AnnotatedParameterProcessor;
 import org.springframework.cloud.netflix.feign.support.SpringEncoder;
 import org.springframework.context.annotation.Bean;
 import org.springframework.core.convert.ConversionService;
 
 import java.util.ArrayList;
 import java.util.List;
 
 /**
  * custom configuration
  */
 public class ApplicationFeignClientsConfiguration {
 
     @Autowired
     private ObjectFactory<HttpMessageConverters> messageConverters;
 
     @Autowired(required = false)
     private List<AnnotatedParameterProcessor> parameterProcessors = new ArrayList<>();
 
     /**
      * Pageable 支持
      *
      * @return PageableEncoder
      */
     @Bean
     public Encoder feignEncoder() {
         return new PageableEncoder(new SpringEncoder(messageConverters));
     }
 
     /**
      * 多个 RequestBody 支持
      */
     @Bean
     public Contract feignContract(ConversionService feignConversionService) {
         return new ApplicationContract(this.parameterProcessors, feignConversionService);
     }
 
     /**
      * 多个 RequestBody 支持
      */
     @Bean
     public AppFeignBuilder feignBuilder(Retryer retryer) {
         return new AppFeignBuilder()
                 .retryer(retryer);
     }
 
 }

```

### 服务端配置

```java
@Configuration
public class AppWebMvcConfigurerAdapter extends WebMvcConfigurerAdapter {
     /**
     　* 接收多个RequestBody请求
　　　*/
     @Bean
     public FeignRequestBodyArgumentResolver feignRequestBodyArgumentResolver() {
        return new FeignRequestBodyArgumentResolver(); 
     }
    
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(feignRequestBodyArgumentResolver());
    }
}
```