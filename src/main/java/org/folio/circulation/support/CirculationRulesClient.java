package org.folio.circulation.support;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;

import org.folio.circulation.domain.CirculationActionType;
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
  private final String loanPolicyPath = "/circulation/rules/loan-policy";
  private final String requestPolicyPath = "/circulation/rules/request-policy";

  CirculationRulesClient(OkapiHttpClient client, WebContext context, CirculationActionType circulationType)
    throws MalformedURLException {

    this.client = client;
    root = context.getOkapiBasedUrl(
      circulationType == CirculationActionType.LOAN ? loanPolicyPath : requestPolicyPath
    );
  }

  public void applyRules(
    String circulationTypeId,
    String locationId,
    String materialTypeId,
    String patronGroup,
    Handler<HttpClientResponse> responseHandler) {

    String circulationRulesQuery = queryParameters(circulationTypeId, locationId,
      materialTypeId, patronGroup);

    log.info("Applying circulation rules for {}", circulationRulesQuery);

    client.get(String.format("%s?%s", root, circulationRulesQuery),
      responseHandler);
  }

  private String queryParameters(
    String circulationTypeId,
    String locationId,
    String materialTypeId,
    String patronGroup) {

    return String.format(
      "item_type_id=%s&loan_type_id=%s&patron_type_id=%s&shelving_location_id=%s",
      materialTypeId, circulationTypeId, patronGroup, locationId);
  }
}
