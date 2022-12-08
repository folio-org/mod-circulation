package org.folio.circulation.services;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.circulation.CirculationVerticle;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class HealthServiceTest {

  @Test
  void health(Vertx vertx, VertxTestContext vtc) {
    var port = NetworkUtils.nextFreePort();
    var options = new DeploymentOptions().setConfig(new JsonObject().put("port", port));
    vertx.deployVerticle(new CirculationVerticle(), options)
    .compose(x -> WebClient.create(vertx).getAbs("http://localhost:" + port + "/admin/health").send())
    .onComplete(vtc.succeeding(httpResponse -> {
      assertThat(httpResponse.statusCode(), is(200));
      assertThat(httpResponse.bodyAsString(), is("OK"));
      vtc.completeNow();
    }));
  }
}
