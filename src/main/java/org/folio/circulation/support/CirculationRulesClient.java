package org.folio.circulation.support;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;

import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.server.WebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;

public class CirculationRulesClient {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final OkapiHttpClient client;
  private final URL root;

  CirculationRulesClient(OkapiHttpClient client, WebContext context, String subPath)
    throws MalformedURLException {

    this.client = client;
    root = context.getOkapiBasedUrl(subPath);
  }

  public void applyRules(
    String loanTypeId,
    String locationId,
    String materialTypeId,
    String patronGroup,
    Handler<HttpClientResponse> responseHandler) {

    String circulationRulesQuery = queryParameters(loanTypeId, locationId,
      materialTypeId, patronGroup);

    log.info("Applying circulation rules for {}", circulationRulesQuery);

    client.get(String.format("%s?%s", root, circulationRulesQuery),
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
