package org.folio.circulation.support;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.http.client.NamedQueryParameter.namedParameter;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

public class CirculationRulesClient {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final URL root;
  private final OkapiHttpClient client;

  CirculationRulesClient(OkapiHttpClient client,
    WebContext context, String policyPath)

    throws MalformedURLException {

    root = context.getOkapiBasedUrl(policyPath);
    this.client = client;
  }

  public CompletableFuture<Result<Response>> applyRules(String loanTypeId,
    String locationId, String materialTypeId, String patronGroupId) {

    if (!ObjectUtils.allNotNull(materialTypeId, loanTypeId, patronGroupId, locationId)) {
      String errorMessage = format("Failed to apply rules for " +
          "materialTypeId: %s, loanTypeId: %s, patronGroupId: %s, locationId: %s",
        materialTypeId, loanTypeId, patronGroupId, locationId);

      log.error(errorMessage);

      return completedFuture(failedDueToServerError(errorMessage));
    }

    return client.get(root, namedParameter("item_type_id", materialTypeId),
        namedParameter("loan_type_id", loanTypeId),
        namedParameter("patron_type_id", patronGroupId),
        namedParameter("location_id", locationId));
  }
}
