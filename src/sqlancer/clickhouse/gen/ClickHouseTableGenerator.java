package sqlancer.clickhouse.gen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import sqlancer.Randomly;
import sqlancer.clickhouse.ClickHouseErrors;
import sqlancer.clickhouse.ClickHouseProvider;
import sqlancer.clickhouse.ClickHouseSchema;
import sqlancer.clickhouse.ClickHouseToStringVisitor;
import sqlancer.clickhouse.ast.ClickHouseExpression;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;

public class ClickHouseTableGenerator {

    private enum ClickHouseEngine {
        // TinyLog, StripeLog,
        Log, Memory, MergeTree, AggregatingMergeTree
    }

    //表达式字符串
    private final StringBuilder sb = new StringBuilder();

    private final String tableName;
    private int columnId;
    private final List<String> columnNames = new ArrayList<>();
    private final List<ClickHouseSchema.ClickHouseColumn> columns = new ArrayList<>();
    private final ClickHouseProvider.ClickHouseGlobalState globalState;

    static private boolean isdiff = true;

    //自定义对照建表表达式
    private final StringBuilder sb1 = new StringBuilder();

    public ClickHouseTableGenerator(String tableName, ClickHouseProvider.ClickHouseGlobalState globalState) {
        this.tableName = tableName;
        this.globalState = globalState;
    }

