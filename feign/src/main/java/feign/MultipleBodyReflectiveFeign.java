package feign;

import feign.InvocationHandlerFactory.MethodHandler;
import feign.Param.Expander;
import feign.Request.Options;
import feign.codec.Decoder;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.Map.Entry;

import static feign.Util.*;

/**
 * 主要功能，记录多个body的位置信息
 * see {@link ParseHandlersByName}
 * see {@link BuildTemplateByResolvingArgs}
 */
public class MultipleBodyReflectiveFeign extends Feign {

    public static final String BODY_META = "bodyMeta";

    private final ParseHandlersByName targetToHandlersByName;
    private final InvocationHandlerFactory factory;

    MultipleBodyReflectiveFeign(ParseHandlersByName targetToHandlersByName, InvocationHandlerFactory factory) {
        this.targetToHandlersByName = targetToHandlersByName;
        this.factory = factory;
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
        List<DefaultMethodHandler> defaultMethodHandlers = new LinkedList<DefaultMethodHandler>();

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
        T proxy = (T) Proxy.newProxyInstance(target.type().getClassLoader(), new Class<?>[]{target.type()}, handler);

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
        private final SynchronousMethodHandler.Factory factory;

        ParseHandlersByName(Contract contract, Options options, Encoder encoder, Decoder decoder,
                            ErrorDecoder errorDecoder, SynchronousMethodHandler.Factory factory) {
            this.contract = contract;
            this.options = options;
            this.factory = factory;
            this.errorDecoder = errorDecoder;
            this.encoder = checkNotNull(encoder, "encoder");
            this.decoder = checkNotNull(decoder, "decoder");
        }

        public Map<String, MethodHandler> apply(Target key) {
            List<MethodMetadata> metadata = contract.parseAndValidatateMetadata(key.type());
            Map<String, MethodHandler> result = new LinkedHashMap<>();
            for (MethodMetadata md : metadata) {
                BuildTemplateByResolvingArgs buildTemplate;
                if (!md.formParams().isEmpty() && md.template().bodyTemplate() == null) {
                    buildTemplate = new BuildFormEncodedTemplateFromArgs(md, encoder);
                } else if (md.bodyIndex() != null || (null != md.template().headers().get(BODY_META))) {
                    buildTemplate = new BuildEncodedTemplateFromArgs(md, encoder);
                } else {
                    buildTemplate = new BuildTemplateByResolvingArgs(md);
                }
                result.put(md.configKey(),
                        factory.create(key, md, buildTemplate, options, decoder, errorDecoder));
            }
            return result;
        }
    }

    private static class BuildTemplateByResolvingArgs implements RequestTemplate.Factory {

        protected final MethodMetadata metadata;
        private final Map<Integer, Expander> indexToExpander = new LinkedHashMap<Integer, Expander>();

        private BuildTemplateByResolvingArgs(MethodMetadata metadata) {
            this.metadata = metadata;
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
            RequestTemplate mutable = new RequestTemplate(metadata.template());
            if (metadata.urlIndex() != null) {
                int urlIndex = metadata.urlIndex();
                checkArgument(argv[urlIndex] != null, "URI parameter %s was null", urlIndex);
                mutable.insert(0, String.valueOf(argv[urlIndex]));
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
                template = addQueryMapQueryParameters(argv, template);
            }

            if (metadata.headerMapIndex() != null) {
                template = addHeaderMapHeaders(argv, template);
            }

            return template;
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
        private RequestTemplate addHeaderMapHeaders(Object[] argv, RequestTemplate mutable) {
            Map<Object, Object> headerMap = (Map<Object, Object>) argv[metadata.headerMapIndex()];
            for (Entry<Object, Object> currEntry : headerMap.entrySet()) {
                checkState(currEntry.getKey().getClass() == String.class, "HeaderMap key must be a String: %s", currEntry.getKey());

                Collection<String> values = new ArrayList<>();

                Object currValue = currEntry.getValue();
                if (currValue instanceof Iterable<?>) {
                    for (Object nextObject : ((Iterable<?>) currValue)) {
                        values.add(nextObject == null ? null : nextObject.toString());
                    }
                } else {
                    values.add(currValue == null ? null : currValue.toString());
                }

                mutable.header((String) currEntry.getKey(), values);
            }
            return mutable;
        }

        @SuppressWarnings("unchecked")
        private RequestTemplate addQueryMapQueryParameters(Object[] argv, RequestTemplate mutable) {
            Map<Object, Object> queryMap = (Map<Object, Object>) argv[metadata.queryMapIndex()];
            for (Entry<Object, Object> currEntry : queryMap.entrySet()) {
                checkState(currEntry.getKey().getClass() == String.class, "QueryMap key must be a String: %s", currEntry.getKey());

                Collection<String> values = new ArrayList<String>();

                boolean encoded = metadata.queryMapEncoded();
                Object currValue = currEntry.getValue();
                if (currValue instanceof Iterable<?>) {
                    Iterator<?> iter = ((Iterable<?>) currValue).iterator();
                    while (iter.hasNext()) {
                        Object nextObject = iter.next();
                        values.add(nextObject == null ? null : encoded ? nextObject.toString() : RequestTemplate.urlEncode(nextObject.toString()));
                    }
                } else {
                    values.add(currValue == null ? null : encoded ? currValue.toString() : RequestTemplate.urlEncode(currValue.toString()));
                }

                mutable.query(true, encoded ? (String) currEntry.getKey() : RequestTemplate.urlEncode(currEntry.getKey()), values);
            }
            return mutable;
        }

        protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable,
                                          Map<String, Object> variables) {
            // Resolving which variable names are already encoded using their indices
            Map<String, Boolean> variableToEncoded = new LinkedHashMap<String, Boolean>();
            for (Entry<Integer, Boolean> entry : metadata.indexToEncoded().entrySet()) {
                Collection<String> names = metadata.indexToName().get(entry.getKey());
                for (String name : names) {
                    variableToEncoded.put(name, entry.getValue());
                }
            }
            return mutable.resolve(variables, variableToEncoded);
        }
    }

    private static class BuildFormEncodedTemplateFromArgs extends BuildTemplateByResolvingArgs {

        private final Encoder encoder;

        private BuildFormEncodedTemplateFromArgs(MethodMetadata metadata, Encoder encoder) {
            super(metadata);
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

        private BuildEncodedTemplateFromArgs(MethodMetadata metadata, Encoder encoder) {
            super(metadata);
            this.encoder = encoder;
        }

        @Override
        protected RequestTemplate resolve(Object[] argv, RequestTemplate mutable,
                                          Map<String, Object> variables) {

            Object body = null;
            if (null != metadata.bodyIndex()) {
                body = argv[metadata.bodyIndex()];
            }
            Collection<String> bodyMeta = mutable.headers().get(BODY_META);
            if (null != bodyMeta && bodyMeta.size() > 0) {
                FeignRequestBody requestBody = new FeignRequestBody();
                List<String> newBodyMeta = new ArrayList<>();

                bodyMeta.forEach(i -> {
                    Integer index = Integer.parseInt(i);
                    Object arg = argv[index];
//                    if (arg instanceof Pageable) {
//                        requestBody.put(-index, arg);
//                    } else {
                    requestBody.put(index, arg);
                    newBodyMeta.add(i);
//                    }
                });
//                if (requestBody.size() == 1 && requestBody.values().toArray()[0] instanceof Pageable) {
//                    mutable.header(BODY_META);
//                    body = requestBody.values().toArray()[0];
//                } else {
                mutable.header(BODY_META, newBodyMeta);
                body = requestBody;
//                }
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