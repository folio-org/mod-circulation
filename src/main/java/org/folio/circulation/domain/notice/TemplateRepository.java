package org.folio.circulation.domain.notice;

import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.client.Response;

public class TemplateRepository {

  private static final String TEMPLATE_RECORD_TYPE = "template";
  private final CollectionResourceClient templateNoticesClient;

  private TemplateRepository(CollectionResourceClient templateNoticeClient) {
    this.templateNoticesClient = templateNoticeClient;
  }

  public static TemplateRepository using(Clients clients) {
    return new TemplateRepository(clients.templateNoticeClient());
  }

  public CompletableFuture<Result<Response>> findById(String id) {
    return templateNoticesClient.get(id);
  }

  public Result<Response> failIfTemplateNotFound(
    Response response, String templateId) {

    if (response.getStatusCode() == 404) {
      return failed(new RecordNotFoundFailure(TEMPLATE_RECORD_TYPE, templateId));
    } else {
      return succeeded(response);
    }
  }
}
