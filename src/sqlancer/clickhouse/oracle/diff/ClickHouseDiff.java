package sqlancer.clickhouse.oracle.diff;

import sqlancer.*;
import sqlancer.clickhouse.*;
import sqlancer.clickhouse.ast.*;
import sqlancer.clickhouse.gen.ClickHouseExpressionGenerator;
import sqlancer.common.oracle.TestOracle;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLancerResultSet;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ClickHouseDiff implements TestOracle<ClickHouseProvider.ClickHouseGlobalState> {

    protected final ClickHouseProvider.ClickHouseGlobalState state;
    protected final ExpectedErrors errors = new ExpectedErrors();
    protected final Main.StateLogger logger;
    protected final MainOptions options;
    protected final SQLConnection con;
    protected String QueryString;
    private final ClickHouseSchema schema;

    public ClickHouseDiff(ClickHouseProvider.ClickHouseGlobalState globalState) {
        this.state = globalState;
        this.con = state.getConnection();
        this.logger = state.getLogger();
        this.options = state.getOptions();
        this.schema = globalState.getSchema();
        ClickHouseErrors.addExpectedExpressionErrors(errors);

    }

    //等待增加udf, groupby,
    @Override
    public void check() throws SQLException {
        ClickHouseExpressionGenerator gen = new ClickHouseExpressionGenerator(state);
        //等待增加udf, groupby,
        //获取所有表集合tables，并随机挑选一张表table
        List<ClickHouseSchema.ClickHouseTable> tables = schema.getRandomTableNonEmptyTables().getTables();
        ClickHouseTableReference table = new ClickHouseTableReference(
                tables.get((int) Randomly.getNotCachedInteger(0, tables.size() - 1)), "left");
        List<ClickHouseColumnReference> columns = table.getColumnReferences();
        //设置join表达式
        List<ClickHouseExpression.ClickHouseJoin> joinStatements = new ArrayList<>();
        if (state.getClickHouseOptions().testJoins && Randomly.getBoolean()) {
            joinStatements = gen.getRandomJoinClauses(table, tables);
            columns.addAll(joinStatements.stream().flatMap(j -> j.getRightTable().getColumnReferences().stream())
                    .collect(Collectors.toList()));
        }
        gen.addColumns(columns);
        //随机生成WHERE表达式，并转换为UInt8
//        ClickHouseExpression randomWhereCondition = gen.generateExpressionWithColumns(columns, 5);
        ClickHouseExpression randomWhereCondition = new ClickHouseUnaryFunctionOperation(gen.generateExpressionWithColumns(columns, 5),
                ClickHouseUnaryFunctionOperation.ClickHouseUnaryFunctionOperator.TOUINT8);
        //创建select对象
        ClickHouseSelect select = new ClickHouseSelect();
        //生成选择对象列
        List<ClickHouseColumnReference> filteredColumns = Randomly.extractNrRandomColumns(columns,
                (int) Randomly.getNotCachedInteger(1, columns.size()));
        //设置select
        select.setFetchColumns(
                filteredColumns.stream().map(c -> (ClickHouseExpression) c).collect(Collectors.toList()));
        select.setFromClause(table);
        select.setWhereClause(randomWhereCondition);
        select.setJoinClauses(joinStatements);

        boolean ifDITINCT = Randomly.getBoolean();
        boolean ifaggregate = Randomly.getBoolean();

        //随机决定是否DISTINCT
        if (ifDITINCT) {
            select.setSelectType(ClickHouseSelect.SelectType.DISTINCT);
        }
        //随机增加聚合函数
        if (ifaggregate) {
            ClickHouseAggregate.ClickHouseAggregateFunction windowFunction = Randomly.fromOptions(
                    ClickHouseAggregate.ClickHouseAggregateFunction.MIN,
                    ClickHouseAggregate.ClickHouseAggregateFunction.MAX,
                    ClickHouseAggregate.ClickHouseAggregateFunction.SUM);

            ClickHouseAggregate aggregate = new ClickHouseAggregate(gen.generateExpressionWithColumns(columns, 6),
                    windowFunction);
            select.setFetchColumns(Arrays.asList(aggregate));
            QueryString = ClickHouseToStringVisitor.asString(select);
            QueryString += " SETTINGS aggregate_functions_null_for_empty = 1";
            select.setFetchColumns(Arrays.asList(new ClickHouseAliasOperation(aggregate, "aggr")));
        } else {
            //转换成字符串
            QueryString = ClickHouseToStringVisitor.asString(select);
        }
        //对比查询结果集
        int firstCount = 0, secondCount = 0;
        String databaseName = state.getDatabaseName();
        SQLQueryAdapter q = new SQLQueryAdapter(QueryString, errors);
        SQLancerResultSet rs1;
        SQLancerResultSet rs2;

        try {
            Statement s = con.createStatement();
            String changedatabase = "USE " + databaseName;
            s.execute(changedatabase);
            state.getState().logStatement(changedatabase);
            rs1 = q.executeAndGetLogged(state);
        } catch (Exception e) {
            throw new AssertionError(QueryString, e);
        }
        try {

            Statement s = con.createStatement();
            String changedatabase = "USE " + databaseName + "1";
            s.execute(changedatabase);
            state.getState().logStatement(changedatabase);
            rs2 = q.executeAndGetLogged(state);
        } catch (Exception e) {
            throw new AssertionError(QueryString, e);
        }

        if (rs1 == null || !rs1.next()) {
            firstCount = 0;
            if (rs1 != null) rs1.close();
        } else {
            while (rs1 != null && rs1.next())
                firstCount++;
        }
        if (rs2 == null || !rs2.next()) {
            secondCount = 0;
            if (rs2 != null) rs2.close();
        } else {
            while (rs2 != null && rs2.next())
                secondCount++;
        }
        //空结果统计后抛出忽略异常
        if (firstCount + secondCount == 0) {
            Main.nrEmptyRes.addAndGet(1);
            throw new IgnoreMeException();
        }
        //有结果则统计后进入比较
        Main.nrNoneEmptyRes.addAndGet(1);
        //对聚合函数比较内容，否则只比较个数
        if (ifaggregate) {
            if (!Objects.equals(rs1.getLong(0), rs2.getLong(0)))
                throw new AssertionError(
                        QueryString + ";\nIn database 1 -- " + rs1.getString(0) + "\nIn database 2 -- " + rs2.getString(0));
        } else {
            if (firstCount != secondCount) {
                throw new AssertionError(
                        QueryString + ";\nIn database 1: --count " + firstCount + "\nIn database 1 --count " + secondCount);
            }
        }
        rs1.close();
        rs2.close();
    }
}
