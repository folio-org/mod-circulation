package org.folio.circulation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.folio.circulation.support.VertxAssistant;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.Test;

class LauncherTest {
  @Test
  void failOnWrongKafkaConfig() {
    var port = NetworkUtils.nextFreePort();
    var future = new Launcher(new VertxAssistant()).start(port);
    var e = assertThrows(Exception.class, future::get);
    assertThat(e.getMessage(), containsString("Kafka"));
  }
}
