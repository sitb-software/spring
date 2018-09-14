package feign.response;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * @author Sean(sean.snow @ live.com) createAt 17-12-29.
 */
@Configuration
public class FeignWebMvcConfiguration implements WebMvcConfigurer {

    /**
     * 异常处理Bean
     *
     * @return HandlerExceptionResolver
     */
    @Bean
    public HandlerExceptionResolver feignExceptionHandler() {
        return new FeignWebExceptionHandler();
    }

    @Override
    public void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {
        exceptionResolvers.add(feignExceptionHandler());
    }

}
