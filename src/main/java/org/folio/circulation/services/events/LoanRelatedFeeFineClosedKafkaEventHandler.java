package org.folio.circulation.services.events;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.circulation.services.events.KafkaRecordHeaders.okapiHeaders;

import org.folio.circulation.support.http.server.WebContext;
import org.folio.kafka.AsyncRecordHandler;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class LoanRelatedFeeFineClosedKafkaEventHandler implements AsyncRecordHandler<String, String> {
  private final HttpClient client;
  private final String defaultOkapiUrl;
  private final LoanRelatedFeeFineClosedEventProcessor processor =
    new LoanRelatedFeeFineClosedEventProcessor();

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> consumerRecord) {
    try {
      String eventKey = consumerRecord.key();
      log.info("handle:: loan-related fee/fine closed event received: key={}", eventKey);
      log.debug("handle:: value={}", consumerRecord.value());

      WebContext context = new WebContext(okapiHeaders(consumerRecord, defaultOkapiUrl));
      return Future.fromCompletionStage(processor.process(new JsonObject(consumerRecord.value()),
          context, client))
        .compose(result -> result.succeeded()
          ? succeededFuture(eventKey)
          : failedFuture(result.cause().toString()));
    } catch (Exception e) {
      log.error("handle:: failed to process loan-related fee/fine closed event", e);
      return failedFuture(e);
    }
  }
}
