package sqlancer.tidb;

import java.io.FileWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sqlancer.DatabaseProvider;
import sqlancer.GlobalState;
import sqlancer.IgnoreMeException;
import sqlancer.Main.QueryManager;
import sqlancer.Main.StateLogger;
import sqlancer.MainOptions;
import sqlancer.Query;
import sqlancer.QueryAdapter;
import sqlancer.Randomly;
import sqlancer.StateToReproduce;
import sqlancer.StateToReproduce.MySQLStateToReproduce;
import sqlancer.mysql.MySQLSchema;

public class TiDBProvider implements DatabaseProvider {

	@FunctionalInterface
	public interface TiDBQueryProvider {

		Query getQuery(TiDBGlobalState globalState) throws SQLException;
	}

	public static enum Action implements AbstractAction<TiDBGlobalState> {
		INSERT((g) -> new QueryAdapter("INSERT INTO t0 VALUES (" + Randomly.getNonCachedInteger() + ")", Arrays.asList("Out of range value for column"))), //
		ANALYZE_TABLE((g) -> new QueryAdapter("ANALYZE TABLE t0"));

		private final TiDBQueryProvider queryProvider;

		private Action(TiDBQueryProvider queryProvider) {
			this.queryProvider = queryProvider;
		}

		public Query getQuery(TiDBGlobalState state) throws SQLException {
			return queryProvider.getQuery(state);
		}
	}

	public static class TiDBGlobalState extends GlobalState {

		private MySQLSchema schema;

		public void setSchema(MySQLSchema schema) {
			this.schema = schema;
		}

		public MySQLSchema getSchema() {
			return schema;
		}

	}
	
	public  interface AbstractAction<G> {
		public Query getQuery(G globalState) throws SQLException;
	}

	@FunctionalInterface
	public interface AfterQueryAction {
		public void notify(Query q) throws SQLException;
	}

	@FunctionalInterface
	public interface ActionMapper<T, A> {
		public int map(T globalState, A action);
	}

	public class StatementExecutor<G extends GlobalState, A extends AbstractAction<G>> {

		private final G globalState;
		private final A[] actions;
		private final ActionMapper<G, A> mapping;
		private final AfterQueryAction queryConsumer;

		StatementExecutor(G globalState, String databaseName, A[] actions,
				ActionMapper<G, A> mapping, AfterQueryAction queryConsumer) {
			this.globalState = globalState;
			this.actions = actions;
			this.mapping = mapping;
			this.queryConsumer = queryConsumer;
		}

		public void executeStatements() throws SQLException {
			Randomly r = globalState.getRandomly();
			int[] nrRemaining = new int[actions.length];
			List<A> availableActions = new ArrayList<>();
			int total = 0;
			for (int i = 0; i < actions.length; i++) {
				A action = actions[i];
				int nrPerformed = mapping.map(globalState, action);
				if (nrPerformed != 0) {
					availableActions.add(action);
				}
				nrRemaining[i] = nrPerformed;
				total += nrPerformed;
			}
			while (total != 0) {
				A nextAction = null;
				int selection = r.getInteger(0, total);
				int previousRange = 0;
				int i;
				for (i = 0; i < nrRemaining.length; i++) {
					if (previousRange <= selection && selection < previousRange + nrRemaining[i]) {
						nextAction = actions[i];
						break;
					} else {
						previousRange += nrRemaining[i];
					}
				}
				assert nextAction != null;
				assert nrRemaining[i] > 0;
				nrRemaining[i]--;
				Query query = null;
				try {
					boolean success;
					int nrTries = 0;
					do {
						query = nextAction.getQuery(globalState);
						if (globalState.getOptions().logEachSelect()) {
							globalState.getLogger().writeCurrent(query.getQueryString());
						}
						success = globalState.getManager().execute(query);
					} while (!success && nrTries++ < 1000);
				} catch (IgnoreMeException e) {

				}
				if (query != null && query.couldAffectSchema()) {
					queryConsumer.notify(query);
				}
				total--;
			}
		}
	}

	private static int mapActions(TiDBGlobalState globalState, Action a) {
		Randomly r = globalState.getRandomly();
		switch (a) {
		case ANALYZE_TABLE:
			return r.getInteger(0, 5);
		case INSERT:
			return r.getInteger(0, globalState.getOptions().getMaxNumberInserts());
		default:
			throw new AssertionError(a);
		}

	}

	@Override
	public void generateAndTestDatabase(String databaseName, Connection con, StateLogger logger, StateToReproduce state,
			QueryManager manager, MainOptions options) throws SQLException {
		Randomly r = new Randomly();
		TiDBGlobalState globalState = new TiDBGlobalState();
		globalState.setConnection(con);
		globalState.setSchema(MySQLSchema.fromConnection(con, databaseName));
		globalState.setRandomly(r);
		globalState.setMainOptions(options);
		globalState.setStateLogger(logger);
		globalState.setManager(manager);
		globalState.setState(state);
//		TiDBOptions TiDBOptions = new TiDBOptions();
//		JCommander.newBuilder().addObject(TiDBOptions).build().parse(options.getDbmsOptions().split(" "));
//		globalState.setTiDBOptions(TiDBOptions);
//

		Query qt = new QueryAdapter("CREATE TABLE t0(c0 INT UNIQUE)");
		manager.execute(qt);

		StatementExecutor<TiDBGlobalState, Action> se = new StatementExecutor<TiDBGlobalState, Action>(globalState, databaseName, Action.values(),
				TiDBProvider::mapActions, (q) -> {
					if (q.couldAffectSchema()) {
						globalState.setSchema(MySQLSchema.fromConnection(con, databaseName));
					}
					if (globalState.getSchema().getDatabaseTables().isEmpty()) {
						throw new IgnoreMeException();
					}
				});
		se.executeStatements();
		manager.incrementCreateDatabase();
//		TestOracle oracle = globalState.getTiDBOptions().oracle.create(globalState);
//		for (int i = 0; i < options.getNrQueries(); i++) {
//			try {
//				oracle.check();
//				manager.incrementSelectQueryCount();
//			} catch (IgnoreMeException e) {
//
//			}
//		}
//		try {
//			if (options.logEachSelect()) {
//				logger.getCurrentFileWriter().close();
//				logger.currentFileWriter = null;
//			}
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}

	}

	@Override
	public Connection createDatabase(String databaseName, StateToReproduce state) throws SQLException {
		String url = "jdbc:mysql://127.0.0.1:4000/";
		Connection con = DriverManager.getConnection(url, "root", "");
		state.statements.add(new QueryAdapter("USE test"));
		state.statements.add(new QueryAdapter("DROP DATABASE IF EXISTS " + databaseName + " CASCADE"));
		String createDatabaseCommand = "CREATE DATABASE " + databaseName;
		state.statements.add(new QueryAdapter(createDatabaseCommand));
		state.statements.add(new QueryAdapter("USE " + databaseName));
		try (Statement s = con.createStatement()) {
			s.execute("DROP DATABASE IF EXISTS " + databaseName);
		}
		try (Statement s = con.createStatement()) {
			s.execute(createDatabaseCommand);
		}
		con.close();
		con = DriverManager.getConnection("jdbc:mysql://127.0.0.1:4000/" + databaseName, "root", "");
		return con;
	}

	@Override
	public String getLogFileSubdirectoryName() {
		return "TiDB";
	}

	@Override
	public void printDatabaseSpecificState(FileWriter writer, StateToReproduce state) {
		// TODO Auto-generated method stub

	}

	@Override
	public StateToReproduce getStateToReproduce(String databaseName) {
		return new MySQLStateToReproduce(databaseName);
	}
}
