package org.folio.circulation.resources;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import org.folio.circulation.loanrules.Drools;
import org.folio.circulation.loanrules.Text2Drools;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.server.ForwardResponse;
import org.folio.circulation.support.http.server.JsonResponse;
import org.folio.circulation.support.http.server.ServerErrorResponse;
import org.folio.circulation.support.http.server.SuccessResponse;
import org.folio.circulation.support.http.server.WebContext;

import java.net.MalformedURLException;
import java.net.URL;

public class LoanRulesResource {

  private final String rootPath;

  public LoanRulesResource(String rootPath) {
    this.rootPath = rootPath;
  }

  public void register(Router router) {
    router.put(rootPath).handler(BodyHandler.create());

    router.get(rootPath).handler(this::get);
    router.put(rootPath).handler(this::put);
  }

  private void get(RoutingContext routingContext) {
    CollectionResourceClient loansRulesClient = getLoanRulesClient(routingContext);

    if (loansRulesClient == null) {
      return;
    }

    loansRulesClient.get(response -> {
      if (response.getStatusCode() == 200) {
        JsonObject loanRules = new JsonObject(response.getBody());
        loanRules.put("loanRulesAsDrools", Text2Drools.convert(loanRules.getString("loanRulesAsTextFile")));
        JsonResponse.success(routingContext.response(), loanRules);
      } else {
        ForwardResponse.forward(routingContext.response(), response);
      }
    });
  }

  private void put(RoutingContext routingContext) {
    CollectionResourceClient loansRulesClient = getLoanRulesClient(routingContext);

    if (loansRulesClient == null) {
      return;
    }

    JsonObject rulesInput = routingContext.getBodyAsJson();
    JsonObject rules = rulesInput.copy();
    rules.remove("loanRulesAsDrools");
    loansRulesClient.put(rules, response -> {
      if (response.getStatusCode() == 204) {
        SuccessResponse.noContent(routingContext.response());
      } else {
        ForwardResponse.forward(routingContext.response(), response);
      }
    });
  }

  private OkapiHttpClient createHttpClient(RoutingContext routingContext,
                                           WebContext context)
    throws MalformedURLException {

    return new OkapiHttpClient(routingContext.vertx().createHttpClient(),
      new URL(context.getOkapiLocation()), context.getTenantId(),
      context.getOkapiToken(),
      exception -> ServerErrorResponse.internalError(routingContext.response(),
        String.format("Failed to contact storage module: %s",
          exception.toString())));
  }

  private CollectionResourceClient createLoanRulesClient(
    OkapiHttpClient client,
    WebContext context)
    throws MalformedURLException {

    CollectionResourceClient loanRulesClient;

    loanRulesClient = new CollectionResourceClient(
      client, context.getOkapiBasedUrl("/loan-rules-storage"),
      context.getTenantId());

    return loanRulesClient;
  }

  private CollectionResourceClient getLoanRulesClient(RoutingContext routingContext) {
    WebContext context = new WebContext(routingContext);

    try {
      OkapiHttpClient client = createHttpClient(routingContext, context);
      return createLoanRulesClient(client, context);
    }
    catch (MalformedURLException e) {
      ServerErrorResponse.internalError(routingContext.response(),
        String.format("Invalid Okapi URL: %s", context.getOkapiLocation()));
      return null;
    }
  }
}
