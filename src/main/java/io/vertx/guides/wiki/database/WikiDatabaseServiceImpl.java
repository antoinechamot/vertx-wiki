package io.vertx.guides.wiki.database;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
//import io.vertx.ext.sql.ResultSet;
//import io.vertx.ext.sql.SQLConnection;
import io.vertx.reactivex.ext.sql.SQLConnection;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.SingleHelper;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.reactivex.ext.sql.SQLClientHelper;




//import  io.vertx.guides.wiki.database.SqlQuery;

public class WikiDatabaseServiceImpl implements WikiDatabaseService{
	
	public static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseServiceImpl.class);
	
	private final HashMap<SqlQuery, String> sqlQueries;
	private final JDBCClient dbClient;
	


	public WikiDatabaseServiceImpl(HashMap<SqlQuery, String> sqlQueries, io.vertx.ext.jdbc.JDBCClient dbClient, Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
		super();
		this.sqlQueries = sqlQueries;
		this.dbClient = new JDBCClient(dbClient);
		
		
		SQLClientHelper.usingConnectionSingle(this.dbClient, conn ->
		conn.rxExecute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE))
		.andThen(Single.just(this)))
		.subscribe(SingleHelper.toObserver(readyHandler));
		
		/*dbClient.getConnection(ar -> {
			if(ar.failed()) {
				LOGGER.error("Could not open a database connection", ar.cause());
				readyHandler.handle(Future.failedFuture(ar.cause()));
			} else {
				SQLConnection connection = ar.result();
				connection.execute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE), create -> {
					connection.close();
					if(create.failed()) {
						LOGGER.error("Database preparation error", create.cause());
						readyHandler.handle(Future.failedFuture(create.cause()));
					} else {
						readyHandler.handle(Future.succeededFuture(this));
					}
				});
			}
		});*/
	}

	@Override
	public WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler) {
		
		dbClient.rxQuery(sqlQueries.get(SqlQuery.ALL_PAGES))
		.flatMapPublisher(res -> {
			List<JsonArray> results = res.getResults();
			return Flowable.fromIterable(results);
		})
		.map(json -> json.getString(0))
		.sorted()
		.collect(JsonArray::new, JsonArray::add)
		.subscribe(SingleHelper.toObserver(resultHandler));
		
		/*dbClient.query(sqlQueries.get(SqlQuery.ALL_PAGES), res -> {
			if(res.succeeded()) {
				JsonArray pages = new JsonArray(res.result()
						.getResults()
						.stream()
						.map(json -> json.getString(0))
						.sorted()
						.collect(Collectors.toList()));
				
				resultHandler.handle(Future.succeededFuture(pages));
			}else {
				LOGGER.error("Database query error", res.cause());
				resultHandler.handle(Future.failedFuture(res.cause()));
			}
		});*/
		return this;
	}

	@Override
	public WikiDatabaseService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler) {
		
		
		dbClient.rxQueryWithParams(sqlQueries.get(SqlQuery.GET_PAGE), new JsonArray().add(name))
		.map(result -> {
			if(result.getNumRows() > 0 ) {
				JsonArray row = result.getResults().get(0);
				return new JsonObject()
						.put("found", true)
						.put("id", row.getInteger(0))
						.put("rawContent", row.getString(1));
			} else {
				return new JsonObject().put("found", false);
			}
		})
		.subscribe(SingleHelper.toObserver(resultHandler));
		/*dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_PAGE), new JsonArray().add(name), fetch -> {
			if(fetch.succeeded()) {
				JsonObject response = new JsonObject();
				ResultSet resultSet = fetch.result();
				if(resultSet.getNumRows() == 0) {
					response.put("found", false);
				} else {
					response.put("found", true);
					JsonArray row = resultSet.getResults().get(0);
					response.put("id", row.getInteger(0));
					response.put("rawContent", row.getString(1));
				}
				resultHandler.handle(Future.succeededFuture(response));
			}else {
				LOGGER.error("Database query error",fetch.cause());
				resultHandler.handle(Future.failedFuture(fetch.cause()));
			}
		});*/
		return this;
	}
	
	
	@Override
	public WikiDatabaseService fetchPageById(int id, Handler<AsyncResult<JsonObject>> resultHandler) {
		
		String query = sqlQueries.get(SqlQuery.GET_PAGE_BY_ID);
		JsonArray params = new JsonArray().add(id);
		
		Single<ResultSet> resultSet = dbClient.rxQueryWithParams(query, params);
		
		resultSet.map(result -> {
			if(result.getNumRows() > 0) {
				JsonObject row = result.getRows().get(0);
				return new JsonObject()
						.put("found", true)
						.put("id", row.getInteger("ID"))
						.put("name", row.getString("NAME"))
						.put("content", row.getString("CONTENT"));
			}else {
				return new JsonObject().put("found", false);
			}
		})
		.subscribe(SingleHelper.toObserver(resultHandler));
		
		
		/*dbClient.queryWithParams(sqlQueries.get(SqlQuery.GET_PAGE_BY_ID), new JsonArray().add(id), fetch -> {
			if(fetch.succeeded()) {
				JsonObject response = new JsonObject();
				ResultSet resultSet = fetch.result();
				if(resultSet.getNumRows() == 0) {
					response.put("found", false);
				} else {
					response.put("found", true);
					JsonArray row = resultSet.getResults().get(0);
					response.put("name", row.getString(0));
					response.put("id", id);
					response.put("content", row.getString(1));
				}
				resultHandler.handle(Future.succeededFuture(response));
			}else {
				LOGGER.error("Database query error",fetch.cause());
				resultHandler.handle(Future.failedFuture(fetch.cause()));
			}
		});*/
		return this;
	}
	

	@Override
	public WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler) {
		
		dbClient.rxUpdateWithParams(sqlQueries.get(SqlQuery.CREATE_PAGE), new JsonArray().add(title).add(markdown))
		.ignoreElement()
		.subscribe(CompletableHelper.toObserver(resultHandler));
		
		
		
		
		/*JsonArray data = new JsonArray().add(title).add(markdown);
		dbClient.updateWithParams(sqlQueries.get(SqlQuery.CREATE_PAGE), data, res -> {
			if(res.succeeded()) {
				resultHandler.handle(Future.succeededFuture());
			} else {
				LOGGER.error("Database query error",res.cause());
				resultHandler.handle(Future.failedFuture(res.cause()));
			}
		});*/
		return this;
	}

	@Override
	public WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler) {
		
		dbClient.rxUpdateWithParams(sqlQueries.get(sqlQueries.get(SqlQuery.SAVE_PAGE)),
		new JsonArray().add(markdown).add(id))
		.ignoreElement()
		.subscribe(CompletableHelper.toObserver(resultHandler));
		
		/*	JsonArray data = new JsonArray().add(markdown).add(id);
		dbClient.updateWithParams(sqlQueries.get(SqlQuery.SAVE_PAGE), data, res -> {
			if(res.succeeded()) {
				resultHandler.handle(Future.succeededFuture());
			}else {
				LOGGER.error("Database query error",res.cause());
				resultHandler.handle(Future.failedFuture(res.cause()));
			}
		});*/
		return this;
	}

	@Override
	public WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler) {
		
		JsonArray data = new JsonArray().add(id);
		dbClient.rxUpdateWithParams(sqlQueries.get(SqlQuery.DELETE_PAGE), data)
		.ignoreElement()
		.subscribe(CompletableHelper.toObserver(resultHandler));
		/*JsonArray data = new JsonArray().add(id);
		dbClient.updateWithParams(sqlQueries.get(SqlQuery.DELETE_PAGE), data, res -> {
			if(res.succeeded()) {
				resultHandler.handle(Future.succeededFuture());
			} else {
				LOGGER.error("Database query error",res.cause());
				resultHandler.handle(Future.failedFuture(res.cause()));
			}
		});*/
		return this;
	}

	@Override
	public WikiDatabaseService fetchAllPagesData(Handler<AsyncResult<List<JsonObject>>>  resultHandler) {
		
		dbClient.rxQuery(sqlQueries.get(SqlQuery.ALL_PAGES_DATA))
		.map(ResultSet::getRows)
		.subscribe(SingleHelper.toObserver(resultHandler));
		return this;
		/*dbClient.query(sqlQueries.get(SqlQuery.ALL_PAGES_DATA), queryResult -> {
			if(queryResult.succeeded()) {
				resultHandler.handle(Future.succeededFuture(queryResult.result().getRows()));
			}else {
				LOGGER.error("Database query error",queryResult.cause());
				resultHandler.handle(Future.failedFuture(queryResult.cause()));
			}
		});
		return null;*/
	}



}
