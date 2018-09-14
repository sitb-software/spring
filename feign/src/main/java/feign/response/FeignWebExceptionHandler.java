package feign.response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * 把异常信息写入流中
 *
 * @author Sean(sean.snow @ live.com) createAt 17-12-29.
 */
public class FeignWebExceptionHandler extends AbstractHandlerExceptionResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeignWebExceptionHandler.class);

    @Override
    protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        LOGGER.error("微服务发生异常[{}]", ex.getMessage(), ex);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        try {
            ObjectOutputStream outputStream = new ObjectOutputStream(response.getOutputStream());
            outputStream.writeObject(ex);
            outputStream.flush();
        } catch (IOException e) {
            LOGGER.error("写出异常失败", e);
        }
        return new ModelAndView();
    }

}
