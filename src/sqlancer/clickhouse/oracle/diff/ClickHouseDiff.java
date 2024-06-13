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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.IntStream.range;

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

    @Override
    public void check() throws SQLException {
        ClickHouseExpressionGenerator gen = new ClickHouseExpressionGenerator(state);
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
        //随机决定是否DISTINCT
        if (Randomly.getBoolean()) {
            select.setSelectType(ClickHouseSelect.SelectType.DISTINCT);
        }
        //随机增加聚合函数
        if (Randomly.getBoolean()){
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
        }else{
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
            try{
                Statement s = con.createStatement();
                String changedatabase = "USE " + databaseName;
                s.execute(changedatabase);
                state.getState().logStatement(changedatabase);
            } catch (SQLException e){
                System.out.println("USE不成功");
            }
            rs1 = q.executeAndGetLogged(state);
        } catch (Exception e) {
            throw new AssertionError(QueryString, e);
        }
        try {
            try{
                Statement s = con.createStatement();
                String changedatabase = "USE " + databaseName + "1";
                s.execute(changedatabase);
                state.getState().logStatement(changedatabase);
            } catch (SQLException e){
                System.out.println("USE1不成功");
            }
            rs2 = q.executeAndGetLogged(state);
        } catch (Exception e) {
            throw new AssertionError(QueryString, e);
        }

        if (rs1 != null){
            while (rs1.next()) {
                firstCount++;
            }
            rs1.close();
        }
        if (rs2 != null){
            while (rs2.next()) {
                secondCount++;
            }
            rs2.close();
        }
        //结果对比
        if (firstCount ==0 && secondCount == 0) {
            Main.nrEmptyRes.addAndGet(1);
            throw new IgnoreMeException();
        }else {
            if (firstCount != secondCount) {
                throw new AssertionError(
                        QueryString + ";\nIn database 1 -- " + firstCount + "\nIn database 1 -- " + secondCount);
            }else{
                Main.nrNoneEmptyRes.addAndGet(1);
            }
        }
    }
}
