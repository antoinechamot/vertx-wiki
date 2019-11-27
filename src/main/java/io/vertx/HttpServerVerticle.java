package io.vertx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;

public class HttpServerVerticle extends AbstractVerticle{
	
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);
	
	public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
	public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";
	
	private String wikiDbQueue = "wikidb.queue";
	
	private FreeMarkerTemplateEngine templateEngine;
	
	
	public void start(Promise<Void> promise) throws Exception {
		
		wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");
		
		HttpServer server = vertx.createHttpServer();
		
		
		Router router = Router.router(vertx);
		router.get("/").handler(this::indexHandler);
		router.get("/wiki/:page").handler(this::pageRenderingHandler); 
		router.post().handler(BodyHandler.create());
		router.post("/save").handler(this::pageUpdateHandler);
		router.post("/create").handler(this::pageCreateHandler);
		router.post("/delete").handler(this::pageDeletionHandler);
		
		templateEngine = FreeMarkerTemplateEngine.create(vertx);
		
		int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT,8080);
		server.requestHandler(router).listen(portNumber, ar ->{
			if(ar.succeeded()) {
				LOGGER.info("HTTP server running on port " + portNumber );
				promise.complete();
			} else {
				LOGGER.error("Could not start a HTTP server",ar.cause());
				promise.fail(ar.cause());
			}
		});
		
	}
	
	private void indexHandler(RoutingContext context) {
		
		DeliveryOptions options = new DeliveryOptions().addHeader("action", "all-pages");
		
		vertx.eventBus().request(wikiDbQueue, new JsonObject(), options, reply -> {
			if(reply.succeeded()) {
				JsonObject body = (JsonObject) reply.result().body();
				context.put("title", "Wiki home");
				context.put("pages", body.getJsonArray("pages").getList());
				templateEngine.render(context.data(), "template/index.ftl", ar -> {
					if(ar.succeeded()) {
						context.response().putHeader("Content-type", "text/html");
						context.response().end(ar.result());
					}else {
						context.fail(ar.cause());
					}
				});
				
			} else {
				context.fail(reply.cause());
			}
		});
	}
	

}
