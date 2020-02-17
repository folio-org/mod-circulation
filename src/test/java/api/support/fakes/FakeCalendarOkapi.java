package api.support.fakes;

import static api.support.fixtures.CalendarExamples.CASE_CALENDAR_IS_EMPTY_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.getCalendarById;
import static api.support.fixtures.LibraryHoursExamples.CASE_CALENDAR_IS_UNAVAILABLE_SERVICE_POINT_ID;
import static api.support.fixtures.LibraryHoursExamples.CASE_CLOSED_LIBRARY_IN_THU_SERVICE_POINT_ID;
import static api.support.fixtures.LibraryHoursExamples.CASE_CLOSED_LIBRARY_SERVICE_POINT_ID;
import static api.support.fixtures.LibraryHoursExamples.getLibraryHoursById;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import api.support.fixtures.OpeningPeriodsExamples;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.Router;

public class FakeCalendarOkapi {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static void registerOpeningHours(Router router) {
    router.get("/calendar/periods")
      .handler(routingContext -> {
        routingContext.response()
          .setStatusCode(200)
          .putHeader("content-type", "application/json")
          .end(OpeningPeriodsExamples.oneDayPeriod().create().toString());
      });
  }

  public static void registerLibraryHours(Router router) {
    router.get("/calendar/periods/:id/period")
      .handler(routingContext -> {
        String servicePointId = routingContext.pathParam("id");
        switch (servicePointId) {
          case CASE_CALENDAR_IS_UNAVAILABLE_SERVICE_POINT_ID:
            routingContext.response()
              .putHeader("content-type", "application/json")
              .setStatusCode(404)
              .end();
            break;

          case CASE_CLOSED_LIBRARY_SERVICE_POINT_ID:
            routingContext.response()
              .setStatusCode(200)
              .putHeader("content-type", "application/json")
              .end(findFakeLibraryHoursById(servicePointId));
            break;

          case CASE_CLOSED_LIBRARY_IN_THU_SERVICE_POINT_ID:
            routingContext.response()
              .setStatusCode(200)
              .putHeader("content-type", "application/json")
              .end(findFakeLibraryHoursById(servicePointId));
            break;

          default:
            routingContext.response()
              .setStatusCode(200)
              .putHeader("content-type", "application/json")
              .end(findFakeLibraryHoursById(servicePointId));
        }
      });
  }

  public static void registerCalendar(Router router) {
    router.get("/calendar/periods/:id/calculateopening")
      .handler(routingContext -> {
        String servicePointId = routingContext.pathParam("id");
        switch (servicePointId) {
          case CASE_CALENDAR_IS_UNAVAILABLE_SERVICE_POINT_ID:
            routingContext.response()
              .putHeader("content-type", "application/json")
              .setStatusCode(404)
              .end();
            break;

          case CASE_CLOSED_LIBRARY_SERVICE_POINT_ID:
            routingContext.response()
              .putHeader("content-type", "application/json")
              .setStatusCode(404)
              .end();
            break;

          case CASE_CALENDAR_IS_EMPTY_SERVICE_POINT_ID:
            routingContext.response()
              .putHeader("content-type", "application/json")
              .setStatusCode(200)
              .end();
            break;

          case CASE_CLOSED_LIBRARY_IN_THU_SERVICE_POINT_ID:
            routingContext.response()
              .putHeader("content-type", "application/json")
              .setStatusCode(404)
              .end();
            break;

          default:
            MultiMap queries = routingContext.queryParams();
            routingContext.response()
              .setStatusCode(200)
              .putHeader("content-type", "application/json")
              .end(findFakeCalendarById(servicePointId, queries));
        }
      });
  }

  private static String findFakeLibraryHoursById(String servicePointId) {
    log.debug(String.format("GET: /calendar/periods/%s/period", servicePointId));
    return getLibraryHoursById(servicePointId).toString();
  }

  private static String findFakeCalendarById(String servicePointId, MultiMap queries) {
    log.debug(String.format("GET: /calendar/periods/%s/calculateopening, queries=%s",
      servicePointId, queries));
    return getCalendarById(servicePointId, queries).toString();
  }
}
