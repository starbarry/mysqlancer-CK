package sqlancer.clickhouse;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.auto.service.AutoService;

import sqlancer.*;
import sqlancer.clickhouse.ClickHouseProvider.ClickHouseGlobalState;
import sqlancer.clickhouse.gen.ClickHouseCommon;
import sqlancer.clickhouse.gen.ClickHouseInsertGenerator;
import sqlancer.clickhouse.gen.ClickHouseTableGenerator;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.common.query.SQLQueryProvider;
import sqlancer.common.query.SQLQueryProvider_diff;

@AutoService(DatabaseProvider.class)
public class ClickHouseProvider extends SQLProviderAdapter<ClickHouseGlobalState, ClickHouseOptions> {

    public ClickHouseProvider() {
        super(ClickHouseGlobalState.class, ClickHouseOptions.class);
    }

    public enum Action_diff implements AbstractAction_diff<ClickHouseGlobalState> {
        INSERT(ClickHouseInsertGenerator::getQuery_diff);
        private final SQLQueryProvider_diff<ClickHouseGlobalState> sqlQueryProvider;
        Action_diff(SQLQueryProvider_diff<ClickHouseGlobalState> sqlQueryProvider) {
            this.sqlQueryProvider = sqlQueryProvider;
        }
        @Override
        public List<SQLQueryAdapter> getQuery_diff(ClickHouseGlobalState state) throws Exception {
            return sqlQueryProvider.getQuery_diff(state);
        }
    }

    public enum Action implements AbstractAction<ClickHouseGlobalState> {
        INSERT(ClickHouseInsertGenerator::getQuery);
        private final SQLQueryProvider<ClickHouseGlobalState> sqlQueryProvider;
        Action(SQLQueryProvider<ClickHouseGlobalState> sqlQueryProvider) {
            this.sqlQueryProvider = sqlQueryProvider;
        }
        @Override
        public SQLQueryAdapter getQuery(ClickHouseGlobalState state) throws Exception {
            return sqlQueryProvider.getQuery(state);
        }
    }

    private static int mapActions(ClickHouseGlobalState globalState, Action_diff a) {
        Randomly r = globalState.getRandomly();
        switch (a) {
        case INSERT:
            return r.getInteger(0, globalState.getOptions().getMaxNumberInserts());
        default:
            throw new AssertionError(a);
        }
    }
    private static int mapActions(ClickHouseGlobalState globalState, Action a) {
        Randomly r = globalState.getRandomly();
        switch (a) {
            case INSERT:
                return r.getInteger(0, globalState.getOptions().getMaxNumberInserts());
            default:
                throw new AssertionError(a);
        }
    }

    public static class ClickHouseGlobalState extends SQLGlobalState<ClickHouseOptions, ClickHouseSchema> {

        private ClickHouseOptions clickHouseOptions;

        public void setClickHouseOptions(ClickHouseOptions clickHouseOptions) {
            this.clickHouseOptions = clickHouseOptions;
        }

        public ClickHouseOptions getClickHouseOptions() {
            return this.clickHouseOptions;
        }

        public String getOracleName() {
            return String.join("_",
                    this.clickHouseOptions.oracle.stream().map(o -> o.toString()).collect(Collectors.toList()));
        }

        @Override
        public String getDatabaseName() {
            return super.getDatabaseName() + this.getOracleName();
        }

        @Override
        protected ClickHouseSchema readSchema() throws SQLException {
            return ClickHouseSchema.fromConnection(getConnection(), getDatabaseName());
        }

    }

