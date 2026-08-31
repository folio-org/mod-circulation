package org.folio.circulation.services.events;

import static org.folio.circulation.domain.EventType.LOAN_RELATED_FEE_FINE_CLOSED;

import io.vertx.core.http.HttpClient;

public class LoanRelatedFeeFineClosedKafkaEventHandler extends AbstractKafkaEventHandler {

  public LoanRelatedFeeFineClosedKafkaEventHandler(HttpClient client, String defaultOkapiUrl) {
    super(client, defaultOkapiUrl, LOAN_RELATED_FEE_FINE_CLOSED,
      new LoanRelatedFeeFineClosedEventProcessor());
  }
}
