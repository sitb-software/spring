package feign;

import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * 主要作用为设置自定义的 {@link MultipleBodyReflectiveFeign}
 *
 * @author Sean(sean.snow @ live.com) createAt 18-1-11.
 */
public class FeignBuilder extends Feign.Builder {

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

    public FeignBuilder logLevel(Logger.Level logLevel) {
        this.logLevel = logLevel;
        return this;
    }

    public FeignBuilder contract(Contract contract) {
        this.contract = contract;
        return this;
    }

    public FeignBuilder client(Client client) {
        this.client = client;
        return this;
    }

    public FeignBuilder retryer(Retryer retryer) {
        this.retryer = retryer;
        return this;
    }

    public FeignBuilder logger(Logger logger) {
        this.logger = logger;
        return this;
    }

    public FeignBuilder encoder(Encoder encoder) {
        this.encoder = encoder;
        return this;
    }

    public FeignBuilder decoder(Decoder decoder) {
        this.decoder = decoder;
        return this;
    }

    /**
     * Allows to map the response before passing it to the decoder.
     */
    public FeignBuilder mapAndDecode(ResponseMapper mapper, Decoder decoder) {
        this.decoder = new Feign.ResponseMappingDecoder(mapper, decoder);
        return this;
    }

    public FeignBuilder decode404() {
        this.decode404 = true;
        return this;
    }

    public FeignBuilder errorDecoder(ErrorDecoder errorDecoder) {
        this.errorDecoder = errorDecoder;
        return this;
    }

    public FeignBuilder options(Request.Options options) {
        this.options = options;
        return this;
    }

    /**
     * Adds a single request interceptor to the builder.
     */
    public FeignBuilder requestInterceptor(RequestInterceptor requestInterceptor) {
        this.requestInterceptors.add(requestInterceptor);
        return this;
    }

    /**
     * Sets the full set of request interceptors for the builder, overwriting any previous
     * interceptors.
     */
    public FeignBuilder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
        this.requestInterceptors.clear();
        for (RequestInterceptor requestInterceptor : requestInterceptors) {
            this.requestInterceptors.add(requestInterceptor);
        }
        return this;
    }

    /**
     * Allows you to override how reflective dispatch works inside of Feign.
     */
    public FeignBuilder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
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
        MultipleBodyReflectiveFeign.ParseHandlersByName handlersByName = new MultipleBodyReflectiveFeign.ParseHandlersByName(
                contract,
                options,
                encoder,
                decoder,
                errorDecoder,
                synchronousMethodHandlerFactory
        );
        return new MultipleBodyReflectiveFeign(handlersByName, invocationHandlerFactory);
    }
}
