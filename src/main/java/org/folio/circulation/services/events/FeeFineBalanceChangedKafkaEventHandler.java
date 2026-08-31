package org.folio.circulation.services.events;

import static org.folio.circulation.domain.EventType.FEE_FINE_BALANCE_CHANGED;

import io.vertx.core.http.HttpClient;

public class FeeFineBalanceChangedKafkaEventHandler extends AbstractKafkaEventHandler {

  public FeeFineBalanceChangedKafkaEventHandler(HttpClient client, String defaultOkapiUrl) {
    super(client, defaultOkapiUrl, FEE_FINE_BALANCE_CHANGED,
      new FeeFineBalanceChangedEventProcessor());
  }
}
