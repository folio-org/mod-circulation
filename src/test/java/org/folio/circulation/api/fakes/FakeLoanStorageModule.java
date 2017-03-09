package org.folio.circulation.api.fakes;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.support.http.server.ClientErrorResponse;
import org.folio.circulation.support.http.server.JsonResponse;
import org.folio.circulation.support.http.server.SuccessResponse;
import org.folio.circulation.support.http.server.WebContext;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FakeLoanStorageModule extends AbstractVerticle {

  private static final int PORT_TO_USE = 9493;
  private static final String address =
    String.format("http://localhost:%s", PORT_TO_USE);

  private static final String rootPath = "/loan-storage/loans";

  private HttpServer server;

  private final Map<String, Map<String, JsonObject>> storedLoansByTenant;

  public static String getAddress() {
    return address;
  }

  public FakeLoanStorageModule() {
    storedLoansByTenant = new HashMap<>();
    storedLoansByTenant.put(APITestSuite.TENANT_ID, new HashMap<>());
  }

  public void start(Future<Void> startFuture) {
    System.out.println("Starting fake loan storage module");

    Router router = Router.router(vertx);

    this.server = vertx.createHttpServer();

    JsonObject config = vertx.getOrCreateContext().config();

    router.post(rootPath + "*").handler(BodyHandler.create());
    router.put(rootPath + "*").handler(BodyHandler.create());

    router.post(rootPath).handler(this::create);
    router.get(rootPath).handler(this::getMany);
    router.delete(rootPath).handler(this::empty);

    router.route(HttpMethod.PUT, rootPath + "/:id")
      .handler(this::replace);

    router.route(HttpMethod.GET, rootPath + "/:id")
      .handler(this::get);

    server.requestHandler(router::accept)
      .listen(PORT_TO_USE, result -> {
        if (result.succeeded()) {
          System.out.println(
            String.format("Listening on %s", server.actualPort()));
          startFuture.complete();
        } else {
          startFuture.fail(result.cause());
        }
      });
  }

  public void stop(Future<Void> stopFuture) {
    System.out.println("Stopping fake loan storage module");

    if(server != null) {
      server.close(result -> {
        if (result.succeeded()) {
          System.out.println(
            String.format("Stopped listening on %s", server.actualPort()));
          stopFuture.complete();
        } else {
          stopFuture.fail(result.cause());
        }
      });
    }
  }

  private void create(RoutingContext routingContext) {

    WebContext context = new WebContext(routingContext);

    JsonObject body = getJsonFromBody(routingContext);

    getLoansForTenant(context).put(body.getString("id"), body);

    JsonResponse.created(routingContext.response(),
      routingContext.getBodyAsJson());
  }

  private void replace(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    JsonObject body = getJsonFromBody(routingContext);

    Map<String, JsonObject> loansForTenant = getLoansForTenant(context);

    loansForTenant.replace(id, body);

    if(loansForTenant.containsKey(id)) {
      SuccessResponse.noContent(routingContext.response());
    }
    else {
      ClientErrorResponse.notFound(routingContext.response());
    }
  }

  private void get(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    String id = routingContext.request().getParam("id");

    Map<String, JsonObject> loansForTenant = getLoansForTenant(context);

    if(loansForTenant.containsKey(id)) {
      JsonResponse.success(routingContext.response(),
        loansForTenant.get(id));
    }
    else {
      ClientErrorResponse.notFound(routingContext.response());
    }
  }

  private void getMany(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    Integer limit = context.getIntegerParameter("limit", 10);
    Integer offset = context.getIntegerParameter("offset", 0);
    String query = context.getStringParameter("query", null);

    Map<String, JsonObject> loansForTenant = getLoansForTenant(context);

    List<Predicate<JsonObject>> predicates = filterFromQuery(query);

    List<JsonObject> filteredItems = loansForTenant.values().stream()
      .filter(predicates.stream().reduce(Predicate::and).orElse(t -> false))
      .collect(Collectors.toList());

    List<JsonObject> pagedItems = filteredItems.stream()
      .skip(offset)
      .limit(limit)
      .collect(Collectors.toList());

    JsonObject result = new JsonObject();

    result.put("loans", new JsonArray(pagedItems));
    result.put("totalRecords", filteredItems.size());

    JsonResponse.success(routingContext.response(), result);
  }

  private void empty(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    Map<String, JsonObject> loansForTenant = getLoansForTenant(context);

    loansForTenant.clear();

    SuccessResponse.noContent(routingContext.response());
  }

  private Map<String, JsonObject> getLoansForTenant(WebContext context) {
    return storedLoansByTenant.get(context.getTenantId());
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

  private List<Predicate<JsonObject>> filterFromQuery(String query) {

    if(query == null || query.trim() == "") {
      ArrayList<Predicate<JsonObject>> predicates = new ArrayList<>();
      predicates.add(t -> true);
      return predicates;
    }

    List<ImmutablePair<String, String>> pairs = Arrays.stream(query.split(" and "))
      .map( pairText -> {
        String searchField = pairText.split("=")[0];

        String searchTerm =
          pairText.replace(String.format("%s=", searchField), "")
            .replaceAll("\"", "")
            .replaceAll("\\*", "");

        return new ImmutablePair<>(searchField, searchTerm);
      })
      .collect(Collectors.toList());

    return pairs.stream()
      .map(pair -> filterByField(pair.getLeft(), pair.getRight()))
      .collect(Collectors.toList());
  }

  private Predicate<JsonObject> filterByField(String field, String term) {
    return loan -> {
      if (term == null || field == null) {
        return true;
      } else {
        if(field.contains(".")) {
          String[] fields = field.split("\\.");

          return loan.getJsonObject(String.format("%s", fields[0]))
            .getString(String.format("%s", fields[1])).contains(term);
        }
        else {
          return loan.getString(String.format("%s", field)).contains(term);
        }
      }
    };
  }
}
