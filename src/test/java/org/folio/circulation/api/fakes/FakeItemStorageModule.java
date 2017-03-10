package org.folio.circulation.api.fakes;

import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.support.http.server.SuccessResponse;

public class FakeItemStorageModule {
  private static final String rootPath = "/item-storage/items";

  public void register(Router router) {
    router.delete(rootPath).handler(this::empty);
  }

  private void empty(RoutingContext routingContext) {
    SuccessResponse.noContent(routingContext.response());
  }
}
