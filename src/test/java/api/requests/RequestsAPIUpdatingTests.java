package api.requests;

import static api.support.fakes.PublishedEvents.byEventType;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static api.support.matchers.EventMatchers.isValidLoanDueDateChangedEvent;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasCode;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.time.ZoneOffset.UTC;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.domain.EventType.LOAN_DUE_DATE_CHANGED;
import static org.folio.circulation.domain.representations.RequestProperties.CANCELLATION_REASON_NAME;
import static org.folio.circulation.domain.representations.RequestProperties.CANCELLATION_REASON_PUBLIC_DESCRIPTION;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_CREATED;
import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_UPDATED;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;

import java.net.HttpURLConnection;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import api.support.APITestContext;
import com.jayway.jsonpath.JsonPath;
import org.awaitility.Awaitility;
import org.folio.circulation.domain.Request;
import org.folio.circulation.support.ErrorCode;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.Address;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import api.support.fakes.FakeModNotify;
import api.support.fakes.FakePubSub;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.val;

class RequestsAPIUpdatingTests extends APITests {
  private static final String REQUEST_CANCELLATION = "Request cancellation";

  @Test
  void canReplaceAnExistingRequest() {
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource steve = usersFixture.steve();

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withRequestDate(requestDate)
        .itemRequestLevel()
        .withInstanceId(temeraire.getInstanceId())
        .forItem(temeraire)
        .by(steve)
        .fulfillToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(LocalDate.of(2017, 7, 30))
        .withHoldShelfExpiration(LocalDate.of(2017, 8, 31)));

    final IndividualResource charlotte = usersFixture.charlotte();

