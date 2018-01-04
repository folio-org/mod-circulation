package org.folio.circulation;

public class Logging {
  private Logging() { }


  static void initialiseFormat() {
    System.setProperty("vertx.logger-delegate-factory-class-name",
      "io.vertx.core.logging.SLF4JLogDelegateFactory");
  }
}
