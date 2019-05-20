package feign.request;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sean(sean.snow @ live.com) createAt 18-1-17.
 */
@Configuration
public class FeignRequestBodyArgumentResolverConfiguration implements CommandLineRunner {

    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    public FeignRequestBodyArgumentResolver feignRequestBodyArgumentResolver() {
        return new FeignRequestBodyArgumentResolver();
    }

    @Override
    public void run(String... args) throws Exception {
        RequestMappingHandlerAdapter requestMappingHandlerAdapter = applicationContext.getBean(RequestMappingHandlerAdapter.class);
        List<HandlerMethodArgumentResolver> defaultArgumentResolvers = requestMappingHandlerAdapter.getArgumentResolvers();
        List<HandlerMethodArgumentResolver> argumentResolvers = new ArrayList<>();
        if (null == defaultArgumentResolvers) {
            argumentResolvers.add(feignRequestBodyArgumentResolver());
        } else {
            argumentResolvers.addAll(defaultArgumentResolvers);
            for (int i = 0; i < defaultArgumentResolvers.size(); i++) {
                if (defaultArgumentResolvers.get(i) instanceof RequestResponseBodyMethodProcessor) {
                    argumentResolvers.add(i, feignRequestBodyArgumentResolver());
                    break;
                }
            }
        }
        requestMappingHandlerAdapter.setArgumentResolvers(argumentResolvers);
    }

}
