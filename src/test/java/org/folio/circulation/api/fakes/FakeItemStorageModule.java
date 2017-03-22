package org.folio.circulation.api.fakes;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.folio.circulation.support.http.server.ClientErrorResponse;
import org.folio.circulation.support.http.server.JsonResponse;
import org.folio.circulation.support.http.server.SuccessResponse;
import org.folio.circulation.support.http.server.WebContext;

import java.util.HashMap;
import java.util.Map;

public class FakeItemStorageModule {
  private static final String rootPath = "/item-storage/items";

  private final Map<String, Map<String, JsonObject>> storedItemsByTenant;

  public FakeItemStorageModule(String tenantId) {
    storedItemsByTenant = new HashMap<>();
    storedItemsByTenant.put(tenantId, new HashMap<>());
  }

  public void register(Router router) {
    router.post(rootPath + "*").handler(BodyHandler.create());
    router.put(rootPath + "*").handler(BodyHandler.create());

    router.delete(rootPath).handler(this::empty);
    router.post(rootPath).handler(this::create);

    router.route(HttpMethod.GET, rootPath + "/:id")
      .handler(this::get);

    router.route(HttpMethod.PUT, rootPath + "/:id")
      .handler(this::replace);

    router.route(HttpMethod.DELETE, rootPath + "/:id")
      .handler(this::delete);
  }

  private void create(RoutingContext routingContext) {

    WebContext context = new WebContext(routingContext);

    JsonObject body = getJsonFromBody(routingContext);

    getItemsForTenant(context).put(body.getString("id"), body);

    JsonResponse.created(routingContext.response(),
      routingContext.getBodyAsJson());
  }

  private void get(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    Map<String, JsonObject> itemsForTenant = getItemsForTenant(context);

    if(itemsForTenant.containsKey(id)) {
      JsonResponse.success(routingContext.response(),
        itemsForTenant.get(id));
    }
    else {
      ClientErrorResponse.notFound(routingContext.response());
    }
  }

  private void replace(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    JsonObject body = getJsonFromBody(routingContext);

    Map<String, JsonObject> itemsForTenant = getItemsForTenant(context);

    itemsForTenant.replace(id, body);

    if(itemsForTenant.containsKey(id)) {
      SuccessResponse.noContent(routingContext.response());
    }
    else {
      ClientErrorResponse.notFound(routingContext.response());
    }
  }

  private void delete(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    Map<String, JsonObject> itemsForTenant = getItemsForTenant(context);

    if(itemsForTenant.containsKey(id)) {
      itemsForTenant.remove(id);

      SuccessResponse.noContent(routingContext.response());
    }
    else {
      ClientErrorResponse.notFound(routingContext.response());
    }
  }

  private void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    Map<String, JsonObject> loansForTenant = getItemsForTenant(context);

    loansForTenant.clear();

    SuccessResponse.noContent(routingContext.response());
  }

  private Map<String, JsonObject> getItemsForTenant(WebContext context) {
    return storedItemsByTenant.get(context.getTenantId());
  }

  private static JsonObject getJsonFromBody(RoutingContext routingContext) {
    if (hasBody(routingContext)) {
      return routingContext.getBodyAsJson();
    } else {
      return new JsonObject();
    }
  }

  private static boolean hasBody(RoutingContext routingContext) {
    return routingContext.getBodyAsString() != null &&
      routingContext.getBodyAsString().trim() != "";
  }
}
