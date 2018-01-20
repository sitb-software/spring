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

import javax.servlet.ServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author Sean(sean.snow @ live.com) createAt 18-1-11.
 */
public class FeignRequestBodyArgumentResolver extends RequestResponseBodyMethodProcessor {

    private static MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter();

    private static final String BODY_KEY = "@@FEIGN_REQUEST_BODY@@";

    @Autowired
    private NativeWebRequest request;


    public FeignRequestBodyArgumentResolver() {
        super(Collections.singletonList(mappingJackson2HttpMessageConverter));
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return isSupport(parameter);
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
        Object bodyArg = webRequest.getAttribute(BODY_KEY, NativeWebRequest.SCOPE_REQUEST);
        FeignRequestBody body;
        if (null == bodyArg) {
            parameter = parameter.nestedIfOptional();
            body = (FeignRequestBody) readWithMessageConverters(webRequest, parameter, FeignRequestBody.class);
            webRequest.setAttribute(BODY_KEY, body, NativeWebRequest.SCOPE_REQUEST);
        } else {
            body = (FeignRequestBody) bodyArg;
        }

        Object arg = body.get(parameter.getParameterIndex());
        ObjectMapper mapper = mappingJackson2HttpMessageConverter.getObjectMapper();
        return mapper.convertValue(arg, parameter.getParameterType());
    }

    private boolean isSupport(MethodParameter parameter) {
        if (ServletRequest.class.isAssignableFrom(parameter.getParameterType())) {
            return false;
        }
        String[] values = request.getHeaderValues(AppReflectiveFeign.BODY_META);
        if (null == values || values.length == 0) {
            return false;
        }
        return Arrays.stream(values)
                .mapToInt(Integer::parseInt)
                .filter(item -> item == parameter.getParameterIndex())
                .findFirst()
                .isPresent();
    }

}
