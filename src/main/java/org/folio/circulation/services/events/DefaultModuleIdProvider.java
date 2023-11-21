package org.folio.circulation.services.events;

import io.vertx.core.Future;

public class DefaultModuleIdProvider implements ModuleIdProvider {

  @Override
  public Future<String> getModuleId() {
    return Future.succeededFuture(ModuleIdProvider.REAL_MODULE_ID);
  }
}
