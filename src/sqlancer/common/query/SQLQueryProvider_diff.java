package sqlancer.common.query;

import java.util.List;

@FunctionalInterface
public interface SQLQueryProvider_diff<S> {
    abstract List<SQLQueryAdapter> getQuery_diff(S globalState) throws Exception;
}
