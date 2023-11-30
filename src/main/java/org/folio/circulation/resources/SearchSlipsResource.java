package org.folio.circulation.resources;

import static org.folio.circulation.domain.RequestType.HOLD;
import static org.folio.circulation.domain.RequestTypeItemStatusWhiteList.getItemStatusesAllowedForRequestType;

import io.vertx.core.http.HttpClient;

public class SearchSlipsResource extends SlipsResource {

  public SearchSlipsResource(String rootPath, HttpClient client) {
    super(rootPath, client, HOLD, getItemStatusesAllowedForRequestType(HOLD), "searchSlips");
  }
}
