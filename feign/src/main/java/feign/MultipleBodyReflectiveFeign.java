package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.Param.Expander;
import feign.Request.Options;
import feign.codec.Decoder;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.template.UriUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.Map.Entry;

import static feign.Util.checkArgument;
import static feign.Util.checkNotNull;

/**
 * 主要功能，支持多个body的位置信息
 * see {@link ParseHandlersByName}
 * see {@link BuildTemplateByResolvingArgs}
 * see {@link BuildEncodedTemplateFromArgs}
 */
public class MultipleBodyReflectiveFeign extends Feign {

    public static final String BODY_META = "X-Feign-Body-Meta";

    private final MultipleBodyReflectiveFeign.ParseHandlersByName targetToHandlersByName;
    private final InvocationHandlerFactory factory;
    private final QueryMapEncoder queryMapEncoder;

    MultipleBodyReflectiveFeign(MultipleBodyReflectiveFeign.ParseHandlersByName targetToHandlersByName,
                                InvocationHandlerFactory factory,
                                QueryMapEncoder queryMapEncoder
    ) {
        this.targetToHandlersByName = targetToHandlersByName;
        this.factory = factory;
        this.queryMapEncoder = queryMapEncoder;
    }

    /**
     * creates an api binding to the {@code target}. As this invokes reflection, care should be taken
     * to cache the result.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T newInstance(Target<T> target) {
        Map<String, MethodHandler> nameToHandler = targetToHandlersByName.apply(target);
        Map<Method, MethodHandler> methodToHandler = new LinkedHashMap<>();
        List<DefaultMethodHandler> defaultMethodHandlers = new LinkedList<>();

        for (Method method : target.type().getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            } else if (Util.isDefault(method)) {
                DefaultMethodHandler handler = new DefaultMethodHandler(method);
                defaultMethodHandlers.add(handler);
                methodToHandler.put(method, handler);
            } else {
                methodToHandler.put(method, nameToHandler.get(Feign.configKey(target.type(), method)));
            }
        }
        InvocationHandler handler = factory.create(target, methodToHandler);
        T proxy = (T) Proxy.newProxyInstance(target.type().getClassLoader(),
                new Class<?>[]{target.type()}, handler);

        for (DefaultMethodHandler defaultMethodHandler : defaultMethodHandlers) {
            defaultMethodHandler.bindTo(proxy);
        }
        return proxy;
    }

    static final class ParseHandlersByName {

        private final Contract contract;
        private final Options options;
        private final Encoder encoder;
        private final Decoder decoder;
        private final ErrorDecoder errorDecoder;
        private final QueryMapEncoder queryMapEncoder;
        private final SynchronousMethodHandler.Factory factory;

        ParseHandlersByName(
                Contract contract,
                Options options,
                Encoder encoder,
                Decoder decoder,
                QueryMapEncoder queryMapEncoder,
                ErrorDecoder errorDecoder,
                SynchronousMethodHandler.Factory factory) {
            this.contract = contract;
            this.options = options;
            this.factory = factory;
            this.errorDecoder = errorDecoder;
            this.queryMapEncoder = queryMapEncoder;
            this.encoder = checkNotNull(encoder, "encoder");
            this.decoder = checkNotNull(decoder, "decoder");
        }

        public Map<String, MethodHandler> apply(Target key) {
            List<MethodMetadata> metadata = contract.parseAndValidatateMetadata(key.type());
            Map<String, MethodHandler> result = new LinkedHashMap<String, MethodHandler>();
            for (MethodMetadata md : metadata) {
                MultipleBodyReflectiveFeign.BuildTemplateByResolvingArgs buildTemplate;
                if (!md.formParams().isEmpty() && md.template().bodyTemplate() == null) {
                    buildTemplate = new MultipleBodyReflectiveFeign.BuildFormEncodedTemplateFromArgs(md, encoder, queryMapEncoder);
                } else if (md.bodyIndex() != null) {
                    buildTemplate = new MultipleBodyReflectiveFeign.BuildEncodedTemplateFromArgs(md, encoder, queryMapEncoder);
                } else {
                    buildTemplate = new MultipleBodyReflectiveFeign.BuildTemplateByResolvingArgs(md, queryMapEncoder);
                }
                result.put(md.configKey(),
                        factory.create(key, md, buildTemplate, options, decoder, errorDecoder));
            }
            return result;
        }
    }

    private static class BuildTemplateByResolvingArgs implements RequestTemplate.Factory {

        private final QueryMapEncoder queryMapEncoder;
        protected final MethodMetadata metadata;
        private final Map<Integer, Expander> indexToExpander = new LinkedHashMap<Integer, Expander>();

        private BuildTemplateByResolvingArgs(MethodMetadata metadata, QueryMapEncoder queryMapEncoder) {
            this.metadata = metadata;
            this.queryMapEncoder = queryMapEncoder;
            if (metadata.indexToExpander() != null) {
                indexToExpander.putAll(metadata.indexToExpander());
                return;
            }
            if (metadata.indexToExpanderClass().isEmpty()) {
                return;
            }
            for (Entry<Integer, Class<? extends Expander>> indexToExpanderClass : metadata
                    .indexToExpanderClass().entrySet()) {
                try {
                    indexToExpander
                            .put(indexToExpanderClass.getKey(), indexToExpanderClass.getValue().newInstance());
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        @Override
        public RequestTemplate create(Object[] argv) {
            RequestTemplate mutable = RequestTemplate.from(metadata.template());
            if (metadata.urlIndex() != null) {
                int urlIndex = metadata.urlIndex();
                checkArgument(argv[urlIndex] != null, "URI parameter %s was null", urlIndex);
                mutable.target(String.valueOf(argv[urlIndex]));
            }
            Map<String, Object> varBuilder = new LinkedHashMap<String, Object>();
            for (Entry<Integer, Collection<String>> entry : metadata.indexToName().entrySet()) {
                int i = entry.getKey();
                Object value = argv[entry.getKey()];
                if (value != null) { // Null values are skipped.
                    if (indexToExpander.containsKey(i)) {
                        value = expandElements(indexToExpander.get(i), value);
                    }
                    for (String name : entry.getValue()) {
                        varBuilder.put(name, value);
                    }
                }
            }

            RequestTemplate template = resolve(argv, mutable, varBuilder);
            if (metadata.queryMapIndex() != null) {
                // add query map parameters after initial resolve so that they take
                // precedence over any predefined values
                Object value = argv[metadata.queryMapIndex()];
                Map<String, Object> queryMap = toQueryMap(value);
                template = addQueryMapQueryParameters(queryMap, template);
            }

            if (metadata.headerMapIndex() != null) {
                template = addHeaderMapHeaders((Map<String, Object>) argv[metadata.headerMapIndex()], template);
            }

            return template;
        }

        private Map<String, Object> toQueryMap(Object value) {
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
            try {
                return queryMapEncoder.encode(value);
            } catch (EncodeException e) {
                throw new IllegalStateException(e);
            }
        }

        private Object expandElements(Expander expander, Object value) {
            if (value instanceof Iterable) {
                return expandIterable(expander, (Iterable) value);
            }
            return expander.expand(value);
        }

        private List<String> expandIterable(Expander expander, Iterable value) {
            List<String> values = new ArrayList<>();
            for (Object element : value) {
                if (element != null) {
                    values.add(expander.expand(element));
                }
            }
            return values;
        }

        @SuppressWarnings("unchecked")
        private RequestTemplate addHeaderMapHeaders(Map<String, Object> headerMap,
                                                    RequestTemplate mutable) {
            for (Entry<String, Object> currEntry : headerMap.entrySet()) {
                Collection<String> values = new ArrayList<>();

                Object currValue = currEntry.getValue();
                if (currValue instanceof Iterable<?>) {
                    for (Object nextObject : ((Iterable<?>) currValue)) {
                        values.add(nextObject == null ? null : nextObject.toString());
                    }
                } else {
                    values.add(currValue == null ? null : currValue.toString());
                }

                mutable.header(currEntry.getKey(), values);
            }
            return mutable;
        }

        @SuppressWarnings("unchecked")
        private RequestTemplate addQueryMapQueryParameters(Map<String, Object> queryMap, RequestTemplate mutable) {
            for (Entry<String, Object> currEntry : queryMap.entrySet()) {
                Collection<String> values = new ArrayList<>();

                boolean encoded = metadata.queryMapEncoded();
                Object currValue = currEntry.getValue();
                if (currValue instanceof Iterable<?>) {
                    for (Object nextObject : ((Iterable<?>) currValue)) {
                        values.add(nextObject == null ? null
                                : encoded ? nextObject.toString()
                                : UriUtils.encode(nextObject.toString()));
                    }
                } else {
                    values.add(currValue == null ? null
                            : encoded ? currValue.toString() : UriUtils.encode(currValue.toString()));
                }

                mutable.query(encoded ? currEntry.getKey() : UriUtils.encode(currEntry.getKey()), values);
            }
            return mutable;
        }

        protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable,
                                          Map<String, Object> variables) {
            return mutable.resolve(variables);
        }
    }

    private static class BuildFormEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {

        private final Encoder encoder;

        private BuildFormEncodedTemplateFromArgs(MethodMetadata metadata, Encoder encoder,
                                                 QueryMapEncoder queryMapEncoder) {
            super(metadata, queryMapEncoder);
            this.encoder = encoder;
        }

        @Override
        protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable,
                                          Map<String, Object> variables) {
            Map<String, Object> formVariables = new LinkedHashMap<String, Object>();
            for (Entry<String, Object> entry : variables.entrySet()) {
                if (metadata.formParams().contains(entry.getKey())) {
                    formVariables.put(entry.getKey(), entry.getValue());
                }
            }
            try {
                encoder.encode(formVariables, Encoder.MAP_STRING_WILDCARD, mutable);
            } catch (EncodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new EncodeException(e.getMessage(), e);
            }
            return super.resolve(argv, mutable, variables);
        }
    }

    private static class BuildEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {

        private final Encoder encoder;

        private BuildEncodedTemplateFromArgs(MethodMetadata metadata, Encoder encoder,
                                             QueryMapEncoder queryMapEncoder) {
            super(metadata, queryMapEncoder);
            this.encoder = encoder;
        }

        @Override
        protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable,
                                          Map<String, Object> variables) {

            Object body = null;
            if (null != metadata.bodyIndex()) {
                body = argv[metadata.bodyIndex()];
            }
            // 把多个body放进map中
            Collection<String> bodyMeta = mutable.headers().get(BODY_META);
            if (null != bodyMeta && bodyMeta.size() > 0) {
                FeignRequestBody requestBody = new FeignRequestBody();

                bodyMeta.forEach(i -> {
                    Integer index = Integer.parseInt(i);
                    Object arg = argv[index];
                    requestBody.put(index, arg);
                });
                body = requestBody;
            }

            checkArgument(body != null, "Body parameter %s was null", metadata.bodyIndex());
            try {
                encoder.encode(body, metadata.bodyType(), mutable);
            } catch (EncodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new EncodeException(e.getMessage(), e);
            }
            return super.resolve(argv, mutable, variables);
        }
    }
}
