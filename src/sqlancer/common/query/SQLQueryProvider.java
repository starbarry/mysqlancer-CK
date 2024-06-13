package sqlancer.common.query;

import java.util.List;

@FunctionalInterface
public interface SQLQueryProvider<S> {
    SQLQueryAdapter getQuery(S globalState) throws Exception;
}
