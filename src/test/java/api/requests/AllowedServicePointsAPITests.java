package api.requests;

import static api.support.http.InterfaceUrls.allowedServicePointsUrl;
import static api.support.http.api.support.NamedQueryStringParameter.namedParameter;
import static api.support.matchers.AllowedServicePointsMatchers.allowedServicePointMatcher;
import static api.support.matchers.JsonObjectMatcher.hasNoJsonPath;
import static java.lang.Boolean.TRUE;
import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.RequestLevel.ITEM;
import static org.folio.circulation.domain.RequestLevel.TITLE;
import static org.folio.circulation.domain.RequestType.HOLD;
import static org.folio.circulation.domain.RequestType.PAGE;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.RequestLevel;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.core.IsNull;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import api.support.APITests;
import api.support.builders.ItemBuilder;
import api.support.builders.RequestBuilder;
import api.support.builders.RequestPolicyBuilder;
import api.support.builders.ServicePointBuilder;
import api.support.dto.AllowedServicePoint;
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
    Response response = getCreateOp(null, randomId(), null, HttpStatus.SC_BAD_REQUEST);
    assertThat(response.getBody(), equalTo("Invalid combination of query parameters"));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "9fdf7408-b8cf-43c1-ae1e-58b7a609b86c,  7341b1d6-a6a7-41ec-8653-00b819a70a30, 5449cec7-815d-42af-83cd-d406d64c822e",
    "9fdf7408-b8cf-43c1-ae1e-58b7a609b86c,  NULL,                                 NULL",
  }, nullValues={"NULL"})
  void getFailsWithBadRequestWhenRequestDoesNotContainExactlyTwoParameters(String requesterId,
    String instanceId, String itemId) {

    Response response = getCreateOp(requesterId, instanceId, itemId, HttpStatus.SC_BAD_REQUEST);
    assertThat(response.getBody(), equalTo("Invalid combination of query parameters"));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "not-a-uuid,                            7341b1d6-a6a7-41ec-8653-00b819a70a30, NULL",
    "9fdf7408-b8cf-43c1-ae1e-58b7a609b86c,  not-a-uuid,                           NULL",
    "9fdf7408-b8cf-43c1-ae1e-58b7a609b86c,  NULL,                                 not-a-uuid",
    "not-a-uuid,                            not-a-uuid,                           not-a-uuid",
  }, nullValues={"NULL"})
  void getFailsWhenRequestContainsInvalidUUID(String requesterId, String instanceId, String itemId) {
    Response response = getCreateOp(requesterId, instanceId, itemId, HttpStatus.SC_BAD_REQUEST);
    assertThat(response.getBody(), containsString("ID is not a valid UUID"));
  }

  @Test
  void getFailsWithMultipleErrors() {
    Response response = getCreateOp(null, "instanceId", "itemId", HttpStatus.SC_BAD_REQUEST);
    assertThat(response.getBody(), equalTo("Instance ID is not a valid UUID: instanceId. " +
      "Item ID is not a valid UUID: itemId. Invalid combination of query parameters"));
  }

  public static Object[] shouldReturnListOfAllowedServicePointsForRequestParameters() {
    String sp1Id = randomId();
    String sp2Id = randomId();
    var sp1 = new AllowedServicePoint(sp1Id, "SP One");
    var sp2 = new AllowedServicePoint(sp2Id, "SP Two");

    List<AllowedServicePoint> none = List.of();
    List<AllowedServicePoint> oneAndTwo = List.of(sp1, sp2);

    return new Object[][]{
      {PAGE, ITEM, AVAILABLE, oneAndTwo, oneAndTwo},
      {HOLD, ITEM, AVAILABLE, oneAndTwo, none},
      {RECALL, ITEM, AVAILABLE, oneAndTwo, none},
      {PAGE, TITLE, AVAILABLE, oneAndTwo, oneAndTwo},
      {HOLD, TITLE, AVAILABLE, oneAndTwo, none},
      {RECALL, TITLE, AVAILABLE, oneAndTwo, none},
      {PAGE, ITEM, CHECKED_OUT, oneAndTwo, none},
      {HOLD, ITEM, CHECKED_OUT, oneAndTwo, oneAndTwo},
      {RECALL, ITEM, CHECKED_OUT, oneAndTwo, oneAndTwo},
      {PAGE, TITLE, CHECKED_OUT, oneAndTwo, none},
      {HOLD, TITLE, CHECKED_OUT, oneAndTwo, oneAndTwo},
      {RECALL, TITLE, CHECKED_OUT, oneAndTwo, oneAndTwo},
    };
  }

  @ParameterizedTest
  @MethodSource("shouldReturnListOfAllowedServicePointsForRequestParameters")
  void shouldReturnListOfAllowedServicePointsForRequest(RequestType requestType,
    RequestLevel requestLevel, ItemStatus itemStatus, List<AllowedServicePoint> allowedSpByPolicy,
    List<AllowedServicePoint> allowedSpInResponse) {

    var requesterId = usersFixture.steve().getId().toString();
    var items = itemsFixture.createMultipleItemForTheSameInstance(1,
      List.of(ib -> ib.withStatus(itemStatus.getValue())));
    var item = items.get(0);
    var itemId = item.getId().toString();
    var instanceId = item.getInstanceId().toString();

    allowedSpByPolicy.forEach(sp -> servicePointsFixture.create(servicePointBuilder()
      .withId(UUID.fromString(sp.getId()))
      .withName(sp.getName())
    ));

    setRequestPolicyWithAllowedServicePoints(requestType, allowedSpByPolicy.stream()
      .map(AllowedServicePoint::getId)
      .map(UUID::fromString)
      .collect(Collectors.toSet()));

    var response = requestLevel == TITLE
      ? get("create", requesterId, instanceId, null, null, HttpStatus.SC_OK).getJson()
      : get("create", requesterId, null, itemId, null, HttpStatus.SC_OK).getJson();

    assertThat(response, allowedServicePointMatcher(Map.of(requestType, allowedSpInResponse)));
  }

  public static Object[] shouldReturnListOfAllowedServicePointsForRequestReplacementParameters() {
    String sp1Id = randomId();
    String sp2Id = randomId();
    var sp1 = new AllowedServicePoint(sp1Id, "SP One");
    var sp2 = new AllowedServicePoint(sp2Id, "SP Two");

    List<AllowedServicePoint> none = List.of();
    List<AllowedServicePoint> oneAndTwo = List.of(sp1, sp2);

    return new Object[][]{
      {PAGE, ITEM, AVAILABLE, oneAndTwo, oneAndTwo},
      {PAGE, TITLE, AVAILABLE, oneAndTwo, oneAndTwo},
      {HOLD, ITEM, CHECKED_OUT, oneAndTwo, oneAndTwo},
      {RECALL, ITEM, CHECKED_OUT, oneAndTwo, oneAndTwo},
      {HOLD, TITLE, CHECKED_OUT, oneAndTwo, oneAndTwo},
      {RECALL, TITLE, CHECKED_OUT, oneAndTwo, oneAndTwo},
    };
  }

  @ParameterizedTest
  @MethodSource("shouldReturnListOfAllowedServicePointsForRequestReplacementParameters")
  void shouldReturnListOfAllowedServicePointsForRequestReplacement(
    RequestType requestType, RequestLevel requestLevel, ItemStatus itemStatus,
    List<AllowedServicePoint> allowedSpByPolicy, List<AllowedServicePoint> allowedSpInResponse) {

    var requesterId = usersFixture.steve().getId().toString();
    var items = itemsFixture.createMultipleItemForTheSameInstance(1,
      List.of(ib -> ib.withStatus(itemStatus.getValue())));
    var item = items.get(0);
    var itemId = item.getId().toString();
    var holdingsRecordId = item.getHoldingsRecordId().toString();
    var instanceId = item.getInstanceId().toString();

    allowedSpByPolicy.forEach(sp -> servicePointsFixture.create(servicePointBuilder()
      .withId(UUID.fromString(sp.getId()))
      .withName(sp.getName())
    ));

    setRequestPolicyWithAllowedServicePoints(requestType, allowedSpByPolicy.stream()
      .map(AllowedServicePoint::getId)
      .map(UUID::fromString)
      .collect(Collectors.toSet()));

    configurationsFixture.enableTlrFeature();
    var pickupLocationId = allowedSpByPolicy.stream()
      .findFirst()
      .map(AllowedServicePoint::getId)
      .map(UUID::fromString)
      .orElse(null);

    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .withRequestType(requestType.toString())
      .fulfillToHoldShelf()
      .withRequestLevel(requestLevel.toString())
      .withInstanceId(UUID.fromString(instanceId))
      .withItemId(requestLevel == TITLE ? null : UUID.fromString(itemId))
      .withHoldingsRecordId(requestLevel == TITLE ? null : UUID.fromString(holdingsRecordId))
      .withRequestDate(ZonedDateTime.now())
      .withRequesterId(UUID.fromString(requesterId))
      .withPickupServicePointId(pickupLocationId));

    var requestId = request == null ? null : request.getId().toString();

    var response =
      get("replace", null, null, null, requestId, HttpStatus.SC_OK).getJson();

    assertThat(response, allowedServicePointMatcher(Map.of(requestType, allowedSpInResponse)));
  }

  public static Object[] shouldReturnOnlyExistingAllowedServicePointForRequestParameters() {
    String sp1Id = randomId();
    String sp2Id = randomId();

    var sp1 = new AllowedServicePoint(sp1Id, "SP One");
    var sp2 = new AllowedServicePoint(sp2Id, "SP Two");

    List<AllowedServicePoint> none = List.of();
    List<AllowedServicePoint> two = List.of(sp2);
    List<AllowedServicePoint> oneAndTwo = List.of(sp1, sp2);

    return new Object[][]{
      {PAGE, ITEM, AVAILABLE, oneAndTwo, two},
      {HOLD, ITEM, AVAILABLE, oneAndTwo, none},
      {RECALL, ITEM, AVAILABLE, oneAndTwo, none},
      {PAGE, TITLE, AVAILABLE, oneAndTwo, two},
      {HOLD, TITLE, AVAILABLE, oneAndTwo, none},
      {RECALL, TITLE, AVAILABLE, oneAndTwo, none},
      {PAGE, ITEM, CHECKED_OUT, oneAndTwo, none},
      {HOLD, ITEM, CHECKED_OUT, oneAndTwo, two},
      {RECALL, ITEM, CHECKED_OUT, oneAndTwo, two},
      {PAGE, TITLE, CHECKED_OUT, oneAndTwo, none},
      {HOLD, TITLE, CHECKED_OUT, oneAndTwo, two},
      {RECALL, TITLE, CHECKED_OUT, oneAndTwo, two},
    };
  }

  @ParameterizedTest
  @MethodSource("shouldReturnOnlyExistingAllowedServicePointForRequestParameters")
  void shouldReturnOnlyExistingAllowedServicePointForRequest(RequestType requestType,
    RequestLevel requestLevel, ItemStatus itemStatus, List<AllowedServicePoint> allowedSpByPolicy,
    List<AllowedServicePoint> allowedSpInResponse) {

    var requesterId = usersFixture.steve().getId().toString();
    var items = itemsFixture.createMultipleItemForTheSameInstance(1,
      List.of(ib -> ib.withStatus(itemStatus.getValue())));
    var item = items.get(0);
    var itemId = item.getId().toString();
    var instanceId = item.getInstanceId().toString();

    allowedSpByPolicy.stream()
      .skip(1)
      .forEach(sp -> servicePointsFixture.create(servicePointBuilder()
        .withId(UUID.fromString(sp.getId()))
        .withName(sp.getName())
      ));

    setRequestPolicyWithAllowedServicePoints(requestType, allowedSpByPolicy.stream()
      .map(AllowedServicePoint::getId)
      .map(UUID::fromString)
      .collect(Collectors.toSet()));

    var response = requestLevel == TITLE
      ? getCreateOp(requesterId, instanceId, null, HttpStatus.SC_OK).getJson()
      : getCreateOp(requesterId, null, itemId, HttpStatus.SC_OK).getJson();

    assertThat(response, allowedServicePointMatcher(Map.of(
      requestType, allowedSpInResponse
    )));
  }

  public static Object[]
  shouldReturnNoAllowedServicePointsIfAllowedServicePointDoesNotExistParameters() {

    String sp1Id = randomId();
    String sp2Id = randomId();

    var sp1 = new AllowedServicePoint(sp1Id, "SP One");
    var sp2 = new AllowedServicePoint(sp2Id, "SP Two");

    List<AllowedServicePoint> none = List.of();
    List<AllowedServicePoint> oneAndTwo = List.of(sp1, sp2);

    return new Object[][]{
      {PAGE, ITEM, AVAILABLE, oneAndTwo, none},
      {HOLD, ITEM, AVAILABLE, oneAndTwo, none},
      {RECALL, ITEM, AVAILABLE, oneAndTwo, none},
      {PAGE, TITLE, AVAILABLE, oneAndTwo, none},
      {HOLD, TITLE, AVAILABLE, oneAndTwo, none},
      {RECALL, TITLE, AVAILABLE, oneAndTwo, none},
      {PAGE, ITEM, CHECKED_OUT, oneAndTwo, none},
      {HOLD, ITEM, CHECKED_OUT, oneAndTwo, none},
      {RECALL, ITEM, CHECKED_OUT, oneAndTwo, none},
      {PAGE, TITLE, CHECKED_OUT, oneAndTwo, none},
      {HOLD, TITLE, CHECKED_OUT, oneAndTwo, none},
      {RECALL, TITLE, CHECKED_OUT, oneAndTwo, none},
    };
  }

  @ParameterizedTest
  @MethodSource("shouldReturnNoAllowedServicePointsIfAllowedServicePointDoesNotExistParameters")
  void shouldReturnNoAllowedServicePointsIfAllowedServicePointDoesNotExist(
    RequestType requestType, RequestLevel requestLevel, ItemStatus itemStatus,
    List<AllowedServicePoint> allowedSpByPolicy, List<AllowedServicePoint> allowedSpInResponse) {

    var requesterId = usersFixture.steve().getId().toString();
    var items = itemsFixture.createMultipleItemForTheSameInstance(1,
      List.of(ib -> ib.withStatus(itemStatus.getValue())));
    var item = items.get(0);
    var itemId = item.getId().toString();
    var instanceId = item.getInstanceId().toString();

    setRequestPolicyWithAllowedServicePoints(requestType, allowedSpByPolicy.stream()
      .map(AllowedServicePoint::getId)
      .map(UUID::fromString)
      .collect(Collectors.toSet()));

    var response = requestLevel == TITLE
      ? getCreateOp(requesterId, instanceId, null, HttpStatus.SC_OK).getJson()
      : getCreateOp(requesterId, null, itemId, HttpStatus.SC_OK).getJson();

    assertThat(response, allowedServicePointMatcher(Map.of(
      requestType, allowedSpInResponse
    )));
  }

  public static Object[]
  shouldReturnNoAllowedServicePointsIfAllowedServicePointIsNotPickupLocationParameters() {

    String sp1Id = randomId();
    String sp2Id = randomId();

    var sp1 = new AllowedServicePoint(sp1Id, "SP One");
    var sp2 = new AllowedServicePoint(sp2Id, "SP Two");

    List<AllowedServicePoint> none = List.of();
    List<AllowedServicePoint> oneAndTwo = List.of(sp1, sp2);

    return new Object[][]{
      {PAGE, ITEM, AVAILABLE, oneAndTwo, none},
      {HOLD, ITEM, AVAILABLE, oneAndTwo, none},
      {RECALL, ITEM, AVAILABLE, oneAndTwo, none},
      {PAGE, TITLE, AVAILABLE, oneAndTwo, none},
      {HOLD, TITLE, AVAILABLE, oneAndTwo, none},
      {RECALL, TITLE, AVAILABLE, oneAndTwo, none},
      {PAGE, ITEM, CHECKED_OUT, oneAndTwo, none},
      {HOLD, ITEM, CHECKED_OUT, oneAndTwo, none},
      {RECALL, ITEM, CHECKED_OUT, oneAndTwo, none},
      {PAGE, TITLE, CHECKED_OUT, oneAndTwo, none},
      {HOLD, TITLE, CHECKED_OUT, oneAndTwo, none},
      {RECALL, TITLE, CHECKED_OUT, oneAndTwo, none},
    };
  }

  @ParameterizedTest
  @MethodSource("shouldReturnNoAllowedServicePointsIfAllowedServicePointIsNotPickupLocationParameters")
  void shouldReturnNoAllowedServicePointsIfAllowedServicePointIsNotPickupLocation(
    RequestType requestType, RequestLevel requestLevel, ItemStatus itemStatus,
    List<AllowedServicePoint> allowedSpByPolicy, List<AllowedServicePoint> allowedSpInResponse) {

    var requesterId = usersFixture.steve().getId().toString();
    var items = itemsFixture.createMultipleItemForTheSameInstance(1,
      List.of(ib -> ib.withStatus(itemStatus.getValue())));
    var item = items.get(0);
    var itemId = item.getId().toString();
    var instanceId = item.getInstanceId().toString();

    allowedSpByPolicy.forEach(sp -> servicePointsFixture.create(servicePointBuilder()
      .withId(UUID.fromString(sp.getId()))
      .withName(sp.getName())
      .withPickupLocation(false)
    ));

    setRequestPolicyWithAllowedServicePoints(requestType, allowedSpByPolicy.stream()
      .map(AllowedServicePoint::getId)
      .map(UUID::fromString)
      .collect(Collectors.toSet()));

    var response = requestLevel == TITLE
      ? getCreateOp(requesterId, instanceId, null, HttpStatus.SC_OK).getJson()
      : getCreateOp(requesterId, null, itemId, HttpStatus.SC_OK).getJson();

    assertThat(response, allowedServicePointMatcher(Map.of(
      requestType, allowedSpInResponse
    )));
  }

  public static Object[] shouldReturnOnlyExistingServicePointsWhenRequestPolicyDoesNotHaveAnyParameters() {
    return new Object[][]{
      {PAGE, ITEM, AVAILABLE, false},
      {HOLD, ITEM, AVAILABLE, true},
      {RECALL, ITEM, AVAILABLE, true},
      {PAGE, TITLE, AVAILABLE, false},
      {HOLD, TITLE, AVAILABLE, true},
      {RECALL, TITLE, AVAILABLE, true},
      {PAGE, ITEM, CHECKED_OUT, true},
      {HOLD, ITEM, CHECKED_OUT, false},
      {RECALL, ITEM, CHECKED_OUT, false},
      {PAGE, TITLE, CHECKED_OUT, true},
      {HOLD, TITLE, CHECKED_OUT, false},
      {RECALL, TITLE, CHECKED_OUT, false},
    };
  }

  @ParameterizedTest
  @MethodSource("shouldReturnOnlyExistingServicePointsWhenRequestPolicyDoesNotHaveAnyParameters")
  void shouldReturnOnlyExistingServicePointsWhenRequestPolicyDoesNotHaveAny(
    @NotNull RequestType requestType, RequestLevel requestLevel, ItemStatus itemStatus,
    boolean noServicePointsShouldBeAvailableForRequestType) {

    var requesterId = usersFixture.steve().getId().toString();
    var items = itemsFixture.createMultipleItemForTheSameInstance(1,
      List.of(ib -> ib.withStatus(itemStatus.getValue())));
    var item = items.get(0);
    var itemId = item.getId().toString();
    var instanceId = item.getInstanceId().toString();

    var response = requestLevel == TITLE
      ? getCreateOp(requesterId, instanceId, null, HttpStatus.SC_OK).getJson()
      : getCreateOp(requesterId, null, itemId, HttpStatus.SC_OK).getJson();

    var allowedServicePoints = response.getJsonArray(requestType.getValue());
    var servicePointsWithPickupLocation = servicePointsFixture.getAllServicePoints().stream()
      .filter(sp -> "true".equals(sp.getJson().getString("pickupLocation")))
      .toList();

    if (noServicePointsShouldBeAvailableForRequestType) {
      assertThat(allowedServicePoints, IsNull.nullValue());
    } else {
      assertThat(allowedServicePoints.stream().toList(), hasSize(servicePointsWithPickupLocation.size()));
      assertServicePointsMatch(allowedServicePoints, servicePointsWithPickupLocation);
    }
  }

  @Test
  void shouldReturnErrorIfRequesterDoesNotExist() {
    var requesterId = randomId();
    var itemId = itemsFixture.basedUponNod().getId().toString();
    var cd1Id = servicePointsFixture.cd1().getId();
    setRequestPolicyWithAllowedServicePoints(PAGE, Set.of(cd1Id));

    Response response = getCreateOp(requesterId, null, itemId, HttpStatus.SC_UNPROCESSABLE_ENTITY);
    assertThat(response.getBody(), containsString("User with id=" + requesterId +
      " cannot be found"));
  }

  @Test
  void shouldReturnErrorIfItemDoesNotExist() {
    var requesterId = usersFixture.steve().getId().toString();
    var itemId = randomId();
    var cd1Id = servicePointsFixture.cd1().getId();
    setRequestPolicyWithAllowedServicePoints(PAGE, Set.of(cd1Id));

    Response response = getCreateOp(requesterId, null, itemId, HttpStatus.SC_UNPROCESSABLE_ENTITY);
    assertThat(response.getBody(), containsString("Item with id=" + itemId +
      " cannot be found"));
  }

  @Test
  void shouldReturnErrorIfInstanceDoesNotExist() {
    var requesterId = usersFixture.steve().getId().toString();
    var instanceId = randomId();
    var cd1Id = servicePointsFixture.cd1().getId();
    setRequestPolicyWithAllowedServicePoints(PAGE, Set.of(cd1Id));

    Response response = getCreateOp(requesterId, instanceId, null, HttpStatus.SC_UNPROCESSABLE_ENTITY);
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
    allowedServicePointsInPolicy.put(PAGE, Set.of(cd1.getId(), cd2.getId()));
    allowedServicePointsInPolicy.put(HOLD, Set.of(cd4.getId(), cd5.getId()));
    var requestPolicy = requestPoliciesFixture
      .createRequestPolicyWithAllowedServicePoints(allowedServicePointsInPolicy,
        PAGE, HOLD);
    policiesActivation.use(PoliciesToActivate.builder().requestPolicy(requestPolicy));

    var response = requestLevel == TITLE
      ? getCreateOp(requesterId, instanceId, null, HttpStatus.SC_OK).getJson()
      : getCreateOp(requesterId, null, itemId, HttpStatus.SC_OK).getJson();

    assertThat(response, hasNoJsonPath(HOLD.getValue()));
    assertThat(response, hasNoJsonPath(RECALL.getValue()));

    final JsonArray allowedPageServicePoints = response.getJsonArray(PAGE.getValue());

    assertServicePointsMatch(allowedPageServicePoints, List.of(cd1, cd2));
  }

  @Test
  void shouldReturnAllowedServicePointsForAllEnabledRequestTypes() {
    var requesterId = usersFixture.steve().getId().toString();
    var items = itemsFixture.createMultipleItemForTheSameInstance(2,
      List.of(
        ib -> ib.withStatus(ItemBuilder.AVAILABLE),
        ib -> ib.withStatus(ItemBuilder.CHECKED_OUT)
      ));
    var item = items.get(0);
    var itemId = item.getId().toString();
    var instanceId = item.getInstanceId().toString();
    var cd1 = servicePointsFixture.cd1();
    var cd2 = servicePointsFixture.cd2();
    final Map<RequestType, Set<UUID>> allowedServicePointsInPolicy = new HashMap<>();
    allowedServicePointsInPolicy.put(PAGE, Set.of(cd1.getId()));
    allowedServicePointsInPolicy.put(HOLD, Set.of(cd2.getId()));
    policiesActivation.use(new RequestPolicyBuilder(
      UUID.randomUUID(),
      List.of(PAGE, HOLD, RECALL),
      "Test request policy",
      "Test description",
      allowedServicePointsInPolicy));
    var allServicePointsWithPickupLocation = servicePointsFixture.getAllServicePoints().stream()
      .filter(sp -> "true".equals(sp.getJson().getString("pickupLocation")))
      .toList();
    assertThat(allServicePointsWithPickupLocation, hasSize(2));

    // ILR scenario

    var responseIlr = getCreateOp(requesterId, null, itemId, HttpStatus.SC_OK).getJson();

    final JsonArray allowedPageServicePointsIlr =
      responseIlr.getJsonArray(PAGE.getValue());
    final JsonArray allowedHoldServicePointsIlr =
      responseIlr.getJsonArray(HOLD.getValue());
    final JsonArray allowedRecallServicePointsIlr =
      responseIlr.getJsonArray(RECALL.getValue());

    assertServicePointsMatch(allowedPageServicePointsIlr, List.of(cd1));
    assertThat(allServicePointsWithPickupLocation, hasSize(2));
    assertThat(allowedHoldServicePointsIlr, IsNull.nullValue());
    assertThat(allowedRecallServicePointsIlr, IsNull.nullValue());

    // TLR scenario

    var responseTlr = getCreateOp(requesterId, instanceId, null, HttpStatus.SC_OK).getJson();

    final JsonArray allowedPageServicePointsTlr =
      responseTlr.getJsonArray(PAGE.getValue());
    final JsonArray allowedHoldServicePointsTlr =
      responseTlr.getJsonArray(HOLD.getValue());
    final JsonArray allowedRecallServicePointsTlr =
      responseTlr.getJsonArray(RECALL.getValue());

    assertServicePointsMatch(allowedPageServicePointsTlr, List.of(cd1));
    assertServicePointsMatch(allowedHoldServicePointsTlr, List.of(cd2));
    assertServicePointsMatch(allowedRecallServicePointsTlr, List.of(cd1, cd2));
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

  private Response getCreateOp(String requesterId, String instanceId, String itemId,
    int expectedStatusCode) {

    return get("create", requesterId, instanceId, itemId, null, expectedStatusCode);
  }

  private Response getReplaceOp(String requesterId, String instanceId, String itemId,
    int expectedStatusCode) {

    return get("replace", requesterId, instanceId, itemId, null, expectedStatusCode);
  }

  private Response get(String operation, String requesterId, String instanceId, String itemId,
    String requestId, int expectedStatusCode) {

    List<QueryStringParameter> queryParams = new ArrayList<>();
    queryParams.add(namedParameter("operation", operation));
    if (requesterId != null) {
      queryParams.add(namedParameter("requesterId", requesterId));
    }
    if (instanceId != null) {
      queryParams.add(namedParameter("instanceId", instanceId));
    }
    if (itemId != null) {
      queryParams.add(namedParameter("itemId", itemId));
    }
    if (requestId != null) {
      queryParams.add(namedParameter("requestId", requestId));
    }

    return restAssuredClient.get(allowedServicePointsUrl(), queryParams, expectedStatusCode,
      "allowed-service-points");
  }

  private UUID setRequestPolicyWithAllowedServicePoints(RequestType requestType,
    Set<UUID> requestPickupSpIds) {

    final Map<RequestType, Set<UUID>> allowedServicePoints = new HashMap<>();
    allowedServicePoints.put(requestType, requestPickupSpIds);
    var requestPolicy = requestPoliciesFixture
      .createRequestPolicyWithAllowedServicePoints(allowedServicePoints, requestType);
    policiesActivation.use(PoliciesToActivate.builder().requestPolicy(requestPolicy));

    return requestPolicy.getId();
  }

  private ServicePointBuilder servicePointBuilder() {
    return new ServicePointBuilder("SP name", "sp-code-" + randomId(), "SP description")
      .withPickupLocation(TRUE)
      .withHoldShelfExpriyPeriod(30, "Days");
  }
}
