package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.http.server.WebContext;

public class DeclareLostResource extends Resource {

  public DeclareLostResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    router.put("/circulation/loans/:id/declare-lost")
      .handler(this::declareLost);
  }

  private void declareLost(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    final LoanRepository loanRepository = new LoanRepository(clients);

    final String loanId = routingContext.request().getParam("id");
    final String comment = routingContext.getBodyAsJson().getString("comment");

    loanRepository.getById(loanId)
      .thenApply(r -> r.map(loan -> loan.declareItemLost(comment)))
      .thenApply(r -> r.after(loanRepository::updateLoan)
        .thenCompose(r1 -> r1.after(loanRepository::updateLoanItemInStorage)
        .thenApply(NoContentResult::from)
        .thenAccept(result -> result.writeTo(routingContext.response()))));
  }
}
