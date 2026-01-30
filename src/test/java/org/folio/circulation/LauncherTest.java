package org.folio.circulation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    System.setProperty("KAFKA_PORT", "1");
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
    var e = assertThrows(Exception.class, future::get);
    assertThat(e.getMessage(), containsString("Kafka"));
  }
}
