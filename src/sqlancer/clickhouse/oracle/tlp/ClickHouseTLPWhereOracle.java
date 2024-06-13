package sqlancer.clickhouse.oracle.tlp;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import sqlancer.ComparatorHelper;
import sqlancer.Randomly;
import sqlancer.clickhouse.ClickHouseErrors;
import sqlancer.clickhouse.ClickHouseProvider;
import sqlancer.clickhouse.ClickHouseVisitor;

public class ClickHouseTLPWhereOracle extends ClickHouseTLPBase {

    public ClickHouseTLPWhereOracle(ClickHouseProvider.ClickHouseGlobalState state) {
        super(state);
        ClickHouseErrors.addExpectedExpressionErrors(errors);
    }
    //WHERE的TLP预言机
    @Override
    public void check() throws SQLException {
        super.check();
        if (Randomly.getBooleanWithRatherLowProbability()) {
            select.setOrderByClauses(IntStream.range(0, 1 + Randomly.smallNumber())
                    .mapToObj(i -> gen.generateExpressionWithColumns(columns, 5)).collect(Collectors.toList()));
        }
        //原始查询的字符串形式和结果
        String originalQueryString = ClickHouseVisitor.asString(select);
        System.out.println(originalQueryString);
        List<String> resultSet = ComparatorHelper.getResultSetFirstColumnAsString(originalQueryString, errors, state);
        boolean orderBy = Randomly.getBooleanWithRatherLowProbability();
        if (orderBy) {
            select.setOrderByClauses(IntStream.range(0, 1 + Randomly.smallNumber())
                    .mapToObj(i -> gen.generateExpressionWithColumns(columns, 5)).collect(Collectors.toList()));
        }
        //分为三个部分
        select.setWhereClause(predicate);
        String firstQueryString = ClickHouseVisitor.asString(select);
        select.setWhereClause(negatedPredicate);
        String secondQueryString = ClickHouseVisitor.asString(select);
        select.setWhereClause(isNullPredicate);
        String thirdQueryString = ClickHouseVisitor.asString(select);
        List<String> combinedString = new ArrayList<>();
        //分区查询结果
        List<String> secondResultSet = ComparatorHelper.getCombinedResultSet(firstQueryString, secondQueryString,
                thirdQueryString, combinedString, !orderBy, state, errors);
        //比较是否相等
        ComparatorHelper.assumeResultSetsAreEqual(resultSet, secondResultSet, originalQueryString, combinedString,
                state);
    }
}
