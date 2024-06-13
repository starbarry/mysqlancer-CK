package sqlancer;

import sqlancer.common.query.Query;
import sqlancer.common.query.SQLQueryAdapter;

import java.util.List;

public interface AbstractAction_diff<G> {

    Query<?> getQuery(G globalState) throws Exception;
    List<SQLQueryAdapter> getQuery_diff(G globalState, boolean is_diff) throws Exception;
    /**
     * Specifies whether it makes sense to request a {@link Query}, when the previous call to {@link #getQuery(Object)}
     * returned a query that failed executing.
     *
     * @return whether retrying getting queries makes sense, if the first query failed executing.
     */
    default boolean canBeRetried() {
        return true;
    }

}
