package org.folio.circulation.services.events;

import static org.folio.circulation.domain.EventType.FEE_FINE_BALANCE_CHANGED;

import io.vertx.core.Context;
import io.vertx.core.http.HttpClient;

public class FeeFineBalanceChangedKafkaEventHandler extends AbstractKafkaEventHandler {

  public FeeFineBalanceChangedKafkaEventHandler(Context vertxContext, HttpClient client,
    String defaultGatewayUrl) {

    super(vertxContext, client, defaultGatewayUrl, FEE_FINE_BALANCE_CHANGED,
      new FeeFineBalanceChangedEventProcessor());
  }
}
