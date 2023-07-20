package api.requests;

import static api.support.http.InterfaceUrls.allowedServicePointsUrl;
import static api.support.http.api.support.NamedQueryStringParameter.namedParameter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import api.support.APITests;
import api.support.http.QueryStringParameter;

public class AllowedServicePointsAPITests extends APITests {

  @ParameterizedTest
  @CsvSource(value = {
    "9fdf7408-b8cf-43c1-ae1e-58b7a609b86c, 7341b1d6-a6a7-41ec-8653-00b819a70a30, NULL",
    "9fdf7408-b8cf-43c1-ae1e-58b7a609b86c, NULL, 5449cec7-815d-42af-83cd-d406d64c822e",
  }, nullValues={"NULL"})
  void getIsSuccessful(String requesterId, String instanceId, String itemId) {
    Response response = get(requesterId, instanceId, itemId, HttpStatus.SC_OK);
    assertThat(response.getJson(), emptyIterable());
  }

  @Test
  void getFailsWithBadRequestWhenRequesterIdIsNull() {
    Response response = get(null, randomId(), null, HttpStatus.SC_BAD_REQUEST);
    assertThat(response.getBody(), equalTo("Request query parameters must contain 'requester'."));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "9fdf7408-b8cf-43c1-ae1e-58b7a609b86c,  7341b1d6-a6a7-41ec-8653-00b819a70a30, 5449cec7-815d-42af-83cd-d406d64c822e",
    "9fdf7408-b8cf-43c1-ae1e-58b7a609b86c,  NULL,                                 NULL",
  }, nullValues={"NULL"})
  void getFailsWithBadRequestWhenRequestDoesNotContainExactlyTwoParameters(String requesterId,
    String instanceId, String itemId) {

    Response response = get(requesterId, instanceId, itemId, HttpStatus.SC_BAD_REQUEST);
    assertThat(response.getBody(), equalTo("Request query parameters must contain either 'instance' or 'item'."));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "not-a-uuid,                            7341b1d6-a6a7-41ec-8653-00b819a70a30, NULL",
    "9fdf7408-b8cf-43c1-ae1e-58b7a609b86c,  not-a-uuid,                           NULL",
    "9fdf7408-b8cf-43c1-ae1e-58b7a609b86c,  NULL,                                 not-a-uuid",
    "not-a-uuid,                            not-a-uuid,                           not-a-uuid",
  }, nullValues={"NULL"})
  void getFailsWhenRequestContainsInvalidUUID(String requesterId, String instanceId, String itemId) {
    Response response = get(requesterId, instanceId, itemId, HttpStatus.SC_BAD_REQUEST);
    assertThat(response.getBody(), containsString("ID is not a valid UUID"));
  }

  @Test
  void getFailsWithMultipleErrors() {
    Response response = get(null, "instanceId", "itemId", HttpStatus.SC_BAD_REQUEST);
    assertThat(response.getBody(), equalTo("Request query parameters must contain 'requester'. " +
      "Request query parameters must contain either 'instance' or 'item'. " +
      "Instance ID is not a valid UUID: instanceId. Item ID is not a valid UUID: itemId."));
  }

  private Response get(String requesterId, String instanceId, String itemId, int expectedStatusCode) {
    List<QueryStringParameter> queryParams = new ArrayList<>();
    if (requesterId != null) {
      queryParams.add(namedParameter("requester", requesterId));
    }
    if (instanceId != null) {
      queryParams.add(namedParameter("instance", instanceId));
    }
    if (itemId != null) {
      queryParams.add(namedParameter("item", itemId));
    }

    return restAssuredClient.get(allowedServicePointsUrl(), queryParams, expectedStatusCode,
      "allowed-service-points");
  }
}
