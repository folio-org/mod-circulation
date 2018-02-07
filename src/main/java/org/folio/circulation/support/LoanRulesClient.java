package org.folio.circulation.support;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.server.WebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;

public class LoanRulesClient {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final OkapiHttpClient client;
  private final WebContext context;

  public LoanRulesClient(OkapiHttpClient client, WebContext context) {
    this.client = client;
    this.context = context;
  }

  public void applyRules(
    String loanTypeId,
    String locationId,
    String materialTypeId,
    String patronGroup,
    Handler<HttpClientResponse> responseHandler) throws MalformedURLException {

    String loanRulesQuery = String.format(
      "?item_type_id=%s&loan_type_id=%s&patron_type_id=%s&shelving_location_id=%s",
      materialTypeId, loanTypeId, patronGroup, locationId);

    log.info(String.format("Applying loan rules for %s", loanRulesQuery));

    client.get(context.getOkapiBasedUrl("/circulation/loan-rules/apply") +
        loanRulesQuery, responseHandler);
  }
}