    /**
     * 生成数据库函数，在一个数据库中生成一定数量的表，引擎随机，并填充数据
     */
    @Override
    public void generateDatabase(ClickHouseGlobalState globalState) throws Exception {
        for (int i = 0; i < Randomly.fromOptions(5); i++) {
            boolean success;
            do {
                //生成表名
                String tableName = ClickHouseCommon.createTableName(i);
                //生成建表的表达式
                List<SQLQueryAdapter> qtList = ClickHouseTableGenerator.createTableStatement(tableName, globalState);
                SQLQueryAdapter QA = qtList.get(0);
                success = globalState.executeStatement(QA);
                if (Objects.equals(globalState.getOracleName(), "Diff")) {
                    SQLQueryAdapter QA1 = qtList.get(1);
                    success = success && globalState.executeStatement(QA1);
                }

            } while (!success);
        }

        //填充数据，使用自定义类executor_diff实现
        // TODO: add more Actions to populate table
        if (Objects.equals(globalState.getOracleName(), "Diff")){
            StatementExecutor_diff<ClickHouseGlobalState, Action_diff> se = new StatementExecutor_diff<>(globalState, Action_diff.values(),
                    ClickHouseProvider::mapActions, (q) -> {
                if (globalState.getSchema().getDatabaseTables().isEmpty()) {
                    throw new IgnoreMeException();
                }
            });
            se.executeStatements();
        }else {
            StatementExecutor<ClickHouseGlobalState, Action> se = new StatementExecutor<>(globalState, Action.values(),
                    ClickHouseProvider::mapActions, (q) -> {
                if (globalState.getSchema().getDatabaseTables().isEmpty()) {
                    throw new IgnoreMeException();
                }
            });
            se.executeStatements();
        }


    }

    /**
     * CK的创建数据库函数
     */
    @Override
    public SQLConnection createDatabase(ClickHouseGlobalState globalState) throws SQLException {
        String host = globalState.getOptions().getHost();
        int port = globalState.getOptions().getPort();
        if (host == null) {
            host = ClickHouseOptions.DEFAULT_HOST;
        }
        if (port == MainOptions.NO_SET_PORT) {
            port = ClickHouseOptions.DEFAULT_PORT;
        }

        ClickHouseOptions clickHouseOptions = globalState.getDbmsSpecificOptions();
        globalState.setClickHouseOptions(clickHouseOptions);
        String url = String.format("jdbc:clickhouse://%s:%d/%s", host, port, "default");
        String databaseName = globalState.getDatabaseName();
        Connection con = DriverManager.getConnection(url, globalState.getOptions().getUserName(),
                globalState.getOptions().getPassword());
        String dropDatabaseCommand = "DROP DATABASE IF EXISTS " + databaseName;
        globalState.getState().logStatement(dropDatabaseCommand);
        String createDatabaseCommand = "CREATE DATABASE IF NOT EXISTS " + databaseName;
        globalState.getState().logStatement(createDatabaseCommand);
//        String useDatabaseCommand = "USE " + databaseName; // Noop. To reproduce easier.
//        globalState.getState().logStatement(useDatabaseCommand);
        //对照数据库名+1
        String databaseName1 = globalState.getDatabaseName()+"1";
        String dropDatabaseCommand1 = "DROP DATABASE IF EXISTS " + databaseName1;
        globalState.getState().logStatement(dropDatabaseCommand1);
        String createDatabaseCommand1 = "CREATE DATABASE IF NOT EXISTS " + databaseName1;
        globalState.getState().logStatement(createDatabaseCommand1);
        //String useDatabaseCommand1 = "USE " + databaseName1; // Noop. To reproduce easier.
        //globalState.getState().logStatement(useDatabaseCommand);
        try (Statement s = con.createStatement()) {
            s.execute(dropDatabaseCommand);
            s.execute(dropDatabaseCommand1);
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try (Statement s = con.createStatement()) {
            s.execute(createDatabaseCommand);
            s.execute(createDatabaseCommand1);
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        con.close();
        con = DriverManager.getConnection(
                String.format("jdbc:clickhouse://%s:%d/%s?socket_timeout=300000%s", host, port, databaseName,
                        clickHouseOptions.enableAnalyzer ? "&allow_experimental_analyzer=1" : ""),
                globalState.getOptions().getUserName(), globalState.getOptions().getPassword());
        return new SQLConnection(con);
    }

    @Override
    public String getDBMSName() {
        return "clickhouse";
    }
}
