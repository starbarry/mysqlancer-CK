package sqlancer.clickhouse;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import sqlancer.DBMSSpecificOptions;
import sqlancer.OracleFactory;
import sqlancer.clickhouse.ClickHouseOptions.ClickHouseOracleFactory;
import sqlancer.clickhouse.ClickHouseProvider.ClickHouseGlobalState;
import sqlancer.clickhouse.oracle.diff.ClickHouseDiff;
import sqlancer.clickhouse.oracle.norec.ClickHouseNoRECOracle;
import sqlancer.clickhouse.oracle.tlp.ClickHouseTLPAggregateOracle;
import sqlancer.clickhouse.oracle.tlp.ClickHouseTLPDistinctOracle;
import sqlancer.clickhouse.oracle.tlp.ClickHouseTLPGroupByOracle;
import sqlancer.clickhouse.oracle.tlp.ClickHouseTLPHavingOracle;
import sqlancer.clickhouse.oracle.tlp.ClickHouseTLPWhereOracle;
import sqlancer.common.oracle.TestOracle;

@Parameters(separators = "=", commandDescription = "ClickHouse (default port: " + ClickHouseOptions.DEFAULT_PORT
        + ", default host: " + ClickHouseOptions.DEFAULT_HOST + ")")
public class ClickHouseOptions implements DBMSSpecificOptions<ClickHouseOracleFactory> {
    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 8123;

    @Parameter(names = "--oracle")
    public List<ClickHouseOracleFactory> oracle = Arrays.asList(ClickHouseOracleFactory.Diff);

    @Parameter(names = { "--test-joins" }, description = "Allow the generation of JOIN clauses", arity = 1)
    public boolean testJoins = true;

    @Parameter(names = { "--analyzer" }, description = "Enable analyzer in ClickHouse", arity = 1)
    public boolean enableAnalyzer = true;

    //自定义的指定差分引擎选项
    @Parameter(names = "--ref-engine", description = "Specify the reference engine for differential testing.")
    private String refEngine = "Log"; //NOPMD

    public String getRef_engine() {
        return refEngine;
    }

    public enum ClickHouseOracleFactory implements OracleFactory<ClickHouseGlobalState> {
        TLPWhere {
            @Override
            public TestOracle<ClickHouseGlobalState> create(ClickHouseGlobalState globalState) throws SQLException {
                return new ClickHouseTLPWhereOracle(globalState);
            }
        },
        TLPDistinct {
            @Override
            public TestOracle<ClickHouseGlobalState> create(ClickHouseGlobalState globalState) throws SQLException {
                return new ClickHouseTLPDistinctOracle(globalState);
            }
        },
        TLPGroupBy {
            @Override
            public TestOracle<ClickHouseGlobalState> create(ClickHouseGlobalState globalState) throws SQLException {
                return new ClickHouseTLPGroupByOracle(globalState);
            }
        },
        TLPAggregate {
            @Override
            public TestOracle<ClickHouseGlobalState> create(ClickHouseGlobalState globalState) throws SQLException {
                return new ClickHouseTLPAggregateOracle(globalState);
            }
        },
        TLPHaving {
            @Override
            public TestOracle<ClickHouseGlobalState> create(ClickHouseGlobalState globalState) throws SQLException {
                return new ClickHouseTLPHavingOracle(globalState);
            }
        },
        NoREC {
            @Override
            public TestOracle<ClickHouseGlobalState> create(ClickHouseGlobalState globalState) throws SQLException {
                return new ClickHouseNoRECOracle(globalState);
            }
        },
        /*
         * 差分测试选项
         */
        Diff {
            @Override
            public TestOracle<ClickHouseGlobalState> create(ClickHouseGlobalState globalState) throws SQLException {
                return new ClickHouseDiff(globalState);
            }
        };
    }

    @Override
    public List<ClickHouseOracleFactory> getTestOracleFactory() {
        return oracle;
    }
}
