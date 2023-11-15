package org.folio.circulation.services.events;

import org.folio.util.pubsub.support.PomReader;

import io.vertx.core.Future;

public interface ModuleIdProvider {
  String REAL_MODULE_ID = PomReader.INSTANCE.getModuleName() + "-" + PomReader.INSTANCE.getVersion();

  Future<String> getModuleId();
}
