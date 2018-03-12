package org.folio.circulation.support;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.server.WebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;

public class LoanRulesClient {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final OkapiHttpClient client;
  private final URL root;

  public LoanRulesClient(OkapiHttpClient client, WebContext context)
    throws MalformedURLException {

    this.client = client;
    root = context.getOkapiBasedUrl("/circulation/loan-rules");
  }

  public void applyRules(
    String loanTypeId,
    String locationId,
    String materialTypeId,
    String patronGroup,
    Handler<HttpClientResponse> responseHandler) {

    String loanRulesQuery = queryParameters(loanTypeId, locationId, materialTypeId, patronGroup);

    log.info(String.format("Applying loan rules for %s", loanRulesQuery));

    client.get(String.format("%s/%s?%s", root, "apply", loanRulesQuery),
      responseHandler);
  }

  private String queryParameters(
    String loanTypeId,
    String locationId,
    String materialTypeId,
    String patronGroup) {

    return String.format(
      "item_type_id=%s&loan_type_id=%s&patron_type_id=%s&shelving_location_id=%s",
      materialTypeId, loanTypeId, patronGroup, locationId);
  }
}
