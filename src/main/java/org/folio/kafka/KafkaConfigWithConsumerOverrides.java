package org.folio.kafka;

import static java.util.Map.copyOf;

import java.util.Map;

public final class KafkaConfigWithConsumerOverrides extends KafkaConfig {
  private final Map<String, String> consumerOverrides;

  public KafkaConfigWithConsumerOverrides(KafkaConfig kafkaConfig,
    Map<String, String> consumerOverrides) {

    super(kafkaConfig.getKafkaHost(), kafkaConfig.getKafkaPort(), kafkaConfig.getOkapiUrl(),
      kafkaConfig.getReplicationFactor(), kafkaConfig.getEnvId(), kafkaConfig.getMaxRequestSize(),
      kafkaConfig.getConsumerKeyDeserializerClass(),
      kafkaConfig.getConsumerValueDeserializerClass());

    this.consumerOverrides = copyOf(consumerOverrides);
  }

  @Override
  public Map<String, String> getConsumerProps() {
    Map<String, String> consumerProps = super.getConsumerProps();
    consumerProps.putAll(consumerOverrides);
    return consumerProps;
  }
}
