package org.folio.circulation.resources;

import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class SearchSlipsResource extends SlipsResource {
  private static final String SEARCH_SLIPS_KEY = "searchSlips";
  private final String rootPath;

  public SearchSlipsResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);
    routeRegistration.getMany(this::getMany);
  }

  protected void getMany(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    fetchHoldRequests()
      .thenApply(flatMapResult(requests -> mapResultToJson(requests, SEARCH_SLIPS_KEY)))
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<MultipleRecords<Request>>> fetchHoldRequests() {
    return ofAsync(MultipleRecords.empty());
  }
}
