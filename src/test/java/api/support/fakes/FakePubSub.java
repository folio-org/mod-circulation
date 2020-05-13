package api.support.fakes;

import java.util.ArrayList;
import java.util.List;

import org.folio.HttpStatus;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

public class FakePubSub {

  private static final List<JsonObject> publishedEvents = new ArrayList<>();

  public static void register(Router router) {
    router.route().handler(BodyHandler.create());
    router.post("/pubsub/publish")
      .handler(routingContext -> {
        publishedEvents.add(routingContext.getBodyAsJson());
        routingContext.response()
          .setStatusCode(HttpStatus.HTTP_NO_CONTENT.toInt())
          .end();
      });
  }

  public static List<JsonObject> getPublishedEvents() {
    return publishedEvents;
  }
}
