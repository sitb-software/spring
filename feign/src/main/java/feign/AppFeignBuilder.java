package feign;

import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sean(sean.snow @ live.com) createAt 18-1-11.
 */
public class AppFeignBuilder {

    private final List<RequestInterceptor> requestInterceptors = new ArrayList<RequestInterceptor>();
    private Logger.Level logLevel = Logger.Level.NONE;
    private Contract contract = new Contract.Default();
    private Client client = new Client.Default(null, null);
    private Retryer retryer = new Retryer.Default();
    private Logger logger = new Logger.NoOpLogger();
    private Encoder encoder = new Encoder.Default();
    private Decoder decoder = new Decoder.Default();
    private ErrorDecoder errorDecoder = new ErrorDecoder.Default();
    private Request.Options options = new Request.Options();
    private InvocationHandlerFactory invocationHandlerFactory = new InvocationHandlerFactory.Default();
    private boolean decode404;

    public AppFeignBuilder logLevel(Logger.Level logLevel) {
        this.logLevel = logLevel;
        return this;
    }

    public AppFeignBuilder contract(Contract contract) {
        this.contract = contract;
        return this;
    }

    public AppFeignBuilder client(Client client) {
        this.client = client;
        return this;
    }

    public AppFeignBuilder retryer(Retryer retryer) {
        this.retryer = retryer;
        return this;
    }

    public AppFeignBuilder logger(Logger logger) {
        this.logger = logger;
        return this;
    }

    public AppFeignBuilder encoder(Encoder encoder) {
        this.encoder = encoder;
        return this;
    }

    public AppFeignBuilder decoder(Decoder decoder) {
        this.decoder = decoder;
        return this;
    }

    /**
     * Allows to map the response before passing it to the decoder.
     */
    public AppFeignBuilder mapAndDecode(ResponseMapper mapper, Decoder decoder) {
        this.decoder = new Feign.ResponseMappingDecoder(mapper, decoder);
        return this;
    }

    /**
     * This flag indicates that the {@link #decoder(Decoder) decoder} should process responses with
     * 404 status, specifically returning null or empty instead of throwing {@link FeignException}.
     * <p>
     * <p/> All first-party (ex gson) decoders return well-known empty values defined by {@link
     * Util#emptyValueOf}. To customize further, wrap an existing {@link #decoder(Decoder) decoder}
     * or make your own.
     * <p>
     * <p/> This flag only works with 404, as opposed to all or arbitrary status codes. This was an
     * explicit decision: 404 -> empty is safe, common and doesn't complicate redirection, retry or
     * fallback policy. If your server returns a different status for not-found, correct via a
     * custom {@link #client(Client) client}.
     *
     * @since 8.12
     */
    public AppFeignBuilder decode404() {
        this.decode404 = true;
        return this;
    }

    public AppFeignBuilder errorDecoder(ErrorDecoder errorDecoder) {
        this.errorDecoder = errorDecoder;
        return this;
    }

    public AppFeignBuilder options(Request.Options options) {
        this.options = options;
        return this;
    }

    /**
     * Adds a single request interceptor to the builder.
     */
    public AppFeignBuilder requestInterceptor(RequestInterceptor requestInterceptor) {
        this.requestInterceptors.add(requestInterceptor);
        return this;
    }

    /**
     * Sets the full set of request interceptors for the builder, overwriting any previous
     * interceptors.
     */
    public AppFeignBuilder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
        this.requestInterceptors.clear();
        for (RequestInterceptor requestInterceptor : requestInterceptors) {
            this.requestInterceptors.add(requestInterceptor);
        }
        return this;
    }

    /**
     * Allows you to override how reflective dispatch works inside of Feign.
     */
    public AppFeignBuilder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
        this.invocationHandlerFactory = invocationHandlerFactory;
        return this;
    }

    public <T> T target(Class<T> apiType, String name, String url) {
        return target(new Target.HardCodedTarget<T>(apiType, name, url));
    }

    public <T> T target(Target<T> target) {
        return build().newInstance(target);
    }

    public Feign build() {
        SynchronousMethodHandler.Factory synchronousMethodHandlerFactory = new SynchronousMethodHandler.Factory(client, retryer, requestInterceptors, logger, logLevel, decode404);
        AppReflectiveFeign.ParseHandlersByName handlersByName = new AppReflectiveFeign.ParseHandlersByName(
                contract,
                options,
                encoder,
                decoder,
                errorDecoder,
                synchronousMethodHandlerFactory
        );
        return new AppReflectiveFeign(handlersByName, invocationHandlerFactory);
    }
}