    /*
     * 生成建表表达式
     */
    public static List<SQLQueryAdapter> createTableStatement(String tableName,
            ClickHouseProvider.ClickHouseGlobalState globalState) {
        ClickHouseTableGenerator chTableGenerator = new ClickHouseTableGenerator(tableName, globalState);

        //核心创建函数
        chTableGenerator.start();
        ExpectedErrors errors = new ExpectedErrors();
        ExpectedErrors errors1 = new ExpectedErrors();
        ClickHouseErrors.addExpectedExpressionErrors(errors);
        ClickHouseErrors.addExpectedExpressionErrors(errors1);
        //返回一个SQLQA列表
        List<SQLQueryAdapter> qtList = new ArrayList<>();
        SQLQueryAdapter QA = new SQLQueryAdapter(chTableGenerator.sb.toString(), errors, true);
        qtList.add(QA);
        if (Objects.equals(globalState.getOracleName(), "Diff")) {
            isdiff = true;
            SQLQueryAdapter QA1 = new SQLQueryAdapter(chTableGenerator.sb1.toString(), errors1, true);
            qtList.add(QA1);
        }
        return qtList;
    }
    /*
     * 核心建表表达式函数
     */
    public void start() {
        //根据选项选择随机引擎，或建立MergeTree与Log对照
        ClickHouseEngine engine, engine1;
        engine1 = ClickHouseEngine.valueOf(globalState.getClickHouseOptions().getRef_engine());
        if (!isdiff)  engine = Randomly.fromOptions(ClickHouseEngine.values());
        else engine = ClickHouseEngine.MergeTree;

        //AST生成树
        ClickHouseExpressionGenerator gen = new ClickHouseExpressionGenerator(globalState).allowAggregates(false);
        sb.append("CREATE ");
        sb.append("TABLE ");

        sb1.append("CREATE ");
        sb1.append("TABLE ");

        if (Randomly.getBoolean()) {
            sb.append("IF NOT EXISTS ");
            sb1.append("IF NOT EXISTS ");
        }

        sb.append(this.globalState.getDatabaseName());
        sb.append(".");
        sb.append(this.tableName);
        sb.append(" (");


        sb1.append(this.globalState.getDatabaseName());
        //数据库名后加1标志
        sb1.append("1.");
        sb1.append(this.tableName);
        sb1.append(" (");

        int nrColumns = 1 + Randomly.smallNumber();
        for (int i = 0; i < nrColumns; i++) {
            columns.add(ClickHouseSchema.ClickHouseColumn.createDummy(ClickHouseCommon.createColumnName(i), null));
        }
        for (int i = 0; i < nrColumns; i++) {
            if (i != 0) {
                sb.append(", ");
                sb1.append(", ");
            }
            String columnName = ClickHouseCommon.createColumnName(columnId);
            ClickHouseColumnBuilder columnBuilder = new ClickHouseColumnBuilder();
            String columnstring = columnBuilder.createColumn(columnName, globalState, columns);
            //sb.append(columnBuilder.createColumn(columnName, globalState, columns));
            sb.append(columnstring);
            sb1.append(columnstring);
            columnNames.add(columnName);
            columnId++;
        }
        if (Randomly.getBooleanWithSmallProbability()) {
            for (int i = 0; i < Randomly.smallNumber(); i++) {
                addColumnsConstraint(gen);
            }
        }
        sb.append(") ENGINE = ");
        sb.append(engine);
        sb.append("(");
        sb.append(") ");

        sb1.append(") ENGINE = ");
        sb1.append(engine1);
        sb1.append("(");
        sb1.append(") ");

        boolean if_orderby = Randomly.getBoolean();
        boolean if_partitionby = Randomly.getBoolean();
        boolean if_sampleby = Randomly.getBoolean();
        ClickHouseExpression expr_orderby = gen.generateExpressionWithColumns(
                columns.stream().map(c -> c.asColumnReference(null)).collect(Collectors.toList()), 3);
        ClickHouseExpression expr_partitionby = gen.generateExpressionWithColumns(
                columns.stream().map(c -> c.asColumnReference(null)).collect(Collectors.toList()), 3);
        ClickHouseExpression expr_sampleby = gen.generateExpressionWithColumns(
                columns.stream().map(c -> c.asColumnReference(null)).collect(Collectors.toList()), 3);

        if (engine == ClickHouseEngine.MergeTree || engine == ClickHouseEngine.AggregatingMergeTree) {
            if (if_orderby) {
                sb.append(" ORDER BY ");
                sb.append(ClickHouseToStringVisitor.asString(expr_orderby));
            } else {
                sb.append(" ORDER BY tuple() ");
            }

            if (if_partitionby) {
                sb.append(" PARTITION BY ");
                sb.append(ClickHouseToStringVisitor.asString(expr_partitionby));
            }
            if (if_sampleby) {
                sb.append(" SAMPLE BY ");
                sb.append(ClickHouseToStringVisitor.asString(expr_sampleby));
            }
            // Suppress index sanity checks https://github.com/sqlancer/sqlancer/issues/788
            sb.append(" SETTINGS allow_suspicious_indices=1");
            // TODO: PRIMARY KEY
        }
        if (engine1 == ClickHouseEngine.MergeTree || engine1 == ClickHouseEngine.AggregatingMergeTree) {
            if (if_orderby) {
                sb1.append(" ORDER BY ");
                sb1.append(ClickHouseToStringVisitor.asString(expr_orderby));
            } else {
                sb1.append(" ORDER BY tuple() ");
            }

            if (if_partitionby) {
                sb1.append(" PARTITION BY ");
                sb1.append(ClickHouseToStringVisitor.asString(expr_partitionby));
            }
            if (if_sampleby) {
                sb1.append(" SAMPLE BY ");
                sb1.append(ClickHouseToStringVisitor.asString(expr_sampleby));
            }
            // Suppress index sanity checks https://github.com/sqlancer/sqlancer/issues/788
            sb1.append(" SETTINGS allow_suspicious_indices=1");
            // TODO: PRIMARY KEY
        }
    }

    private void addColumnsConstraint(ClickHouseExpressionGenerator gen) {
        for (int i = 0; i < Randomly.smallNumber() + 1; i++) {
            sb.append(",");
            sb.append(" CONSTRAINT ");
            sb.append(ClickHouseCommon.createConstraintName(i));
            sb.append(" CHECK ");

            sb1.append(",");
            sb1.append(" CONSTRAINT ");
            sb1.append(ClickHouseCommon.createConstraintName(i));
            sb1.append(" CHECK ");

            ClickHouseExpression expr = gen.generateExpressionWithColumns(
                    columns.stream().map(c -> c.asColumnReference(null)).collect(Collectors.toList()), 2);
            sb.append(ClickHouseToStringVisitor.asString(expr));
            sb1.append(ClickHouseToStringVisitor.asString(expr));
        }
    }
}
