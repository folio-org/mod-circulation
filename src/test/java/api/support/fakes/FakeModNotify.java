package api.support.fakes;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_OK;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import lombok.Setter;

public class FakeModNotify {

  private static final List<JsonObject> sentPatronNotices = new ArrayList<>();

  @Setter
  private static boolean failPatronNoticesWithBadRequest;

  public static void register(Router router) {
    router.post("/patron-notice")
      .handler(routingContext -> {
        if (failPatronNoticesWithBadRequest) {
          Buffer buffer = Buffer.buffer(
            "Bad request error message", "UTF-8");
          routingContext.response()
            .setStatusCode(HTTP_BAD_REQUEST.toInt())
            .putHeader("content-type", "text/plain; charset=utf-8")
            .putHeader("content-length", Integer.toString(buffer.length()))
            .write(buffer);
          routingContext.response().end();
        }
        else {
          sentPatronNotices.add(routingContext.body().asJsonObject());
          routingContext.response()
            .setStatusCode(HTTP_OK.toInt())
            .end();
        }
      });
  }

  public static void clearSentPatronNotices() {
    sentPatronNotices.clear();
  }

  public static JsonObject getFirstSentPatronNotice() {
    return sentPatronNotices.stream()
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("No notices were sent"));
  }

  // do not replace with lombok: static import of generated getter breaks compilation
  public static List<JsonObject> getSentPatronNotices() {
    return sentPatronNotices;
  }

}
