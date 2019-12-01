package io.vertx.guides.wiki.http;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rjeschke.txtmark.Processor;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
import io.vertx.guides.wiki.database.WikiDatabaseService;

public class HttpServerVerticle extends AbstractVerticle {

	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

	public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
	public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

	private String wikiDbQueue = "wikidb.queue";

	private FreeMarkerTemplateEngine templateEngine;

	private static final String EMPTY_PAGE_MARKDOWN = "# A new page\n" + "\n" + "Feel-free to write in Markdown!\n";

	private WikiDatabaseService dbService;
	private WebClient webClient;

	public void start(Promise<Void> promise) throws Exception {
		
		webClient = WebClient.create(vertx,new WebClientOptions().setSsl(true).setUserAgent("vert-x3"));

		wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");
		dbService = WikiDatabaseService.createProxy(vertx, wikiDbQueue);

		HttpServer server = vertx.createHttpServer();

		Router router = Router.router(vertx);
		router.get("/").handler(this::indexHandler);
		router.get("/wiki/:page").handler(this::pageRenderingHandler);
		router.post().handler(BodyHandler.create());
		router.post("/save").handler(this::pageUpdateHandler);
		router.post("/create").handler(this::pageCreateHandler);
		router.post("/delete").handler(this::pageDeletionHandler);
		router.get("/backup").handler(this::backupHandler);

		templateEngine = FreeMarkerTemplateEngine.create(vertx);

		int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);
		server.requestHandler(router).listen(portNumber, ar -> {
			if (ar.succeeded()) {
				LOGGER.info("HTTP server running on port " + portNumber);
				promise.complete();
			} else {
				LOGGER.error("Could not start a HTTP server", ar.cause());
				promise.fail(ar.cause());
			}
		});
		
		

	}

	private void indexHandler(RoutingContext context) {

		DeliveryOptions options = new DeliveryOptions().addHeader("action", "all-pages");

		/*
		 * vertx.eventBus().request(wikiDbQueue, new JsonObject(), options, reply -> {
		 * if(reply.succeeded()) { JsonObject body = (JsonObject) reply.result().body();
		 * context.put("title", "Wiki home"); context.put("pages",
		 * body.getJsonArray("pages").getList()); templateEngine.render(context.data(),
		 * "template/index.ftl", ar -> { if(ar.succeeded()) {
		 * context.response().putHeader("Content-type", "text/html");
		 * context.response().end(ar.result()); }else { context.fail(ar.cause()); } });
		 * 
		 * } else { context.fail(reply.cause()); } });
		 */

		dbService.fetchAllPages(reply -> {
			if (reply.succeeded()) {
				context.put("title", "Wiki home");
				context.put("pages", reply.result().getList());
				templateEngine.render(context.data(), "template/index.ftl", ar -> {
					if (ar.succeeded()) {
						context.response().putHeader("Content-type", "text/html");
						context.response().end(ar.result());
					} else {
						context.fail(ar.cause());
					}
				});

			} else {
				context.fail(reply.cause());
			}
		});
	}

	private void pageRenderingHandler(RoutingContext context) {

		String requestedPage = context.request().getParam("page");

		dbService.fetchPage(requestedPage, reply -> {
			if (reply.succeeded()) {

				JsonObject payload = reply.result();
				boolean found = payload.getBoolean("found");
				String rawContent = payload.getString("rawContent", EMPTY_PAGE_MARKDOWN);
				context.put("title", requestedPage);
				context.put("id", payload.getInteger("id", -1));
				context.put("newPage", found ? "no" : "yes");
				context.put("rawContent", rawContent);
				context.put("content", Processor.process(rawContent));
				context.put("timestamp", new Date().toString());

				templateEngine.render(context.data(), "template/page.ftl", ar -> {
					if (ar.succeeded()) {
						context.response().putHeader("Content-Type", "text/html");
						context.response().end(ar.result());
					} else {
						context.fail(ar.cause());
					}
				});

			} else {
				context.fail(reply.cause());
			}
		});

	}

	private void pageUpdateHandler(RoutingContext context) {

		String title = context.request().getParam("title");
		String markdown = context.request().getParam("markdown");

		Handler<AsyncResult<Void>> handler = reply -> {
			if (reply.succeeded()) {
				context.response().setStatusCode(303);
				context.response().putHeader("Location", "/wiki/" + title);
				context.response().end();
			} else {
				context.fail(reply.cause());
			}
		};

		if ("yes".equals(context.request().getParam("newPage"))) {
			dbService.createPage(title, markdown, handler);
		} else {
			dbService.savePage(Integer.valueOf(context.request().getParam("id")), markdown, handler);
		}

	}

	private void pageCreateHandler(RoutingContext context) {
		String pageName = context.request().getParam("name");
		String location = "/wiki/" + pageName;
		if (pageName == null || pageName.isEmpty()) {
			location = "/";
		}
		context.response().setStatusCode(303);
		context.response().putHeader("Location", location);
		context.response().end();
	}

	private void pageDeletionHandler(RoutingContext context) {

		dbService.deletePage(Integer.valueOf(context.request().getParam("id")), reply -> {
			if (reply.succeeded()) {
				context.response().setStatusCode(303);
				context.response().putHeader("Location", "/");
				context.response().end();
			} else {
				context.fail(reply.cause());
			}
		});
	}
	
	private void backupHandler(RoutingContext context) {
		dbService.fetchAllPagesData(reply -> {
			if(reply.succeeded()) {
				JsonArray filesObject = new JsonArray();
				JsonObject payload = new JsonObject()
						.put("files", filesObject)
						.put("language", "plaintext")
						.put("title", "vertx-wiki-backup")
						.put("public", true);
				
				reply.result()
				.forEach(page -> {
					JsonObject fileObject = new JsonObject();
					fileObject.put("name", page.getString("NAME"));
					fileObject.put("content", page.getString("CONTENT"));
					filesObject.add(fileObject);
				});
				
				webClient.post(443, "snippets.glot.io", "/snippets")
				.putHeader("Content-Type", "application/json")
				.as(BodyCodec.jsonObject())
				.sendJson(payload, ar -> {
					if(ar.succeeded()) {
						HttpResponse<JsonObject> response = ar.result();
						if(response.statusCode() == 200) {
							String url = "https://glot.io/snippets/" + response.body().getString("id");
				              context.put("backup_gist_url", url); 
				              indexHandler(context);
						}else {
							StringBuilder message = new StringBuilder()
									.append("Could no backup the wiki :")
									.append(response.statusMessage());
							JsonObject body = response.body();
							if(body != null) {
								message.append(System.getProperty("line.separator"))
								.append(body.encodePrettily());
							}
							LOGGER.error(message.toString());
							context.fail(502);
						}
						
					}else {
						Throwable err = ar.cause();
			            LOGGER.error("HTTP Client error", err);
			            context.fail(err);
					}
				});
			}else {
				context.fail(reply.cause());
			}
		});
	}

}
