package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.representations.ItemSummaryRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.OkJsonResponseResult;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collector;

import static org.folio.circulation.domain.ItemStatus.IN_TRANSIT;
import static org.folio.circulation.support.CqlQuery.exactMatch;

public class ItemsInTransitResource extends Resource {
  private static final String ITEMS_KEY = "items";
  private static final String STATUS_NAME_KEY = "status.name";
  private static final String TOTAL_RECORDS_KEY = "totalRecords";

  private final String rootPath;

  public ItemsInTransitResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);
    routeRegistration.getMany(this::getMany);
  }

  private void getMany(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);

    CompletableFuture<Result<Collection<Item>>> itemsInTransit = itemRepository
      .findByQuery(exactMatch(STATUS_NAME_KEY, IN_TRANSIT.getValue()));

    itemsInTransit
      .thenApply(this::mapResultToJson)
      .thenApply(OkJsonResponseResult::from)
      .thenAccept(r -> r.writeTo(routingContext.response()));
  }

  private Result<JsonObject> mapResultToJson(Result<Collection<Item>> items) {
    return items.map(resultList -> resultList
      .stream()
      .map(item -> new ItemSummaryRepresentation().createItemSummary(item))
      .collect(Collector.of(JsonArray::new, JsonArray::add, JsonArray::add)))
      .next(jsonArray -> Result.succeeded(new JsonObject()
        .put(ITEMS_KEY, jsonArray)
        .put(TOTAL_RECORDS_KEY, jsonArray.size())));
  }

}
