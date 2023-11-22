package org.folio.circulation;

import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.folio.circulation.support.VertxAssistant;
import org.folio.rest.tools.utils.NetworkUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class LauncherTest {

  @Test
  void test() {
    VertxAssistant vertxAssistant = new VertxAssistant();
    new Launcher(vertxAssistant).start(NetworkUtils.nextFreePort());

    Awaitility.await()
      .atMost(10, TimeUnit.MINUTES)
      .until(()-> vertxAssistant.getDeploymentIds().size(), Matchers.is(2));
  }
}