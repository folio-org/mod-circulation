package api.requests;

import static api.support.http.InterfaceUrls.allowedServicePointsUrl;
import static api.support.http.api.support.NamedQueryStringParameter.namedParameter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import api.support.APITests;
import api.support.fixtures.policies.PoliciesToActivate;
import api.support.http.QueryStringParameter;

class AllowedServicePointsAPITests extends APITests {

  @BeforeEach
  void cleanUp() {
    servicePointsFixture.cleanUp();
  }

  @ParameterizedTest
  @CsvSource(value = {
    "9fdf7408-b8cf-43c1-ae1e-58b7a609b86c, 7341b1d6-a6a7-41ec-8653-00b819a70a30, NULL",
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

  @ParameterizedTest
  @EnumSource(
    value = RequestType.class,
    names = {"NONE"},
    mode = EnumSource.Mode.EXCLUDE
  )
  void shouldReturnListOfAllowedServicePointsForItemLevelRequest(RequestType requestType) {
    var requesterId = usersFixture.steve().getId().toString();
    var itemId = itemsFixture.basedUponNod().getId().toString();
    var cd1Id = servicePointsFixture.cd1().getId();
    var cd2Id = servicePointsFixture.cd2().getId();
    setRequestPolicyWithAllowedServicePoints(requestType, cd1Id, cd2Id);

    var response = get(requesterId, null, itemId, HttpStatus.SC_OK).getJson();
    var allowedServicePoints = response.getJsonArray(requestType.getValue()).stream().toList();
    assertThat(allowedServicePoints, hasSize(2));
    assertThat(allowedServicePoints, hasItems(cd1Id.toString(), cd2Id.toString()));
  }

  @ParameterizedTest
  @EnumSource(
    value = RequestType.class,
    names = {"NONE"},
    mode = EnumSource.Mode.EXCLUDE
  )
  void shouldReturnOnlyExistingAllowedServicePointForItemLevelRequest(RequestType requestType) {
    var requesterId = usersFixture.steve().getId().toString();
    var itemId = itemsFixture.basedUponNod().getId().toString();
    var cd1Id = servicePointsFixture.cd1().getId();
    var cd2Id = UUID.randomUUID();
    setRequestPolicyWithAllowedServicePoints(requestType, cd1Id, cd2Id);

    var response = get(requesterId, null, itemId, HttpStatus.SC_OK).getJson();
    var allowedServicePoints = response.getJsonArray(requestType.getValue()).stream().toList();
    assertThat(allowedServicePoints, hasSize(1));
    assertThat(allowedServicePoints, hasItem(cd1Id.toString()));
  }

  @ParameterizedTest
  @EnumSource(
    value = RequestType.class,
    names = {"NONE"},
    mode = EnumSource.Mode.EXCLUDE
  )
  void shouldReturnNoAllowedServicePointsIfAllowedServicePointDoesNotExist(
    RequestType requestType) {

    var requesterId = usersFixture.steve().getId().toString();
    var itemId = itemsFixture.basedUponNod().getId().toString();
    setRequestPolicyWithAllowedServicePoints(requestType, UUID.randomUUID());

    var response = get(requesterId, null, itemId, HttpStatus.SC_OK).getJson();
    var allowedServicePoints = response.getJsonArray(requestType.getValue());
    assertThat(allowedServicePoints, nullValue());
  }

  @ParameterizedTest
  @EnumSource(
    value = RequestType.class,
    names = {"NONE"},
    mode = EnumSource.Mode.EXCLUDE
  )
  void shouldReturnNoAllowedServicePointsIfAllowedServicePointIsNotPickupLocation(
    RequestType requestType) {

    var requesterId = usersFixture.steve().getId().toString();
    var itemId = itemsFixture.basedUponNod().getId().toString();
    var servicePointWithNoPickupLocationId = servicePointsFixture.cd3().getId();
    setRequestPolicyWithAllowedServicePoints(requestType, servicePointWithNoPickupLocationId);

    var response = get(requesterId, null, itemId, HttpStatus.SC_OK).getJson();
    var allowedServicePoints = response.getJsonArray(requestType.getValue());
    assertThat(allowedServicePoints, nullValue());
  }

  @ParameterizedTest
  @EnumSource(
    value = RequestType.class,
    names = {"NONE"},
    mode = EnumSource.Mode.EXCLUDE
  )
  void shouldReturnOnlyExistingServicePointsWhenRequestPolicyDoesNotHaveAny(
    RequestType requestType) {

    var requesterId = usersFixture.steve().getId().toString();
    var itemId = itemsFixture.basedUponNod().getId().toString();

    var response = get(requesterId, null, itemId, HttpStatus.SC_OK).getJson();
    var allowedServicePoints = response.getJsonArray(requestType.getValue()).stream().toList();
    var servicePointsWithPickupLocation = servicePointsFixture.getAllServicePoints().stream()
      .filter(sp -> "true".equals(sp.getJson().getString("pickupLocation")))
      .toList();

    assertThat(allowedServicePoints, hasSize(servicePointsWithPickupLocation.size()));
    assertThat(allowedServicePoints.get(0), is(servicePointsWithPickupLocation.get(0).getId().toString()));
  }

  @Test
  void shouldReturnErrorIfRequesterDoesNotExist() {
    var requesterId = randomId();
    var itemId = itemsFixture.basedUponNod().getId().toString();
    var cd1Id = servicePointsFixture.cd1().getId();
    setRequestPolicyWithAllowedServicePoints(RequestType.PAGE, cd1Id);

    Response response = get(requesterId, null, itemId, HttpStatus.SC_UNPROCESSABLE_ENTITY);
    assertThat(response.getBody(), containsString("User with id=" + requesterId +
      " cannot be found"));
  }

  @Test
  void shouldReturnErrorIfItemDoesNotExist() {
    var requesterId = usersFixture.steve().getId().toString();
    var itemId = randomId();
    var cd1Id = servicePointsFixture.cd1().getId();
    setRequestPolicyWithAllowedServicePoints(RequestType.PAGE, cd1Id);

    Response response = get(requesterId, null, itemId, HttpStatus.SC_UNPROCESSABLE_ENTITY);
    assertThat(response.getBody(), containsString("Item with id=" + itemId +
      " cannot be found"));
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

  private UUID setRequestPolicyWithAllowedServicePoints(RequestType requestType,
    UUID... requestPickupSpIds) {

    final Map<RequestType, Set<UUID>> allowedServicePoints = new HashMap<>();
    allowedServicePoints.put(requestType, Set.of(requestPickupSpIds));
    var requestPolicy = requestPoliciesFixture
      .createRequestPolicyWithAllowedServicePoints(allowedServicePoints, requestType);
    policiesActivation.use(PoliciesToActivate.builder().requestPolicy(requestPolicy));

    return requestPolicy.getId();
  }
}
