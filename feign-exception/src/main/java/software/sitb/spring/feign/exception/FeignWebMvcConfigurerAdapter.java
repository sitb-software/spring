package software.sitb.spring.feign.exception;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import java.util.List;

/**
 * @author Sean(sean.snow @ live.com) createAt 17-12-29.
 */
@Configuration
@ConditionalOnMissingBean(name = "exceptionResolver")
public class FeignWebMvcConfigurerAdapter extends WebMvcConfigurerAdapter {

    /**
     * 异常处理Bean
     *
     * @return HandlerExceptionResolver
     */
    @Bean
    public HandlerExceptionResolver exceptionResolver() {
        return new FeignWebExceptionHandler();
    }

    @Override
    public void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> exceptionResolvers) {
        exceptionResolvers.add(exceptionResolver());
    }

}
