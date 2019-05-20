package feign;

import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.codec.ExceptionErrorDecoder;
import org.springframework.context.annotation.Bean;

public class MultipleBodyConfiguration {

    @Bean
    public Contract contract() {
        return new MultipleBodyContract();
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new ExceptionErrorDecoder();
    }

    @Bean
    public Feign.Builder feignBuilder(
            Encoder encoder,
            Decoder decoder,
            Contract contract,
            Client client,
            Retryer retryer,
            ErrorDecoder errorDecoder
    ) {
        return new FeignBuilder()
                .encoder(encoder)
                .decoder(decoder)
                .contract(contract)
                .client(client)
                .retryer(retryer)
                .errorDecoder(errorDecoder)
                ;
    }

}
