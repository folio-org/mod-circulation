package org.folio.circulation.support.kafka;

import lombok.experimental.UtilityClass;

@UtilityClass
public class KafkaConfigConstants {

  public static final String KAFKA_HOST = "KAFKA_HOST";
  public static final String KAFKA_PORT = "KAFKA_PORT";
  public static final String OKAPI_URL = "OKAPI_URL";
  public static final String DEFAULT_OKAPI_URL = "http://okapi:9130";
  public static final String KAFKA_REPLICATION_FACTOR = "REPLICATION_FACTOR";
  public static final String KAFKA_ENV = "ENV";
  public static final String KAFKA_MAX_REQUEST_SIZE = "MAX_REQUEST_SIZE";
}
