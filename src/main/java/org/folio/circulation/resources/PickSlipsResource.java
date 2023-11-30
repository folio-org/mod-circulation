package org.folio.circulation.resources;

import static org.folio.circulation.domain.ItemStatus.PAGED;
import static org.folio.circulation.domain.RequestType.PAGE;

import io.vertx.core.http.HttpClient;

public class PickSlipsResource extends SlipsResource {

  public PickSlipsResource(String rootPath, HttpClient client) {
    super(rootPath, client, PAGE, PAGED, "pickSlips");
  }
}
