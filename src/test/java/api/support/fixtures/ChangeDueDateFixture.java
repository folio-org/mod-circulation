package api.support.fixtures;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.changeDueDateURL;

import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;
import api.support.builders.ChangeDueDateRequestBuilder;

public class ChangeDueDateFixture {

  private final RestAssuredClient restAssuredClient;

  public ChangeDueDateFixture() {
    restAssuredClient = new RestAssuredClient(getOkapiHeadersFromContext());
  }

  public Response changeDueDate(
    ChangeDueDateRequestBuilder request) {

    return restAssuredClient.post(request.create(),
      changeDueDateURL(request.getLoanId()), 204, "change-due-date-request");
  }

  public Response attemptChangeDueDate(
    ChangeDueDateRequestBuilder request) {

    return restAssuredClient.post(request.create(),
        changeDueDateURL(request.getLoanId()), "change-due-date-request");
  }

}
