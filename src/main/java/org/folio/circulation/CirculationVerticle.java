package org.folio.circulation;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

public class CirculationVerticle extends AbstractVerticle {

  public void start(Future<Void> startFuture) {
    startFuture.complete();
  }

  public void stop(Future<Void> stopFuture) {
    stopFuture.complete();
  }
}
