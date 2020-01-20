package org.folio.circulation.domain.notice;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.Response;

public class TemplateRepository {

  private TemplateRepository(
    CollectionResourceClient templateNoticeClient) {
    this.templateNoticesClient = templateNoticeClient;
  }

  private final CollectionResourceClient templateNoticesClient;

  public static TemplateRepository using(Clients clients) {
    return new TemplateRepository(clients.templateNoticeClient());
  }

  public CompletableFuture<Result<Response>> findById(String id) {
    return templateNoticesClient.get(id);
  }
}
