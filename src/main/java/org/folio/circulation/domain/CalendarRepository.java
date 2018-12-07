package org.folio.circulation.domain;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FetchSingleRecord;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.HttpResult.failed;

public class CalendarRepository {

  private static final String PATH_QUERY = "%s/period";
  private static final String RECORD_NAME = "openingPeriods";
  private static final String SERVICE_ID = "serviceId";
  private static final String ERROR_MESSAGE = "There is no entry in the calendar for serviceId";

  private final CollectionResourceClient resourceClient;

  public CalendarRepository(Clients clients) {
    this.resourceClient = clients.calendarStorageClient();
  }

  public CompletableFuture<HttpResult<Calendar>> getCalendar(String id) {
    String serviceId = String.format(PATH_QUERY, id);
    return FetchSingleRecord.<Calendar>forRecord(RECORD_NAME)
      .using(resourceClient)
      .mapTo(Calendar::new)
      .whenNotFound(
        failed(getErrorFailure(serviceId)))
      .fetch(serviceId);
  }

  private ValidationErrorFailure getErrorFailure(String serviceId) {
    ValidationError error = new ValidationError(ERROR_MESSAGE, SERVICE_ID, serviceId);
    return new ValidationErrorFailure(error);
  }
}
