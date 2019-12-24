package org.folio.circulation.resources;

import org.folio.circulation.domain.ChangeDueDateRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.representations.changeduedate.BatchChangeDueDateRequest;
import org.folio.circulation.domain.representations.changeduedate.BatchChangeDueDateResponseMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class BatchChangeDueDateResource extends Resource {

  private final String rootPath;

  public BatchChangeDueDateResource(HttpClient client, String rootPath) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {

    RouteRegistration routeRegistration = new RouteRegistration(
      rootPath, router);

    routeRegistration.create(this::batchChangeDueDate);
  }

  private void batchChangeDueDate(RoutingContext routingContext) {

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    ChangeDueDateRepository changeDueDateRepository = new ChangeDueDateRepository(
      clients);
    List<String> failedIds = new CopyOnWriteArrayList<>();

    BatchChangeDueDateRequest requestResult = validateRequest(
      routingContext);

    CompletableFuture
      .allOf(
        requestResult.getLoanIds().stream().map(loanId ->
          {
            CompletableFuture<Result<Loan>> result = changeDueDateRepository
              .changeDueDate(loanId, requestResult.getDueDate());
            result.handle((res, e) -> {
              if (res.failed()) {
                failedIds.add(loanId);
              }
              return res;
            });
            return result;
          }

        ).toArray(CompletableFuture[]::new))
      .thenApply(a -> BatchChangeDueDateResponseMapper.from(failedIds))
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private BatchChangeDueDateRequest validateRequest(
    RoutingContext routingContext) {

    Result<BatchChangeDueDateRequest> request = BatchChangeDueDateRequest
      .from(routingContext);

    if (request.failed()) {
      request.cause().writeTo(routingContext.response());
    }

    return request.value();
  }
}


