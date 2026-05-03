package de.jpx3.common.connect;

import com.google.common.base.Preconditions;
import org.spongepowered.configurate.ConfigurationNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.Executor;

public final class DatabaseService {

  private static final String CONNECTION_URL_LAYOUT = "jdbc:%s://%s:%s/%s?user=%s&password=%s&autoReconnect=true";

  private final ConfigurationNode configuration;
  private final Executor executor;

  private Connection connection;
  private IQueryExecutor queryExecutor;
  private String database;

  private DatabaseService(ConfigurationNode configuration, Executor executor) {
    this.configuration = configuration;
    this.executor = executor;
  }

  private static final String CONFIG_KEY_SERVICE  = "jdbc-service";
  private static final String CONFIG_KEY_HOST     = "host";
  private static final String CONFIG_KEY_PORT     = "port";
  private static final String CONFIG_KEY_DATABASE = "database";
  private static final String CONFIG_KEY_USER     = "user";
  private static final String CONFIG_KEY_PASSWORD = "password";

  public void tryConnection() {
    if (!shouldConnect()) {
      return;
    }

    ConfigurationNode config = configuration.node("connection");

    String service  = config.node(CONFIG_KEY_SERVICE).getString();
    String host     = config.node(CONFIG_KEY_HOST).getString();
    int    port     = config.node(CONFIG_KEY_PORT).getInt();
    String database = config.node(CONFIG_KEY_DATABASE).getString();
    String user     = config.node(CONFIG_KEY_USER).getString();
    String password = config.node(CONFIG_KEY_PASSWORD).getString();

    Preconditions.checkNotNull(service);
    Preconditions.checkNotNull(host);
    Preconditions.checkNotNull(database);
    Preconditions.checkNotNull(user);
    Preconditions.checkNotNull(password);

    String connectionURL = parseConnectionUrlFrom(service, host, port, database, user, password);

    try {
      this.connection = tryConnection(connectionURL);
      this.queryExecutor = new AsyncQueryExecutor(executor, this.connection, database);
      this.database = database;
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public void closeConnection() {
    if (connection == null)
      return;

    try {
      connection.close();
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public boolean isConnected() {
    try {
      return connection != null && !connection.isClosed();
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private Connection tryConnection(String connectionURL) throws SQLException {
    return DriverManager.getConnection(connectionURL);
  }

  private String parseConnectionUrlFrom(String service, String host, int port, String database, String user, String password) {
    return String.format(CONNECTION_URL_LAYOUT, service, host, port, database, user, password);
  }

  public boolean shouldCreateTables() {
    return configuration.node("create-tables").getBoolean(true);
  }

  public boolean shouldConnect() {
    return configuration.node("enabled").getBoolean();
  }

  public IQueryExecutor getQueryExecutor() {
    return queryExecutor;
  }

  public void setQueryExecutor(IQueryExecutor queryExecutor) {
    Preconditions.checkNotNull(queryExecutor);
    this.queryExecutor = queryExecutor;
  }

  public String database() {
    return database;
  }

  public static DatabaseService createFrom(ConfigurationNode configuration, Executor executor) {
    Preconditions.checkNotNull(configuration);
    Preconditions.checkNotNull(executor);
    return new DatabaseService(configuration, executor);
  }
}