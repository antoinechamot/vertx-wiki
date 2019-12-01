/*
* Copyright 2014 Red Hat, Inc.
*
* Red Hat licenses this file to you under the Apache License, version 2.0
* (the "License"); you may not use this file except in compliance with the
* License. You may obtain a copy of the License at:
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations
* under the License.
*/

package io.vertx.guides.wiki.database;

import io.vertx.guides.wiki.database.WikiDatabaseService;
import io.vertx.core.Vertx;
import io.vertx.core.Handler;
import io.vertx.core.AsyncResult;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.serviceproxy.ProxyHandler;
import io.vertx.serviceproxy.ServiceException;
import io.vertx.serviceproxy.ServiceExceptionMessageCodec;
import io.vertx.serviceproxy.HelperUtils;

import io.vertx.core.json.JsonArray;
import java.util.List;
import io.vertx.core.json.JsonObject;
import io.vertx.guides.wiki.database.WikiDatabaseService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
/*
  Generated Proxy code - DO NOT EDIT
  @author Roger the Robot
*/

@SuppressWarnings({"unchecked", "rawtypes"})
public class WikiDatabaseServiceVertxProxyHandler extends ProxyHandler {

  public static final long DEFAULT_CONNECTION_TIMEOUT = 5 * 60; // 5 minutes 
  private final Vertx vertx;
  private final WikiDatabaseService service;
  private final long timerID;
  private long lastAccessed;
  private final long timeoutSeconds;

  public WikiDatabaseServiceVertxProxyHandler(Vertx vertx, WikiDatabaseService service){
    this(vertx, service, DEFAULT_CONNECTION_TIMEOUT);
  }

  public WikiDatabaseServiceVertxProxyHandler(Vertx vertx, WikiDatabaseService service, long timeoutInSecond){
    this(vertx, service, true, timeoutInSecond);
  }

  public WikiDatabaseServiceVertxProxyHandler(Vertx vertx, WikiDatabaseService service, boolean topLevel, long timeoutSeconds) {
      this.vertx = vertx;
      this.service = service;
      this.timeoutSeconds = timeoutSeconds;
      try {
        this.vertx.eventBus().registerDefaultCodec(ServiceException.class,
            new ServiceExceptionMessageCodec());
      } catch (IllegalStateException ex) {}
      if (timeoutSeconds != -1 && !topLevel) {
        long period = timeoutSeconds * 1000 / 2;
        if (period > 10000) {
          period = 10000;
        }
        this.timerID = vertx.setPeriodic(period, this::checkTimedOut);
      } else {
        this.timerID = -1;
      }
      accessed();
    }


  private void checkTimedOut(long id) {
    long now = System.nanoTime();
    if (now - lastAccessed > timeoutSeconds * 1000000000) {
      close();
    }
  }

    @Override
    public void close() {
      if (timerID != -1) {
        vertx.cancelTimer(timerID);
      }
      super.close();
    }

    private void accessed() {
      this.lastAccessed = System.nanoTime();
    }

  public void handle(Message<JsonObject> msg) {
    try{
      JsonObject json = msg.body();
      String action = msg.headers().get("action");
      if (action == null) throw new IllegalStateException("action not specified");
      accessed();
      switch (action) {
        case "fetchAllPages": {
          service.fetchAllPages(HelperUtils.createHandler(msg));
          break;
        }
        case "fetchAllPagesData": {
          service.fetchAllPagesData(HelperUtils.createListHandler(msg));
          break;
        }
        case "fetchPage": {
          service.fetchPage((java.lang.String)json.getValue("name"),
                        HelperUtils.createHandler(msg));
          break;
        }
        case "createPage": {
          service.createPage((java.lang.String)json.getValue("title"),
                        (java.lang.String)json.getValue("markdown"),
                        HelperUtils.createHandler(msg));
          break;
        }
        case "savePage": {
          service.savePage(json.getValue("id") == null ? null : (json.getLong("id").intValue()),
                        (java.lang.String)json.getValue("markdown"),
                        HelperUtils.createHandler(msg));
          break;
        }
        case "deletePage": {
          service.deletePage(json.getValue("id") == null ? null : (json.getLong("id").intValue()),
                        HelperUtils.createHandler(msg));
          break;
        }
        default: throw new IllegalStateException("Invalid action: " + action);
      }
    } catch (Throwable t) {
      msg.reply(new ServiceException(500, t.getMessage()));
      throw t;
    }
  }
}