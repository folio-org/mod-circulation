package api.support.fixtures;

import api.support.builders.CalendarBuilder;
import api.support.http.ResourceClient;
import org.folio.circulation.support.http.client.IndividualResource;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.support.fixtures.CalendarExamples.CASE_1_SERVICE_POINT_ID;

public class CalendarFixture {

  private final ResourceClient requestsClient;

  public CalendarFixture(ResourceClient requestsClient) {
    this.requestsClient = requestsClient;
  }

  private IndividualResource place(CalendarBuilder builder)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return requestsClient.create(builder);
  }

  public IndividualResource gerCaseCalendar()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    return place(CalendarExamples.getCalendarById(CASE_1_SERVICE_POINT_ID));
  }

}
