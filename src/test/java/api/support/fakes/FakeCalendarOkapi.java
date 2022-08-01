package api.support.fakes;

import static api.support.fixtures.CalendarExamples.CASE_CALENDAR_IS_EMPTY_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.getCalendarById;
import static api.support.fixtures.LibraryHoursExamples.CASE_CALENDAR_IS_UNAVAILABLE_SERVICE_POINT_ID;
import static api.support.fixtures.LibraryHoursExamples.CASE_CLOSED_LIBRARY_IN_THU_SERVICE_POINT_ID;
import static api.support.fixtures.LibraryHoursExamples.CASE_CLOSED_LIBRARY_SERVICE_POINT_ID;

import api.support.fixtures.OpeningDayCollectionExamples;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FakeCalendarOkapi {

  private static final Logger log = LogManager.getLogger(
    MethodHandles.lookup().lookupClass()
  );

  public static void registerCalendarAllDates(Router router) {
    router
      .get("/calendar/dates/:id/all-openings")
      .handler(
        routingContext -> {
          routingContext
            .response()
            .setStatusCode(200)
            .putHeader("content-type", "application/json")
            .end(
              OpeningDayCollectionExamples.oneDayPeriod().create().toString()
            );
        }
      );
  }

  public static void registerCalendarSurroundingDates(Router router) {
    router
      .get("/calendar/dates/:id/surrounding-openings")
      .handler(
        routingContext -> {
          String servicePointId = routingContext.pathParam("id");
          switch (servicePointId) {
            case CASE_CALENDAR_IS_UNAVAILABLE_SERVICE_POINT_ID:
              routingContext
                .response()
                .putHeader("content-type", "application/json")
                .setStatusCode(404)
                .end();
              break;
            case CASE_CLOSED_LIBRARY_SERVICE_POINT_ID:
              routingContext
                .response()
                .putHeader("content-type", "application/json")
                .setStatusCode(404)
                .end();
              break;
            case CASE_CALENDAR_IS_EMPTY_SERVICE_POINT_ID:
              routingContext
                .response()
                .putHeader("content-type", "application/json")
                .setStatusCode(200)
                .end();
              break;
            case CASE_CLOSED_LIBRARY_IN_THU_SERVICE_POINT_ID:
              routingContext
                .response()
                .putHeader("content-type", "application/json")
                .setStatusCode(404)
                .end();
              break;
            default:
              MultiMap queries = routingContext.queryParams();
              routingContext
                .response()
                .setStatusCode(200)
                .putHeader("content-type", "application/json")
                .end(findFakeCalendarById(servicePointId, queries));
          }
        }
      );
  }

  private static String findFakeCalendarById(
    String servicePointId,
    MultiMap queries
  ) {
    log.info(
      String.format(
        "GET: /calendar/dates/%s/surrounding-openings, queries=%s",
        servicePointId,
        queries
      )
    );
    // repackage openingDays property into new object as {openings: ...}
    return new JsonObject(
      Map.of(
        "openings",
        getCalendarById(servicePointId, queries)
          .create()
          .getJsonArray("openingDays")
      )
    )
      .toString();
  }
}
