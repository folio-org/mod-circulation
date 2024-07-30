package api.support.fakes;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.SneakyThrows;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static api.support.APITestContext.getTenantId;
import static api.support.fakes.Storage.getStorage;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.support.http.server.JsonHttpResponse.ok;

public class FakePrintEventStatusModule {

  @SneakyThrows
  public void register(Router router) {
    router.post("/print-events-storage/print-events-status")
      .handler(this::handlePrintEventStatusRequest);
  }

  private void handlePrintEventStatusRequest(RoutingContext routingContext) {
    var request = routingContext.body().asJsonObject();
    var requestIds = request.getJsonArray("requestIds");
    if (requestIds.isEmpty()) {
      Buffer buffer = Buffer.buffer(
        "size must be between 1 and 2147483647", "UTF-8");
      routingContext.response()
        .setStatusCode(HTTP_UNPROCESSABLE_ENTITY.toInt())
        .putHeader("content-type", "text/plain; charset=utf-8")
        .putHeader("content-length", Integer.toString(buffer.length()))
        .write(buffer);
      routingContext.response().end();
    } else {
      var jsonObjectList = new ArrayList<>(getStorage()
        .getTenantResources("/print-events-storage/print-events-entry", getTenantId())
        .values()
        .stream()
        .toList());
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
      // Sorting jsonObjectList based on PrintEventDate so that it will always return latest printEventDetail
      jsonObjectList.sort((obj1, obj2) -> {
        try {
          Date date1 = dateFormat.parse(obj1.getString("printEventDate"));
          Date date2 = dateFormat.parse(obj2.getString("printEventDate"));
          return date2.compareTo(date1);
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
      });
      Map<String, List<JsonObject>> groupByRequestIdMap = new LinkedHashMap<>();
      requestIds.forEach(requestId -> {
        var requestList = jsonObjectList.stream().filter(jsonObject ->
            jsonObject.getJsonArray("requestIds").contains(requestId))
          .toList();
        groupByRequestIdMap.put((String) requestId, requestList);
      });
      var jsonObjectResponse = new JsonObject();
      var printEventStatusResponses = new ArrayList<>();
      jsonObjectResponse.put("printEventsStatusResponses", printEventStatusResponses);
      requestIds.forEach(id -> {
        var requestDetail = groupByRequestIdMap.get(id);
        if (requestDetail != null && !requestDetail.isEmpty()) {
          var object = new JsonObject()
            .put("requestId", id)
            .put("count", requestDetail.size())
            .put("requesterId", requestDetail.get(0).getString("requesterId"))
            .put("printEventDate", requestDetail.get(0).getString("printEventDate"));
          printEventStatusResponses.add(object);
        }
      });
      ok(jsonObjectResponse).writeTo(routingContext.response());
    }
  }
}
