package api.support.fakes;

import java.util.ArrayList;
import java.util.List;

import org.folio.HttpStatus;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class FakePubSub {
  private static final Logger logger = LoggerFactory.getLogger(FakePubSub.class);
  private static final List<JsonObject> publishedEvents = new ArrayList<>();

  private static boolean failPubSubRegistration;

  public static void register(Router router) {
    router.route().handler(BodyHandler.create());

    router.post("/pubsub/publish")
      .handler(routingContext -> {
        publishedEvents.add(routingContext.getBodyAsJson());
        routingContext.response()
          .setStatusCode(HttpStatus.HTTP_NO_CONTENT.toInt())
          .end();
      });

    router.post("/pubsub/event-types")
      .handler(FakePubSub::respondCreatedOrFail);;

    router.post("/pubsub/event-types/declare/publisher")
      .handler(FakePubSub::respondCreatedOrFail);

    router.post("/pubsub/event-types/declare/subscriber")
      .handler(FakePubSub::respondCreatedOrFail);
  }

  private static void respondCreatedOrFail(RoutingContext routingContext) {
    if (failPubSubRegistration) {
      routingContext.response()
        .setStatusCode(HttpStatus.HTTP_INTERNAL_SERVER_ERROR.toInt())
        .end();
    }
    else {
      String json = routingContext.getBodyAsJson().encodePrettily();
      Buffer buffer = Buffer.buffer(json, "UTF-8");
      routingContext.response()
        .setStatusCode(HttpStatus.HTTP_CREATED.toInt())
        .putHeader("content-type", "application/json; charset=utf-8")
        .putHeader("content-length", Integer.toString(buffer.length()))
        .write(buffer)
        .end();
    }
  }

  public static List<JsonObject> getPublishedEvents() {
    return publishedEvents;
  }

  public static void cleanUp() {
    publishedEvents.clear();
  }

  public static void setFailPubSubRegistration(boolean failPubSubRegistration) {
    FakePubSub.failPubSubRegistration = failPubSubRegistration;
  }
}
