package io.vertx;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

public class WikiDatabaseVerticle extends AbstractVerticle{
	
	  public static final String CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url";
	  public static final String CONFIG_WIKIDB_JDBC_DRIVER_CLASS = "wikidb.jdbc.driver_class";
	  public static final String CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max_pool_size";
	  public static final String CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sqlqueries.resource.file";

	  public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";
	  
	  private static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseVerticle.class);

	  private enum SqlQuery {
		  CREATE_PAGES_TABLE,
		  ALL_PAGES,
		  GET_PAGE,
		  CREATE_PAGE,
		  SAVE_PAGE,
		  DELETE_PAGE
	  }
	  
	  private final HashMap<SqlQuery, String> sqlQueries = new HashMap<>();
	  
	  private JDBCClient dbClient;
	  
	  
	  private void loadSqlQueries() throws IOException {
		  
		  String queriesFile = config().getString(CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE);
		  InputStream queriesInputStream;
		  if(queriesFile != null ) {
			  queriesInputStream = new FileInputStream(queriesFile);
		  } else {
			  queriesInputStream = getClass().getResourceAsStream("/db-queries.properties");
		  }
		  
		  Properties queriesProps = new Properties();
		  queriesProps.load(queriesInputStream);
		  queriesInputStream.close();
		  
		  
		  sqlQueries.put(SqlQuery.CREATE_PAGES_TABLE, queriesProps.getProperty("create-pages-table"));
		  sqlQueries.put(SqlQuery.ALL_PAGES, queriesProps.getProperty("all-pages"));
		  sqlQueries.put(SqlQuery.GET_PAGE, queriesProps.getProperty("get-page"));
		  sqlQueries.put(SqlQuery.CREATE_PAGE, queriesProps.getProperty("create-page"));
		  sqlQueries.put(SqlQuery.SAVE_PAGE, queriesProps.getProperty("save-page"));
		  sqlQueries.put(SqlQuery.DELETE_PAGE, queriesProps.getProperty("delete-page"));
		  
	  }
	  
	  public void start(Promise<Void> promise) throws Exception {
		  loadSqlQueries();
		  
		  dbClient = JDBCClient.createShared(vertx, new JsonObject()
				    .put("url", config().getString(CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:file:db/wiki"))
				    .put("driver_class", config().getString(CONFIG_WIKIDB_JDBC_DRIVER_CLASS, "org.hsqldb.jdbcDriver"))
				    .put("max_pool_size", config().getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 30)));
		  
		  
		  dbClient.getConnection(ar -> {
			 if(ar.failed()) {
				 LOGGER.error("Could not open a database connection", ar.cause());
				 promise.fail(ar.cause());
			 } else {
				 SQLConnection connection = ar.result();
				 connection.execute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE), create -> {
					 connection.close();
					 if(create.failed()) {
						 LOGGER.error("Database preparation error", create.cause());
						 promise.fail(create.cause());
					 }else {
						 vertx.eventBus().consumer(config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue"), this::onMessage);
						 promise.complete();
					 }
				 });
			 }
		  });
	  }
	  
}
