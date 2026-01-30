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
    oldKafkaPort = System.getProperty("kafka-port");
    System.clearProperty("kafka-port");
  }

  @AfterAll
  static void afterAll() {
    if (oldKafkaPort != null) {
      System.setProperty("kafka-port", oldKafkaPort);
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
