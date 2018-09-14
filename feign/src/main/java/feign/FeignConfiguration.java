package feign;

import feign.codec.Decoder;
import feign.codec.Encoder;
import org.springframework.context.annotation.Bean;

public class FeignConfiguration {

    @Bean
    public Contract contract() {
        return new ContractImpl();
    }

    @Bean
    public Feign.Builder feignBuilder(
            Encoder encoder,
            Decoder decoder,
            Contract contract,
            Client client,
            Retryer retryer
    ) {
        return new FeignBuilder()
                .encoder(encoder)
                .decoder(decoder)
                .contract(contract)
                .client(client)
                .retryer(retryer);
    }

}
