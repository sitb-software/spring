package feign;

import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;

import java.util.ArrayList;
import java.util.List;

import static feign.ExceptionPropagationPolicy.NONE;

/**
 * 主要作用为设置自定义的 {@link MultipleBodyReflectiveFeign}
 *
 * @author Sean(sean.snow @ live.com) createAt 18-1-11.
 */
public class FeignBuilder extends Feign.Builder {

    private final List<RequestInterceptor> requestInterceptors = new ArrayList<>();
    private Logger.Level logLevel = Logger.Level.NONE;
    private Contract contract = new Contract.Default();
    private Client client = new Client.Default(null, null);
    private Retryer retryer = new Retryer.Default();
    private Logger logger = new Logger.NoOpLogger();
    private Encoder encoder = new Encoder.Default();
    private Decoder decoder = new Decoder.Default();
    private QueryMapEncoder queryMapEncoder = new QueryMapEncoder.Default();
    private ErrorDecoder errorDecoder = new ErrorDecoder.Default();
    private Request.Options options = new Request.Options();
    private InvocationHandlerFactory invocationHandlerFactory =
            new InvocationHandlerFactory.Default();
    private boolean decode404;
    private boolean closeAfterDecode = true;
    private ExceptionPropagationPolicy propagationPolicy = NONE;


    public Feign.Builder logLevel(Logger.Level logLevel) {
        this.logLevel = logLevel;
        return this;
    }

    public Feign.Builder contract(Contract contract) {
        this.contract = contract;
        return this;
    }

    public Feign.Builder client(Client client) {
        this.client = client;
        return this;
    }

    public Feign.Builder retryer(Retryer retryer) {
        this.retryer = retryer;
        return this;
    }

    public Feign.Builder logger(Logger logger) {
        this.logger = logger;
        return this;
    }

    public Feign.Builder encoder(Encoder encoder) {
        this.encoder = encoder;
        return this;
    }

    public Feign.Builder decoder(Decoder decoder) {
        this.decoder = decoder;
        return this;
    }

    public Feign.Builder queryMapEncoder(QueryMapEncoder queryMapEncoder) {
        this.queryMapEncoder = queryMapEncoder;
        return this;
    }

    /**
     * Allows to map the response before passing it to the decoder.
     */
    public Feign.Builder mapAndDecode(ResponseMapper mapper, Decoder decoder) {
        this.decoder = new Feign.ResponseMappingDecoder(mapper, decoder);
        return this;
    }

    /**
     * This flag indicates that the {@link #decoder(Decoder) decoder} should process responses with
     * 404 status, specifically returning null or empty instead of throwing {@link FeignException}.
     * <p>
     * <p/>
     * All first-party (ex gson) decoders return well-known empty values defined by
     * {@link Util#emptyValueOf}. To customize further, wrap an existing {@link #decoder(Decoder)
     * decoder} or make your own.
     * <p>
     * <p/>
     * This flag only works with 404, as opposed to all or arbitrary status codes. This was an
     * explicit decision: 404 -> empty is safe, common and doesn't complicate redirection, retry or
     * fallback policy. If your server returns a different status for not-found, correct via a
     * custom {@link #client(Client) client}.
     *
     * @since 8.12
     */
    public Feign.Builder decode404() {
        this.decode404 = true;
        return this;
    }

    public Feign.Builder errorDecoder(ErrorDecoder errorDecoder) {
        this.errorDecoder = errorDecoder;
        return this;
    }

    public Feign.Builder options(Request.Options options) {
        this.options = options;
        return this;
    }

    /**
     * Adds a single request interceptor to the builder.
     */
    public Feign.Builder requestInterceptor(RequestInterceptor requestInterceptor) {
        this.requestInterceptors.add(requestInterceptor);
        return this;
    }

    /**
     * Sets the full set of request interceptors for the builder, overwriting any previous
     * interceptors.
     */
    public Feign.Builder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
        this.requestInterceptors.clear();
        for (RequestInterceptor requestInterceptor : requestInterceptors) {
            this.requestInterceptors.add(requestInterceptor);
        }
        return this;
    }

    /**
     * Allows you to override how reflective dispatch works inside of Feign.
     */
    public Feign.Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
        this.invocationHandlerFactory = invocationHandlerFactory;
        return this;
    }

    /**
     * This flag indicates that the response should not be automatically closed upon completion of
     * decoding the message. This should be set if you plan on processing the response into a
     * lazy-evaluated construct, such as a {@link java.util.Iterator}.
     *
     * </p>
     * Feign standard decoders do not have built in support for this flag. If you are using this
     * flag, you MUST also use a custom Decoder, and be sure to close all resources appropriately
     * somewhere in the Decoder (you can use {@link Util#ensureClosed} for convenience).
     *
     * @since 9.6
     */
    public Feign.Builder doNotCloseAfterDecode() {
        this.closeAfterDecode = false;
        return this;
    }

    public Feign.Builder exceptionPropagationPolicy(ExceptionPropagationPolicy propagationPolicy) {
        this.propagationPolicy = propagationPolicy;
        return this;
    }

    public <T> T target(Class<T> apiType, String url) {
        return target(new Target.HardCodedTarget<T>(apiType, url));
    }

    public <T> T target(Target<T> target) {
        return build().newInstance(target);
    }

    public Feign build() {
        SynchronousMethodHandler.Factory synchronousMethodHandlerFactory =
                new SynchronousMethodHandler.Factory(client, retryer, requestInterceptors, logger,
                        logLevel, decode404, closeAfterDecode, propagationPolicy);
        MultipleBodyReflectiveFeign.ParseHandlersByName handlersByName = new MultipleBodyReflectiveFeign.ParseHandlersByName(
                contract,
                options,
                encoder,
                decoder,
                queryMapEncoder,
                errorDecoder,
                synchronousMethodHandlerFactory
        );
        return new MultipleBodyReflectiveFeign(handlersByName, invocationHandlerFactory, queryMapEncoder);
    }
}
