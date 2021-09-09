package api.support.http;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;

import java.time.ZonedDateTime;

import org.folio.circulation.support.http.client.Response;

public class CheckOutResource extends IndividualResource {
  public CheckOutResource(Response response) {
    super(response);
  }

  public ZonedDateTime getDueDate() {
    return getDateTimeProperty(response.getJson(), "dueDate");
  }
}
