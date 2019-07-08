package org.folio.circulation.support.logging;

public class Logging {
  private Logging() { }

  public static void initialiseFormat() {
    System.setProperty("vertx.logger-delegate-factory-class-name",
      "io.vertx.core.logging.SLF4JLogDelegateFactory");
  }
}
