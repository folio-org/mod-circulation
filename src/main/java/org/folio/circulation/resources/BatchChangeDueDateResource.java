package org.folio.circulation.resources;

import org.folio.circulation.domain.ChangeDueDateRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.representations.changeduedate.BatchChangeDueDateRequest;
import org.folio.circulation.domain.representations.changeduedate.BatchChangeDueDateRequestValidator;
import org.folio.circulation.domain.representations.changeduedate.BatchChangeDueDateResponseMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchChangeDueDateResource extends Resource {

  private final String rootPath;
  private static final Logger log = LoggerFactory
    .getLogger(MethodHandles.lookup().lookupClass());

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

    Result<BatchChangeDueDateRequestValidator> requestValidator = new BatchChangeDueDateRequestValidator(
      routingContext).validate();
    if (requestValidator.failed()) {
      requestValidator.cause().writeTo(routingContext.response());
      return;
    }

    BatchChangeDueDateRequest request = requestValidator.value()
      .createRequest();
    CompletableFuture.allOf(
      request.getLoanIds().stream().map(loanId ->
        {
          CompletableFuture<Result<Loan>> result = changeDueDateRepository
            .changeDueDate(loanId, request.getDueDate());

          result.exceptionally(e -> {
            log.error(e.getMessage(), e);
            return Result.failed(null);
          });

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
}


