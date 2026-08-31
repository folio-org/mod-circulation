package org.folio.circulation.services.events;

import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;
import static org.folio.kafka.headers.FolioKafkaHeaders.TENANT_ID;

import java.util.HashMap;
import java.util.Map;

import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import lombok.experimental.UtilityClass;

@UtilityClass
class KafkaRecordHeaders {
  static Map<String, String> okapiHeaders(KafkaConsumerRecord<String, String> consumerRecord,
    String defaultOkapiUrl) {

    Map<String, String> headers = new HashMap<>();
    consumerRecord.headers()
      .forEach(header -> headers.put(header.key(), header.value().toString()));

    putIfAbsentIgnoringCase(headers, OKAPI_URL, defaultOkapiUrl);
    putIfAbsentIgnoringCase(headers, TENANT, headers.get(TENANT_ID));

    return headers;
  }

  private static void putIfAbsentIgnoringCase(Map<String, String> headers, String key,
    String value) {

    if (value != null && headers.keySet().stream().noneMatch(key::equalsIgnoreCase)) {
      headers.put(key, value);
    }
  }
}
