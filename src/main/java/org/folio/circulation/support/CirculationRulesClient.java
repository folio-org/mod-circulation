package org.folio.circulation.support;

import static org.folio.circulation.support.http.client.NamedQueryParameter.namedParameter;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.VertxWebClientOkapiHttpClient;
import org.folio.circulation.support.http.server.WebContext;

public class CirculationRulesClient {
  private final URL root;
  private final VertxWebClientOkapiHttpClient client;

  CirculationRulesClient(VertxWebClientOkapiHttpClient client,
    WebContext context, String policyPath)

    throws MalformedURLException {

    root = context.getOkapiBasedUrl(policyPath);
    this.client = client;
  }

  public CompletableFuture<Result<Response>> applyRules(String loanTypeId,
    String locationId, String materialTypeId, String patronGroupId) {

    return client.get(root, namedParameter("item_type_id", materialTypeId),
        namedParameter("loan_type_id", loanTypeId),
        namedParameter("patron_type_id", patronGroupId),
        namedParameter("location_id", locationId));
  }
}
