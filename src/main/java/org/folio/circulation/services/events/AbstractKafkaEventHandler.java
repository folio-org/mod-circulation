package org.folio.circulation.services.events;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.circulation.services.events.KafkaRecordHeaders.headersFrom;

import org.folio.circulation.domain.EventType;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.kafka.AsyncRecordHandler;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.extern.log4j.Log4j2;

@Log4j2
abstract class AbstractKafkaEventHandler implements AsyncRecordHandler<String, String> {
  private final Context vertxContext;
  private final HttpClient client;
  private final String defaultGatewayUrl;
  private final EventType eventType;
  private final KafkaEventProcessor processor;

  protected AbstractKafkaEventHandler(Context vertxContext, HttpClient client,
    String defaultGatewayUrl, EventType eventType, KafkaEventProcessor processor) {

    this.vertxContext = vertxContext;
    this.client = client;
    this.defaultGatewayUrl = defaultGatewayUrl;
    this.eventType = eventType;
    this.processor = processor;
  }

  @Override
  public Future<String> handle(KafkaConsumerRecord<String, String> consumerRecord) {
    try {
      String eventKey = consumerRecord.key();
      log.info("handle:: {} event received: key={}", eventType, eventKey);

      WebContext context = new WebContext(headersFrom(consumerRecord, defaultGatewayUrl),
        vertxContext);
      return Future.fromCompletionStage(processor.process(new JsonObject(consumerRecord.value()),
          context, client))
        .compose(result -> result.succeeded()
          ? succeededFuture(eventKey)
          : failedFuture(result.cause().toString()));
    } catch (Exception e) {
      log.error("handle:: failed to process {} event", eventType, e);
      return failedFuture(e);
    }
  }
}
