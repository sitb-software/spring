package feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor;

import java.io.IOException;
import java.util.Collections;

/**
 * @author Sean(sean.snow @ live.com) createAt 18-1-11.
 */
public class FeignRequestBodyArgumentResolver extends RequestResponseBodyMethodProcessor {

    private static MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter();

    @Autowired
    private NativeWebRequest request;

    private volatile FeignRequestBody body;

    public FeignRequestBodyArgumentResolver() {
        super(Collections.singletonList(mappingJackson2HttpMessageConverter));
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return isSupport();
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return isSupport() || super.supportsReturnType(returnType);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        String[] values = webRequest.getHeaderValues(AppReflectiveFeign.BODY_META);
        for (String value : values) {
            Integer index = Integer.parseInt(value);
            if (index == parameter.getParameterIndex()) {
                Object arg = getParameter(parameter, webRequest);
                String name = Conventions.getVariableNameForParameter(parameter);

                WebDataBinder binder = binderFactory.createBinder(webRequest, arg, name);
                if (arg != null) {
                    validateIfApplicable(binder, parameter);
                    if (binder.getBindingResult().hasErrors() && isBindExceptionRequired(binder, parameter)) {
                        throw new MethodArgumentNotValidException(parameter, binder.getBindingResult());
                    }
                }
                mavContainer.addAttribute(BindingResult.MODEL_KEY_PREFIX + name, binder.getBindingResult());

                return adaptArgumentIfNecessary(arg, parameter);
            }
        }

        return super.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
    }

    private Object getParameter(MethodParameter parameter, NativeWebRequest webRequest) throws IOException, HttpMediaTypeNotSupportedException {
        if (null == body) {
            parameter = parameter.nestedIfOptional();
            body = (FeignRequestBody) readWithMessageConverters(webRequest, parameter, FeignRequestBody.class);
        }

        Object arg = body.get(parameter.getParameterIndex());
        ObjectMapper mapper = mappingJackson2HttpMessageConverter.getObjectMapper();
        return mapper.convertValue(arg, parameter.getParameterType());
    }

    private boolean isSupport() {
        String[] values = request.getHeaderValues(AppReflectiveFeign.BODY_META);
        return null != values && values.length > 0;
    }

}
