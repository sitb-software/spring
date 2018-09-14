package feign.codec;

import feign.FeignException;
import feign.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * @author Sean(sean.snow @ live.com) createAt 17-12-29.
 */
public class ExceptionErrorDecoder implements ErrorDecoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionErrorDecoder.class);

    @Override
    public Exception decode(String methodKey, Response response) {
        Exception exception = handleApplicationException(response);
        if (null != exception) {
            LOGGER.error("微服务客户端收到异常信息[{}],[{}]->{}",
                    response.request().url(),
                    response.status(),
                    exception.getMessage());
            return exception;
        }
        return FeignException.errorStatus(methodKey, response);
    }

    private Exception handleApplicationException(Response response) {
        try {
            if (null != response.body()) {
                ObjectInputStream inputStream = new ObjectInputStream(response.body().asInputStream());
                return (Exception) inputStream.readObject();
            }
        } catch (IOException ignored) {
        } catch (ClassNotFoundException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return null;
    }
}
