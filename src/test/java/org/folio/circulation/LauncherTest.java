package org.folio.circulation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.ExecutionException;

import org.apache.kafka.common.KafkaException;
import org.folio.circulation.support.VertxAssistant;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LauncherTest {
  private static String oldKafkaPort;

  @BeforeAll
  static void beforeAll() {
    oldKafkaPort = System.getProperty("KAFKA_PORT");
    System.setProperty("KAFKA_PORT", "x");
  }

  @AfterAll
  static void afterAll() {
    if (oldKafkaPort == null) {
      System.clearProperty("KAFKA_PORT");
    } else {
      System.setProperty("KAFKA_PORT", oldKafkaPort);
    }
  }

  @Test
  void failOnWrongKafkaConfig() {
    var port = NetworkUtils.nextFreePort();
    var future = new Launcher(new VertxAssistant()).start(port);
    var e = assertThrows(ExecutionException.class, future::get);
    assertThat(e.getCause(), is(instanceOf(KafkaException.class)));
  }
}
