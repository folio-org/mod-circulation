package org.folio.circulation.support.http.server;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.folio.circulation.support.http.client.BufferHelper;
import org.folio.circulation.support.http.client.HttpClient;

import java.net.MalformedURLException;

public class VertxCollectionResource {

  private final String rootPath;

  public VertxCollectionResource(String rootPath) {
    this.rootPath = rootPath;
  }

  public void register(Router router) {
    router.post(rootPath + "*").handler(BodyHandler.create());
    router.post(rootPath).handler(this::create);
  }

  private void create(RoutingContext routingContext) {

    WebContext context = new WebContext(routingContext);
    HttpClient client = new HttpClient(routingContext.vertx(), "");

    try {
      client.post(context.getOkapiBasedUrl("/loan-storage/loans"),
        routingContext.getBodyAsJson(),
        context.getTenantId(), response -> {
          response.bodyHandler(buffer -> {
            String responseBody = BufferHelper.stringFromBuffer(buffer);

            if(response.statusCode() == 201) {
              JsonResponse.created(routingContext.response(),
                new JsonObject(responseBody));
            }
            else {
              ServerErrorResponse.internalError(routingContext.response(),
                String.format("Response From Storage Module: %s: %s",
                  response.statusCode(), responseBody));
            }
          });
      });
    } catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));
    }
  }
}
