package io.vertx.guides.wiki.database;

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
import io.vertx.guides.wiki.DatabaseConstants;
import io.vertx.serviceproxy.ServiceBinder;

public class WikiDatabaseVerticle extends AbstractVerticle implements DatabaseConstants{
	

	  public static final String CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sqlqueries.resource.file";
	  public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";
	  
	  private static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseVerticle.class);



  
	  
	  
	  public void start(Promise<Void> promise) throws Exception {
		  
		  HashMap<SqlQuery, String> sqlQueries =  loadSqlQueries();
		 
		  
		  JDBCClient dbClient = JDBCClient.createShared(vertx, new JsonObject()
				    .put("url", config().getString(CONFIG_WIKIDB_JDBC_URL, DEFAULT_WIKIDB_JDBC_URL))
				    .put("driver_class", config().getString(CONFIG_WIKIDB_JDBC_DRIVER_CLASS, DEFAULT_WIKIDB_JDBC_DRIVER_CLASS))
				    .put("max_pool_size", config().getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, DEFAULT_JDBC_MAX_POOL_SIZE)));
		  
		  
		 WikiDatabaseService.create(dbClient, sqlQueries, ready -> {
			 if(ready.succeeded()) {
				 ServiceBinder binder = new ServiceBinder(vertx);
				 binder.setAddress(CONFIG_WIKIDB_QUEUE)
				 .register(WikiDatabaseService.class, ready.result());
				 promise.complete();
			 }else {
				 promise.fail(ready.cause());
			 }
		 });
	  }
	 
	  
	  
	  private HashMap<SqlQuery, String> loadSqlQueries() throws IOException {
		  
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
		  
		  HashMap<SqlQuery, String> sqlQueries = new HashMap<>();
		  sqlQueries.put(SqlQuery.CREATE_PAGES_TABLE, queriesProps.getProperty("create-pages-table"));
		  sqlQueries.put(SqlQuery.ALL_PAGES, queriesProps.getProperty("all-pages"));
		  sqlQueries.put(SqlQuery.GET_PAGE, queriesProps.getProperty("get-page"));
		  sqlQueries.put(SqlQuery.GET_PAGE_BY_ID, queriesProps.getProperty("get-pageById"));
		  sqlQueries.put(SqlQuery.CREATE_PAGE, queriesProps.getProperty("create-page"));
		  sqlQueries.put(SqlQuery.SAVE_PAGE, queriesProps.getProperty("save-page"));
		  sqlQueries.put(SqlQuery.DELETE_PAGE, queriesProps.getProperty("delete-page"));
		  sqlQueries.put(SqlQuery.ALL_PAGES_DATA, queriesProps.getProperty("all-pages-data"));
		  return sqlQueries;
		  
	  }
	  
	  
	  /*
	  
	  public void onMessage(Message<JsonObject> message) {
		  if(!message.headers().contains("action")) {
			  LOGGER.error("No action header specified for message with header {} and body {}",message.headers(),message.body().encodePrettily());
			  message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
			  return;
		  }
		  
		  String action = message.headers().get("action");
		  
		  
		  switch(action) {
		  	case "all-pages":
		  		fetchAllPages(message);
		  		break;
		  	case "get-page":
		  		fetchPage(message);
		  		break;
		  	case "create-page":
		  		createPage(message);
		  		break;
		  	case "save-page":
		  		savePage(message);
		  		break;
		  	case "delete-page":
		  		deletePage(message);
		  		break;
		  	default:
		  		message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);	
		  }
		  
		  
	  }
	  
	  private void fetchAllPages(Message<JsonObject> message) {
		  dbClient.query(sqlQueries.get(SqlQuery.ALL_PAGES), res -> {
			 if(res.succeeded()) {
				 List<String> pages = res.result()
						 .getResults()
						 .stream()
						 .map(json -> json.getString(0))
						 .sorted()
						 .collect(Collectors.toList());
				 message.reply(new JsonObject().put("pages", new JsonArray(pages)));
			 }else {
				 reportQueryError(message,res.cause());
			 }
		  });
	  }
	  
	  private void fetchPage(Message<JsonObject> message) {
		  String requestedPage = message.body().getString("page");
		  JsonArray params = new JsonArray().add(requestedPage);
		  
		  
		  dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_PAGE), params, fetch -> {
			  if(fetch.succeeded()) {
				  JsonObject response = new JsonObject();
				  ResultSet resultSet = fetch.result();
				  if(resultSet.getNumRows() == 0 ) {
					  response.put("found", false);
				  }else {
					  response.put("found", true);
					  JsonArray row = resultSet.getResults().get(0);
					  response.put("id", row.getInteger(0));
					  response.put("rawContent", row.getString(1));
				  }
				  message.reply(response);
			  } else {
				  reportQueryError(message,fetch.cause());
			  }
		  });
	  }
	  
	  private void createPage(Message<JsonObject> message) {
		  JsonObject request = message.body();
		  JsonArray data = new JsonArray()
				  .add(request.getString("title"))
				  .add(request.getString("markdown"));
		  
		  dbClient.updateWithParams(sqlQueries.get(SqlQuery.CREATE_PAGE), data, res -> {
			 if(res.succeeded()) {
				 message.reply("ok");
			 }else {
				 reportQueryError(message,res.cause());
			 }
		  });
	  }
	  
	  
	  private void savePage(Message<JsonObject> message) {
		  JsonObject request = message.body();
		  JsonArray data = new JsonArray()
				  .add(request.getString("markdown"))
				  .add(request.getString("id"));
		  
		  dbClient.updateWithParams(sqlQueries.get(SqlQuery.SAVE_PAGE), data, res -> {
			    if (res.succeeded()) {
			      message.reply("ok");
			    } else {
			      reportQueryError(message, res.cause());
			    }
			  });
		}
	  
	  
	  private void deletePage(Message<JsonObject> message) {
		  JsonArray data = new JsonArray().add(message.body().getString("id"));

		  dbClient.updateWithParams(sqlQueries.get(SqlQuery.DELETE_PAGE), data, res -> {
		    if (res.succeeded()) {
		      message.reply("ok");
		    } else {
		      reportQueryError(message, res.cause());
		    }
		  });
		}
	  
	  
	  private void reportQueryError(Message<JsonObject> message, Throwable cause) {
		  LOGGER.error("Database Query error", cause);
		  message.fail(ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
	  }
	  
	  */
	  
}
