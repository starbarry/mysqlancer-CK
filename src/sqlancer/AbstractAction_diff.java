package sqlancer;

import sqlancer.common.query.Query;
import sqlancer.common.query.SQLQueryAdapter;

import java.util.List;

public interface AbstractAction_diff<G> {

    List<SQLQueryAdapter> getQuery_diff(G globalState) throws Exception;
    /**
     * Specifies whether it makes sense to request a {@link Query}, when the previous call to {@link #getQuery_diff(Object)}
     * returned a list of query that failed executing.
     *
     * @return whether retrying getting queries makes sense, if the first query failed executing.
     */
    default boolean canBeRetried() {
        return true;
    }

}
