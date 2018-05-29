package software.sitb.spring.feign.exception;

import feign.*;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.codec.PageableEncoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.cloud.netflix.feign.AnnotatedParameterProcessor;
import org.springframework.cloud.netflix.feign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import software.sitb.spring.feign.exception.codec.ExceptionErrorDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sean(sean.snow @ live.com) createAt 17-12-26.
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
    public AppFeignBuilder feignBuilder(
            Encoder encoder,
            Decoder decoder,
            Contract contract,
            Client client,
            Retryer retryer,
            ErrorDecoder errorDecoder
    ) {
        return new AppFeignBuilder()
                .encoder(encoder)
                .decoder(decoder)
                .contract(contract)
                .client(client)
                .errorDecoder(errorDecoder)
                .retryer(retryer);
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new ExceptionErrorDecoder();
    }

}
