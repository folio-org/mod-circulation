package api.support.http;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getOffsetDateTimeProperty;

import java.time.OffsetDateTime;

import org.folio.circulation.support.http.client.Response;

public class CheckOutResource extends IndividualResource {
  public CheckOutResource(Response response) {
    super(response);
  }

  public OffsetDateTime getDueDate() {
    return getOffsetDateTimeProperty(response.getJson(), "dueDate");
  }
}
