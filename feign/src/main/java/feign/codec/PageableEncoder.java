package feign.codec;

import feign.RequestTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This encoder adds support for pageable, which will be applied to the query parameters.
 *
 * @author Sean(sean.snow @ live.com) createAt 17-12-26.
 */
public class PageableEncoder implements Encoder {
    private final Encoder delegate;

    public PageableEncoder(Encoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException {

        if (object instanceof Pageable) {
            Pageable pageable = (Pageable) object;
            template.query("page", pageable.getPageNumber() + "");
            template.query("size", pageable.getPageSize() + "");

            if (null != pageable.getSort()) {
                Collection<String> existingSorts = template.queries().get("sort");
                List<String> sortQueries = existingSorts != null ? new ArrayList<>(existingSorts) : new ArrayList<>();
                for (Sort.Order order : pageable.getSort()) {
                    sortQueries.add(order.getProperty() + "," + order.getDirection());
                }
                template.query("sort", sortQueries);
            }
        } else {
            delegate.encode(object, bodyType, template);
        }
    }
}
