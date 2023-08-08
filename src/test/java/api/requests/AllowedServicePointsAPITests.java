package api.requests;

import static api.support.http.InterfaceUrls.allowedServicePointsUrl;
import static api.support.http.api.support.NamedQueryStringParameter.namedParameter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.folio.circulation.domain.RequestLevel;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import api.support.APITests;
import api.support.fixtures.policies.PoliciesToActivate;
import api.support.http.IndividualResource;
import api.support.http.QueryStringParameter;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class AllowedServicePointsAPITests extends APITests {

  @BeforeEach
  void cleanUp() {
    servicePointsFixture.cleanUp();
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
  @MethodSource("parameters")
  void shouldReturnListOfAllowedServicePointsForRequest(RequestType requestType,
    RequestLevel requestLevel) {

    var requesterId = usersFixture.steve().getId().toString();
    var itemId = itemsFixture.basedUponNod().getId().toString();
    var instanceId = itemsFixture.createMultipleItemsForTheSameInstance(2).get(0)
      .getInstanceId().toString();
    var cd1 = servicePointsFixture.cd1();
    var cd2 = servicePointsFixture.cd2();
    setRequestPolicyWithAllowedServicePoints(requestType, cd1.getId(), cd2.getId());

    var response = requestLevel == RequestLevel.TITLE
      ? get(requesterId, instanceId, null, HttpStatus.SC_OK).getJson()
      : get(requesterId, null, itemId, HttpStatus.SC_OK).getJson();

    var allowedServicePoints = response.getJsonArray(requestType.getValue()).stream()
      .map(JsonObject.class::cast).toList();
    assertThat(allowedServicePoints, hasSize(2));
  }

  @ParameterizedTest
  @MethodSource("parameters")
  void shouldReturnOnlyExistingAllowedServicePointForRequest(RequestType requestType,
    RequestLevel requestLevel) {

    var requesterId = usersFixture.steve().getId().toString();
    var itemId = itemsFixture.basedUponNod().getId().toString();
    var instanceId = itemsFixture.createMultipleItemsForTheSameInstance(2).get(0)
      .getInstanceId().toString();
    var cd1 = servicePointsFixture.cd1();
    var cd2Id = UUID.randomUUID();
    setRequestPolicyWithAllowedServicePoints(requestType, cd1.getId(), cd2Id);

    var response = requestLevel == RequestLevel.TITLE
      ? get(requesterId, instanceId, null, HttpStatus.SC_OK).getJson()
      : get(requesterId, null, itemId, HttpStatus.SC_OK).getJson();

    var allowedServicePoints = response.getJsonArray(requestType.getValue());
    assertThat(allowedServicePoints.stream().toList(), hasSize(1));
    assertServicePointsMatch(allowedServicePoints, List.of(cd1));
  }

  @ParameterizedTest
  @MethodSource("parameters")
  void shouldReturnNoAllowedServicePointsIfAllowedServicePointDoesNotExist(
    RequestType requestType, RequestLevel requestLevel) {

    var requesterId = usersFixture.steve().getId().toString();
    var itemId = itemsFixture.basedUponNod().getId().toString();
    var instanceId = itemsFixture.createMultipleItemsForTheSameInstance(2).get(0)
      .getInstanceId().toString();
    setRequestPolicyWithAllowedServicePoints(requestType, UUID.randomUUID());

    var response = requestLevel == RequestLevel.TITLE
      ? get(requesterId, instanceId, null, HttpStatus.SC_OK).getJson()
      : get(requesterId, null, itemId, HttpStatus.SC_OK).getJson();
    var allowedServicePoints = response.getJsonArray(requestType.getValue());
    assertThat(allowedServicePoints, nullValue());
  }

  @ParameterizedTest
  @MethodSource("parameters")
  void shouldReturnNoAllowedServicePointsIfAllowedServicePointIsNotPickupLocation(
    RequestType requestType, RequestLevel requestLevel) {

    var requesterId = usersFixture.steve().getId().toString();
    var itemId = itemsFixture.basedUponNod().getId().toString();
    var instanceId = itemsFixture.createMultipleItemsForTheSameInstance(2).get(0)
      .getInstanceId().toString();
    var servicePointWithNoPickupLocationId = servicePointsFixture.cd3().getId();
    setRequestPolicyWithAllowedServicePoints(requestType, servicePointWithNoPickupLocationId);

    var response = requestLevel == RequestLevel.TITLE
      ? get(requesterId, instanceId, null, HttpStatus.SC_OK).getJson()
      : get(requesterId, null, itemId, HttpStatus.SC_OK).getJson();
    var allowedServicePoints = response.getJsonArray(requestType.getValue());
    assertThat(allowedServicePoints, nullValue());
  }

  @ParameterizedTest
  @MethodSource("parameters")
  void shouldReturnOnlyExistingServicePointsWhenRequestPolicyDoesNotHaveAny(
    RequestType requestType, RequestLevel requestLevel) {

    var requesterId = usersFixture.steve().getId().toString();
    var itemId = itemsFixture.basedUponNod().getId().toString();
    var instanceId = itemsFixture.createMultipleItemsForTheSameInstance(2).get(0)
      .getInstanceId().toString();
    var response = requestLevel == RequestLevel.TITLE
      ? get(requesterId, instanceId, null, HttpStatus.SC_OK).getJson()
      : get(requesterId, null, itemId, HttpStatus.SC_OK).getJson();

    var allowedServicePoints = response.getJsonArray(requestType.getValue());
    var servicePointsWithPickupLocation = servicePointsFixture.getAllServicePoints().stream()
      .filter(sp -> "true".equals(sp.getJson().getString("pickupLocation")))
      .toList();

    assertThat(allowedServicePoints.stream().toList(), hasSize(servicePointsWithPickupLocation.size()));
    assertServicePointsMatch(allowedServicePoints, servicePointsWithPickupLocation);
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

  @Test
  void shouldReturnErrorIfInstanceDoesNotExist() {
    var requesterId = usersFixture.steve().getId().toString();
    var instanceId = randomId();
    var cd1Id = servicePointsFixture.cd1().getId();
    setRequestPolicyWithAllowedServicePoints(RequestType.PAGE, cd1Id);

    Response response = get(requesterId, instanceId, null, HttpStatus.SC_UNPROCESSABLE_ENTITY);
    assertThat(response.getBody(), containsString("There are no holdings for this instance"));
  }

  @ParameterizedTest
  @EnumSource(value = RequestLevel.class, names = {"ITEM", "TITLE"})
  void shouldReturnListOfAllowedServicePointsForBothTypesOfRequest(RequestLevel requestLevel) {
    var requesterId = usersFixture.steve().getId().toString();
    var itemId = itemsFixture.basedUponNod().getId().toString();
    var instanceId = itemsFixture.createMultipleItemsForTheSameInstance(2).get(0)
      .getInstanceId().toString();
    var cd1 = servicePointsFixture.cd1();
    var cd2 = servicePointsFixture.cd2();
    var cd4 = servicePointsFixture.cd4();
    var cd5 = servicePointsFixture.cd5();
    final Map<RequestType, Set<UUID>> allowedServicePointsInPolicy = new HashMap<>();
    allowedServicePointsInPolicy.put(RequestType.PAGE, Set.of(cd1.getId(), cd2.getId()));
    allowedServicePointsInPolicy.put(RequestType.HOLD, Set.of(cd4.getId(), cd5.getId()));
    var requestPolicy = requestPoliciesFixture
      .createRequestPolicyWithAllowedServicePoints(allowedServicePointsInPolicy,
        RequestType.PAGE, RequestType.HOLD);
    policiesActivation.use(PoliciesToActivate.builder().requestPolicy(requestPolicy));

    var response = requestLevel == RequestLevel.TITLE
      ? get(requesterId, instanceId, null, HttpStatus.SC_OK).getJson()
      : get(requesterId, null, itemId, HttpStatus.SC_OK).getJson();

    final JsonArray allowedPageServicePoints = response.getJsonArray(RequestType.PAGE.getValue());
    final JsonArray allowedHoldServicePoints = response.getJsonArray(RequestType.HOLD.getValue());

    assertServicePointsMatch(allowedPageServicePoints, List.of(cd1, cd2));
    assertServicePointsMatch(allowedHoldServicePoints, List.of(cd4, cd5));
  }

  private void assertServicePointsMatch(JsonArray response,
    List<IndividualResource> expectedServicePoints) {

    final List<String> expectedIds = expectedServicePoints.stream()
      .map(IndividualResource::getId)
      .map(UUID::toString)
      .toList();

    final List<String> servicePointIds = response.stream()
      .map(JsonObject.class::cast)
      .map(sp -> sp.getString("id"))
      .collect(Collectors.toList());

    final List<String> servicePointNames = response.stream()
      .map(JsonObject.class::cast)
      .map(allowedSp -> allowedSp.getString("name"))
      .collect(Collectors.toList());

    assertThat(servicePointIds, containsInAnyOrder(expectedIds.toArray(String[]::new)));
    assertThat(servicePointNames, containsInAnyOrder(expectedServicePoints.stream()
      .map(sp -> sp.getJson().getString("name")).toArray(String[]::new)));
  }

  public static Object[] parameters() {
    return new Object[][]{
      {RequestType.PAGE, RequestLevel.ITEM},
      {RequestType.HOLD, RequestLevel.ITEM},
      {RequestType.RECALL, RequestLevel.ITEM},
      {RequestType.PAGE, RequestLevel.TITLE},
      {RequestType.HOLD, RequestLevel.TITLE},
      {RequestType.RECALL, RequestLevel.TITLE},
    };
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