    requestsClient.replace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .hold()
        .by(charlotte)
        .withTags(new RequestBuilder.Tags(Arrays.asList("new", "important")))
    );

    Response getResponse = requestsClient.getById(createdRequest.getId());

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(createdRequest.getId()));
    assertThat(representation.getString("requestType"), is("Hold"));
    assertThat(representation.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(representation.getString("itemId"), is(temeraire.getId()));
    assertThat(representation.getString("holdingsRecordId"), is(temeraire.getHoldingsRecordId()));
    assertThat(representation.getString("instanceId"), is(temeraire.getInstanceId()));
    assertThat(representation.getString("requestLevel"), is("Item"));
    assertThat(representation.getString("requesterId"), is(charlotte.getId()));
    assertThat(representation.getString("fulfillmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30T23:59:59.000Z"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    JsonObject instance = representation.getJsonObject("instance");
    assertThat("title is taken from instance", instance.getString("title"), is("Temeraire"));

    JsonArray contributors = instance.getJsonArray("contributorNames");
    assertThat(contributors, notNullValue());
    assertThat(contributors.size(), is(1));
    assertThat(contributors.getJsonObject(0).getString("name"), is("Novik, Naomi"));

    assertThat("barcode is taken from item",
      representation.getJsonObject("item").getString("barcode"),
      is("232142443432"));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("last name is taken from requesting user",
      representation.getJsonObject("requester").getString("lastName"),
      is("Broadwell"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Charlotte"));

    assertThat("middle name is not taken from requesting user",
      representation.getJsonObject("requester").containsKey("middleName"),
      is(false));

    assertThat("barcode is taken from requesting user",
      representation.getJsonObject("requester").getString("barcode"),
      is("6430705932"));

    assertThat(representation.containsKey("tags"), is(true));
    final JsonObject tagsRepresentation = representation.getJsonObject("tags");

    assertThat(tagsRepresentation.containsKey("tagList"), is(true));
    assertThat(tagsRepresentation.getJsonArray("tagList"), contains("new", "important"));
  }

  //TODO: Check does not have pickup service point any more
  @Test
  void canReplaceAnExistingRequestWithDeliveryAddress() {
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource workAddressType = addressTypesFixture.work();

    final IndividualResource charlotte = usersFixture.charlotte(userBuilder -> userBuilder
      .withAddress(
        new Address(workAddressType.getId(),
          "Fake first address line",
          "Fake second address line",
          "Fake city",
          "Fake region",
          "Fake postal code",
          "Fake country code")));

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(temeraire)
      .by(charlotte)
      .withRequestDate(requestDate)
      .fulfillToHoldShelf(exampleServicePoint.getId()));

    requestsClient.replace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .hold()
        .deliverToAddress(workAddressType.getId()));

    Response getResponse = requestsClient.getById(createdRequest.getId());

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(createdRequest.getId()));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("Request should have a delivery address",
      representation.containsKey("deliveryAddress"), is(true));

    final JsonObject deliveryAddress = representation.getJsonObject("deliveryAddress");

    assertThat(deliveryAddress.getString("addressTypeId"), is(workAddressType.getId()));
    assertThat(deliveryAddress.getString("addressLine1"), is("Fake first address line"));
    assertThat(deliveryAddress.getString("addressLine2"), is("Fake second address line"));
    assertThat(deliveryAddress.getString("city"), is("Fake city"));
    assertThat(deliveryAddress.getString("region"), is("Fake region"));
    assertThat(deliveryAddress.getString("postalCode"), is("Fake postal code"));
    assertThat(deliveryAddress.getString("countryId"), is("Fake country code"));
  }

  @Test
  void replacingAnExistingRequestRemovesItemInformationWhenItemDoesNotExist() {
    final ItemResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod);

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withPickupServicePointId(pickupServicePointId)
        .forItem(nod)
        .by(usersFixture.steve()));

    itemsClient.delete(nod.getId());

    requestsClient.replace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .hold());

    Response getResponse = requestsClient.getById(createdRequest.getId());

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("itemId"), is(nod.getId()));

    assertThat("has no item information when item no longer exists",
      representation.containsKey("item"), is(false));
  }

  @Test
  void replacingAnExistingRequestRemovesRequesterInformationWhenUserDoesNotExist() {
    final ItemResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod);

    val requester = usersFixture.steve();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withPickupServicePointId(pickupServicePointId)
        .forItem(nod)
        .by(requester));

    usersFixture.remove(requester);

    requestsClient.replace(createdRequest.getId(), RequestBuilder.from(createdRequest));

    final IndividualResource fetchedRequest = requestsClient.get(createdRequest.getId());

    JsonObject representation = fetchedRequest.getJson();

    assertThat(representation.getString("requesterId"), is(requester.getId()));

    assertThat("has no requesting user information taken when user no longer exists",
      representation.containsKey("requester"), is(false));
  }

  @Test
  void replacingAnExistingRequestRemovesRequesterBarcodeWhenNonePresent() {
    final ItemResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod);

    final IndividualResource steve = usersFixture.steve();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withPickupServicePointId(pickupServicePointId)
        .forItem(nod)
        .by(steve));

    final JsonObject userToReplace = steve.copyJson();

    userToReplace.remove("barcode");

    usersClient.replace(steve.getId(), userToReplace);

    requestsClient.replace(createdRequest.getId(), RequestBuilder.from(createdRequest));

    final IndividualResource fetchedRequest = requestsClient.get(createdRequest.getId());

    JsonObject representation = fetchedRequest.getJson();

    assertThat(representation.getString("requesterId"), is(steve.getId()));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("last name is taken from requesting user",
      representation.getJsonObject("requester").getString("lastName"),
      is("Jones"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Steven"));

    assertThat("barcode is not present when requesting user does not have one",
      representation.getJsonObject("requester").containsKey("barcode"),
      is(false));
  }

  @Test
  void replacingAnExistingRequestIncludesRequesterMiddleNameWhenPresent() {
    final ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod);

    final IndividualResource steve = usersFixture.steve();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withPickupServicePointId(pickupServicePointId)
        .forItem(nod)
        .by(steve));

    final JsonObject userToReplace = steve.copyJson();

    final JsonObject personalDetails = userToReplace.getJsonObject("personal");

    write(personalDetails, "middleName", "Carter");

    usersClient.replace(steve.getId(), userToReplace);

    requestsClient.replace(createdRequest.getId(), RequestBuilder.from(createdRequest));

    final IndividualResource fetchedRequest = requestsClient.get(createdRequest.getId());

    JsonObject representation = fetchedRequest.getJson();

    assertThat(representation.getString("requesterId"), is(steve.getId()));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("last name is taken from requesting user",
      representation.getJsonObject("requester").getString("lastName"),
      is("Jones"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Steven"));

    assertThat("middle name is taken from requesting user",
      representation.getJsonObject("requester").getString("middleName"),
      is("Carter"));
  }

  @Test
  void replacingAnExistingRequestRemovesItemBarcodeWhenNonePresent() {
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(temeraire);

    IndividualResource createdRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(usersFixture.steve()));

    final JsonObject itemToReplace = temeraire.copyJson();

    itemToReplace.remove("barcode");

    itemsClient.replace(temeraire.getId(), itemToReplace);

    requestsClient.replace(createdRequest.getId(), createdRequest.copyJson());

    IndividualResource fetchedRequest = requestsClient.get(createdRequest.getId());

    JsonObject representation = fetchedRequest.getJson();

    assertThat(representation.getString("itemId"), is(temeraire.getId()));

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    assertThat("title is taken from instance",
      representation.getJsonObject("instance").getString("title"),
      is("Temeraire"));

    assertThat("barcode is not taken from item",
      representation.getJsonObject("item").containsKey("barcode"),
      is(false));
  }

  @Test
  void cannotReplaceAnExistingRequestWithServicePointThatIsNotForPickup() {
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(temeraire)
      .by(usersFixture.steve())
      .fulfillToHoldShelf(exampleServicePoint.getId()));

    UUID badServicePointId = servicePointsFixture.cd3().getId();

    final Response putResponse = requestsClient.attemptReplace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .withPickupServicePointId(badServicePointId));

    assertThat(putResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Service point is not a pickup location"),
      hasUUIDParameter("pickupServicePointId", badServicePointId),
      hasCode(ErrorCode.SERVICE_POINT_IS_NOT_PICKUP_LOCATION))));
  }

  @Test
  void cannotReplaceAnExistingRequestWithUnknownPickupLocation() {
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .forItem(temeraire)
        .by(usersFixture.james())
        .fulfillToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId()));

    UUID badServicePointId = UUID.randomUUID();

    final Response putResponse = requestsClient.attemptReplace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .withPickupServicePointId(badServicePointId));

    assertThat(putResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(putResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Pickup service point does not exist"),
      hasUUIDParameter("pickupServicePointId", badServicePointId))));
  }

  @Test
  void cancellationReasonPublicDescriptionIsUsedAsReasonForCancellationToken() {
    UUID requestCancellationTemplateId = UUID.randomUUID();
    JsonObject requestCancellationConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(requestCancellationTemplateId)
      .withEventType(REQUEST_CANCELLATION)
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with request cancellation notice")
      .withLoanNotices(Collections.singletonList(requestCancellationConfiguration));
    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final IndividualResource requester = usersFixture.steve();
    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();
    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .page()
        .withRequestDate(requestDate)
        .forItem(temeraire)
        .by(requester)
        .fulfillToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(LocalDate.of(2017, 7, 30))
        .withHoldShelfExpiration(LocalDate.of(2017, 8, 31)));

    final IndividualResource itemNotAvailable = cancellationReasonsFixture.itemNotAvailable();
    JsonObject updatedRequest = RequestBuilder.from(createdRequest)
      .cancelled()
      .withCancellationReasonId(itemNotAvailable.getId())
      .withCancellationAdditionalInformation("Cancellation info")
      .create();
    requestsClient.replace(createdRequest.getId(), updatedRequest);

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();

    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(requester));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(temeraire, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getRequestContextMatchers(updatedRequest));
    noticeContextMatchers.putAll(TemplateContextMatchers.getCancelledRequestContextMatchers(updatedRequest));
    noticeContextMatchers.put("request.reasonForCancellation",
      is(itemNotAvailable.getJson().getString(CANCELLATION_REASON_PUBLIC_DESCRIPTION)));

    assertThat(FakeModNotify.getSentPatronNotices(), hasItems(
      hasEmailNoticeProperties(requester.getId(), requestCancellationTemplateId, noticeContextMatchers)));
  }

  @Test
  void cancellationReasonNameIsUsedAsReasonForCancellationTokenWhenPublicDescriptionIsNotPresent() {
    UUID requestCancellationTemplateId = UUID.randomUUID();
    JsonObject requestCancellationConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(requestCancellationTemplateId)
      .withEventType(REQUEST_CANCELLATION)
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with request cancellation notice")
      .withLoanNotices(Collections.singletonList(requestCancellationConfiguration));
    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final IndividualResource requester = usersFixture.steve();
    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();
    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .page()
        .withRequestDate(requestDate)
        .forItem(temeraire)
        .by(requester)
        .fulfillToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(LocalDate.of(2017, 7, 30))
        .withHoldShelfExpiration(LocalDate.of(2017, 8, 31)));

    final IndividualResource courseReserves = cancellationReasonsFixture.courseReserves();
    JsonObject updatedRequest = RequestBuilder.from(createdRequest)
      .cancelled()
      .withCancellationReasonId(courseReserves.getId())
      .withCancellationAdditionalInformation("Cancellation info")
      .create();

    requestsClient.replace(createdRequest.getId(), updatedRequest);

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();

    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(requester));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(temeraire, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getRequestContextMatchers(updatedRequest));
    noticeContextMatchers.putAll(TemplateContextMatchers.getCancelledRequestContextMatchers(updatedRequest));
    noticeContextMatchers.put("request.reasonForCancellation",
      is(courseReserves.getJson().getString(CANCELLATION_REASON_NAME)));

    assertThat(FakeModNotify.getSentPatronNotices(), hasItems(
      hasEmailNoticeProperties(requester.getId(), requestCancellationTemplateId, noticeContextMatchers)));
  }

  @Test
  void replacedRequestShouldOnlyIncludeStoredPropertiesInStorage() {
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();

    proxyRelationshipsFixture.currentProxyFor(steve, charlotte);

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withRequestDate(requestDate)
        .forItem(temeraire)
        .by(steve)
        .proxiedBy(charlotte)
        .fulfillToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(LocalDate.of(2017, 7, 30))
        .withHoldShelfExpiration(LocalDate.of(2017, 8, 31)));

    requestsClient.replace(createdRequest.getId(), createdRequest.copyJson());

    Response getStorageRequestResponse = requestsStorageClient.getById(createdRequest.getId());

    assertThat(String.format("Failed to get request: %s", getStorageRequestResponse.getBody()),
      getStorageRequestResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject storageRepresentation = getStorageRequestResponse.getJson();

    assertThat("has information taken from requesting user",
      storageRepresentation.containsKey("requester"), is(true));

    final JsonObject requesterSummary = storageRepresentation.getJsonObject("requester");

    assertThat("last name is taken from requesting user",
      requesterSummary.getString("lastName"), is("Jones"));

    assertThat("first name is taken from requesting user",
      requesterSummary.getString("firstName"), is("Steven"));

    assertThat("patron group information should not be stored for requesting user",
      requesterSummary.containsKey("patronGroup"), is(false));

    assertThat("has information taken from proxying user",
      storageRepresentation.containsKey("proxy"), is(true));

    final JsonObject proxySummary = storageRepresentation.getJsonObject("proxy");

    assertThat("last name is taken from proxying user",
      proxySummary.getString("lastName"), is("Broadwell"));

    assertThat("first name is taken from proxying user",
      proxySummary.getString("firstName"), is("Charlotte"));

    assertThat("patron group information should not be stored for proxying user",
      proxySummary.containsKey("patronGroup"), is(false));
  }

  @Test
  void canReplaceRequestWithAnInactiveUser() {
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource steve = usersFixture.steve();

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withRequestDate(requestDate)
        .forItem(temeraire)
        .by(steve)
        .fulfillToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(LocalDate.of(2017, 7, 30))
        .withHoldShelfExpiration(LocalDate.of(2017, 8, 31)));

    final IndividualResource inactiveCharlotte
      = usersFixture.charlotte(UserBuilder::inactive);

    final Response putResponse = requestsClient.attemptReplace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .hold()
        .by(inactiveCharlotte)
        .withTags(new RequestBuilder.Tags(Arrays.asList("new", "important"))));

    assertThat(putResponse, hasStatus(HTTP_NO_CONTENT));
  }

  @Test
  void instanceIdentifiersAreUpdatedWhenRequestIsUpdated() {
    final UUID instanceId = UUID.randomUUID();
    final UUID isbnIdentifierId = identifierTypesFixture.isbn().getId();
    final String isbnValue = "9780866989732";

    final ItemResource item = itemsFixture.basedUponSmallAngryPlanet(
      identity(),
      instanceBuilder -> instanceBuilder.withId(instanceId),
      identity());

    IndividualResource request = requestsClient.create(
      new RequestBuilder()
        .page()
        .forItem(item)
        .itemRequestLevel()
        .withInstanceId(instanceId)
        .by(usersFixture.steve())
        .fulfillToHoldShelf()
        .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    JsonObject updatedInstance = instancesClient.getById(instanceId).getJson().copy()
      .put("identifiers", new JsonArray().add(new JsonObject()
        .put("identifierTypeId", isbnIdentifierId.toString())
        .put("value", isbnValue)));

    instancesClient.replace(instanceId, updatedInstance);

    requestsClient.replace(request.getId(), request.copyJson());

    Response response = requestsClient.getById(request.getId());
    assertThat(response.getJson().getString("instanceId"), is(instanceId));
    assertThat(response.getJson().getString("requestLevel"), is("Item"));

    JsonObject instance = response.getJson().getJsonObject("instance");
    JsonArray identifiers = instance.getJsonArray("identifiers");
    assertThat(identifiers, CoreMatchers.notNullValue());
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers.getJsonObject(0).getString("identifierTypeId"),
      is(isbnIdentifierId.toString()));
    assertThat(identifiers.getJsonObject(0).getString("value"),
      is(isbnValue));

    JsonArray contributors = instance.getJsonArray("contributorNames");
    assertThat(contributors, CoreMatchers.notNullValue());
    assertThat(contributors.size(), is(1));
    assertThat(contributors.getJsonObject(0).getString("name"), is("Chambers, Becky"));
  }

  @Test
  void eventsShouldBePublished() {
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource steve = usersFixture.steve();

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withRequestDate(requestDate)
        .forItem(temeraire)
        .by(steve)
        .fulfillToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(LocalDate.of(2017, 7, 30))
        .withHoldShelfExpiration(LocalDate.of(2017, 8, 31)));

    final IndividualResource charlotte = usersFixture.charlotte();

    requestsClient.replace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .hold()
        .by(charlotte)
        .withTags(new RequestBuilder.Tags(Arrays.asList("new", "important"))));

    Response response = loansClient.getById(loan.getId());
    JsonObject updatedLoan = response.getJson();

    // There should be seven events published - for "check out", for "log event: check out",
    // for "log event: request created", for "log event: request updated" for "recall" and for "replace"
    // and one log events for loans
    final var publishedEvents = Awaitility.await()
      .atMost(1, SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(9));

    final var requestCreatedLogEvent = publishedEvents.findFirst(byLogEventType(REQUEST_CREATED.value()));
    final var requestUpdatedLogEvent = publishedEvents.findFirst(byLogEventType(REQUEST_UPDATED.value()));
    final var dueDateChangedEvent = publishedEvents.findFirst(byEventType(LOAN_DUE_DATE_CHANGED.name()));

    Request requestCreatedFromEventPayload = getRequestFromPayload(requestCreatedLogEvent, "created");
    assertThat(requestCreatedFromEventPayload, notNullValue());

    Request originalCreatedFromEventPayload = getRequestFromPayload(requestUpdatedLogEvent, "original");
    Request updatedCreatedFromEventPayload = getRequestFromPayload(requestUpdatedLogEvent, "updated");

    assertThat(requestCreatedFromEventPayload.getRequestType(), equalTo(originalCreatedFromEventPayload.getRequestType()));
    assertThat(originalCreatedFromEventPayload.getRequestType(), not(equalTo(updatedCreatedFromEventPayload.getRequestType())));

    assertThat(dueDateChangedEvent, isValidLoanDueDateChangedEvent(updatedLoan));
  }

  private Request getRequestFromPayload(JsonObject logEvent, String created) {
    return Request.from(new JsonObject(logEvent.getString("eventPayload"))
      .getJsonObject("payload").getJsonObject("requests").getJsonObject(created));
  }

  @Test
  void cannotUpdatePatronComments() {
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final IndividualResource steve = usersFixture.steve();

    final IndividualResource createdRequest = requestsClient.create(new RequestBuilder()
      .page()
      .withRequestDate(ClockUtil.getZonedDateTime())
      .forItem(temeraire)
      .by(steve)
      .fulfillToHoldShelf()
      .withPatronComments("Original patron comments")
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    final var replaceResponse = requestsClient.attemptReplace(createdRequest.getId(),
      RequestBuilder.from(createdRequest).withPatronComments("updated patron comments"));

    assertThat(replaceResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Patron comments are not allowed to change"),
      hasParameter("existingPatronComments", "Original patron comments"))));
  }

  @Test
  void shouldUseCurrentUserIdAsSourceWhenSendingUpdateRequestMessage() {
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    var originalRequest = requestsClient.create(
      new RequestBuilder()
        .page()
        .forItem(temeraire)
        .by(steve)
        .withRequestDate(ZonedDateTime.now())
        .withPickupServicePointId(exampleServicePoint.getId()));

    final IndividualResource newServicePoint = servicePointsFixture.cd2();

    requestsClient.replace(originalRequest.getId(),
      RequestBuilder.from(originalRequest)
        .withPickupServicePointId(newServicePoint.getId()));

    final var publishedEvents = Awaitility.await()
      .atMost(1, SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(2));

    final var requestUpdatedLogEvent = publishedEvents.findFirst(byLogEventType(REQUEST_UPDATED.value()));
    var resultStr = requestUpdatedLogEvent.getString("eventPayload");
    var userIdPath = "$['payload']['requests']['updated']['metadata']['updatedByUserId']";
    var userId = JsonPath.parse(resultStr).read(userIdPath);

    assertThat(userId, Matchers.equalTo(APITestContext.getUserId()));
  }

  @Test
  void editingRecallTlrShouldNotChangeRecalledItem() {
    settingsFixture.enableTlrFeature();

    IndividualResource patron1 = usersFixture.steve();
    IndividualResource patron2 = usersFixture.rebecca();
    IndividualResource patron3 = usersFixture.jessica();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    // Step 1 - An instance with two items (Item A & B). Check out item A. Check out item B.
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);

    ItemResource itemA = items.get(0);
    ItemResource itemB = items.get(1);
    final UUID instanceId = itemA.getInstanceId();

    checkOutFixture.checkOutByBarcode(itemA, patron1);
    checkOutFixture.checkOutByBarcode(itemB, patron2);

    // Step 2 - Create TLR recall request from Inventory (itemA is recalled)
    IndividualResource recallTlr = requestsFixture.placeTitleLevelRecallRequest(instanceId,
      patron3);

    assertThat(recallTlr.getJson().getString("itemId"), is(itemA.getId().toString()));

    // Step 3 - Go to request queue on the instance (verify that itemA is shown)
    JsonObject queueOnStep3 = requestQueueFixture.retrieveQueueForInstance(instanceId.toString());
    assertThat(queueOnStep3.getString("totalRecords"), is("1"));
    assertThat(queueOnStep3, hasJsonPath("requests[0].item.barcode", itemA.getBarcode()));

    // Step 4 - Go to Request detail - Pay attention to item barcode under Item information
    // accordion. (Item A)
    var recallTlrUpdated = requestsFixture.getById(recallTlr.getId()).getJson();
    assertThat(recallTlrUpdated.getString("requesterId"), is(patron3.getId().toString()));
    assertThat(recallTlrUpdated.getString("requestType"), is("Recall"));
    assertThat(recallTlrUpdated.getString("requestLevel"), is("Title"));
    assertThat(recallTlrUpdated.getString("position"), is("1"));
    JsonObject itemDetails = recallTlrUpdated.getJsonObject("item");
    assertThat(itemDetails.getString("barcode"), is(itemA.getBarcode()));

    // Step 5 - Go to Item A - verify that there is a "1" under Requests that links back to request
    // detail - nothing to check on BE side
    // Step 6 - Go to Circulation log - verify that Item A is recalled from current borrower -
    // nothing to check on BE side

    // Step 7 - At Request detail: Actions - Edit:
    // Step 8 Change something in the request (e.g. Request expiration date or Pickup service point)
    requestsFixture.replaceRequest(recallTlr.getId(), RequestBuilder.from(recallTlr)
      .withRequestDate(ZonedDateTime.now())
      .withPickupServicePointId(servicePointsFixture.cd2().getId()));

    // Expected results:
    // 1. The same item (itemA) is shown at Item information accordion
    var expectedQueue = requestQueueFixture.retrieveQueueForInstance(instanceId.toString());
    assertThat(expectedQueue, hasJsonPath("requests[0].item.barcode", itemA.getBarcode()));

    // 2. The patron that has itemA on loan SHOULD NOT be able to renew their loan as it's recalled
    Response itemARenewalResponse = loansFixture.attemptRenewal(itemA, patron1);
    assertThat(itemARenewalResponse.getJson(), hasJsonPath("errors[0].message",
      "items cannot be renewed when there is an active recall request"));
    assertThat(itemARenewalResponse.getJson().getJsonArray("errors").size(), is(1));

    // 3. The patron that has Item B on loan SHOULD be able to renew their loan
    var itemBRenewalResponse = loansFixture.renewLoan(itemB, patron2);
    JsonObject itemBRenewalResponseJson = itemBRenewalResponse.getJson();
    assertThat(itemBRenewalResponseJson.getString("action"), is("renewed"));
    assertThat(itemBRenewalResponseJson.getString("userId"), is(patron2.getId().toString()));
    assertThat(itemBRenewalResponseJson.getString("itemId"), is(itemB.getId().toString()));
    assertThat(itemBRenewalResponseJson.getString("renewalCount"), is("1"));
  }
}
