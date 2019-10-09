package api.requests;

import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static org.folio.HttpStatus.HTTP_VALIDATION_ERROR;
import static org.folio.circulation.domain.representations.RequestProperties.CANCELLATION_REASON_NAME;
import static org.folio.circulation.domain.representations.RequestProperties.CANCELLATION_REASON_PUBLIC_DESCRIPTION;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.awaitility.Awaitility;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.Address;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class RequestsAPIUpdatingTests extends APITests {

  private static final String REQUEST_CANCELLATION = "Request cancellation";

  @Test
  public void canReplaceAnExistingRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    loansFixture.checkOutByBarcode(temeraire);

    final IndividualResource steve = usersFixture.steve();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
      .recall()
      .withRequestDate(requestDate)
      .forItem(temeraire)
      .by(steve)
      .fulfilToHoldShelf()
      .withPickupServicePointId(exampleServicePoint.getId())
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

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
    assertThat(representation.getString("requesterId"), is(charlotte.getId()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    assertThat("title is taken from item",
      representation.getJsonObject("item").getString("title"),
      is("Temeraire"));

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
  public void canReplaceAnExistingRequestWithDeliveryAddress()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    loansFixture.checkOutByBarcode(temeraire);

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

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(temeraire)
      .by(charlotte)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf(exampleServicePoint.getId()));

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
  public void replacingAnExistingRequestRemovesItemInformationWhenItemDoesNotExist()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(nod);

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
  public void replacingAnExistingRequestRemovesRequesterInformationWhenUserDoesNotExist()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(nod);

    IndividualResource requester = usersFixture.steve();

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
  public void replacingAnExistingRequestRemovesRequesterBarcodeWhenNonePresent()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(nod);

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
  public void replacingAnExistingRequestIncludesRequesterMiddleNameWhenPresent()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOutByBarcode(nod);

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
  public void replacingAnExistingRequestRemovesItemBarcodeWhenNonePresent()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(temeraire);

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

    assertThat("title is taken from item",
      representation.getJsonObject("item").getString("title"),
      is("Temeraire"));

    assertThat("barcode is not taken from item",
      representation.getJsonObject("item").containsKey("barcode"),
      is(false));
  }

  @Test
  public void cannotReplaceAnExistingRequestWithServicePointThatIsNotForPickup()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    loansFixture.checkOutByBarcode(temeraire);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(temeraire)
      .by(usersFixture.steve())
      .fulfilToHoldShelf(exampleServicePoint.getId()));

     UUID badServicePointId = servicePointsFixture.cd3().getId();

    final Response putResponse = requestsClient.attemptReplace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .withPickupServicePointId(badServicePointId));

    assertThat(putResponse.getJson(), hasErrorWith(allOf(
     hasMessage("Service point is not a pickup location"),
     hasUUIDParameter("pickupServicePointId", badServicePointId))));
  }

  @Test
  public void cannotReplaceAnExistingRequestWithUnknownPickupLocation()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    loansFixture.checkOutByBarcode(temeraire);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .forItem(temeraire)
        .by(usersFixture.james())
        .fulfilToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId()));

    UUID badServicePointId = UUID.randomUUID();

    final Response putResponse = requestsClient.attemptReplace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .withPickupServicePointId(badServicePointId));

    assertThat(putResponse, hasStatus(HTTP_VALIDATION_ERROR));

    assertThat(putResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Pickup service point does not exist"),
      hasUUIDParameter("pickupServicePointId", badServicePointId))));
  }

  @Test
  public void cancellationReasonPublicDescriptionIsUsedAsReasonForCancellationToken()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID requestCancellationTemplateId = UUID.randomUUID();
    JsonObject requestCancellationConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(requestCancellationTemplateId)
      .withEventType(REQUEST_CANCELLATION)
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with request cancellation notice")
      .withLoanNotices(Collections.singletonList(requestCancellationConfiguration));
    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId());

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final IndividualResource requester = usersFixture.steve();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();
    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .page()
        .withRequestDate(requestDate)
        .forItem(temeraire)
        .by(requester)
        .fulfilToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    final IndividualResource itemNotAvailable = cancellationReasonsFixture.itemNotAvailable();
    JsonObject updatedRequest = RequestBuilder.from(createdRequest)
      .cancelled()
      .withCancellationReasonId(itemNotAvailable.getId())
      .withCancellationAdditionalInformation("Cancellation info")
      .create();
    requestsClient.replace(createdRequest.getId(), updatedRequest);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronNoticesClient::getAll, Matchers.hasSize(1));
    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(requester));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(temeraire, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getRequestContextMatchers(updatedRequest));
    noticeContextMatchers.putAll(TemplateContextMatchers.getCancelledRequestContextMatchers(updatedRequest));
    noticeContextMatchers.put("request.reasonForCancellation",
      is(itemNotAvailable.getJson().getString(CANCELLATION_REASON_PUBLIC_DESCRIPTION)));
    assertThat(sentNotices,
      hasItems(
        hasEmailNoticeProperties(requester.getId(), requestCancellationTemplateId, noticeContextMatchers)));
  }

  @Test
  public void cancellationReasonNameIsUsedAsReasonForCancellationTokenWhenPublicDescriptionIsNotPresent()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID requestCancellationTemplateId = UUID.randomUUID();
    JsonObject requestCancellationConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(requestCancellationTemplateId)
      .withEventType(REQUEST_CANCELLATION)
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with request cancellation notice")
      .withLoanNotices(Collections.singletonList(requestCancellationConfiguration));
    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId());

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final IndividualResource requester = usersFixture.steve();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();
    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .page()
        .withRequestDate(requestDate)
        .forItem(temeraire)
        .by(requester)
        .fulfilToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    final IndividualResource courseReserves = cancellationReasonsFixture.courseReserves();
    JsonObject updatedRequest = RequestBuilder.from(createdRequest)
      .cancelled()
      .withCancellationReasonId(courseReserves.getId())
      .withCancellationAdditionalInformation("Cancellation info")
      .create();
    requestsClient.replace(createdRequest.getId(), updatedRequest);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronNoticesClient::getAll, Matchers.hasSize(1));
    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(requester));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(temeraire, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getRequestContextMatchers(updatedRequest));
    noticeContextMatchers.putAll(TemplateContextMatchers.getCancelledRequestContextMatchers(updatedRequest));
    noticeContextMatchers.put("request.reasonForCancellation",
      is(courseReserves.getJson().getString(CANCELLATION_REASON_NAME)));
    assertThat(sentNotices,
      hasItems(
        hasEmailNoticeProperties(requester.getId(), requestCancellationTemplateId, noticeContextMatchers)));
  }

  @Test
  public void replacedRequestShouldOnlyIncludeStoredPropertiesInStorage()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    loansFixture.checkOutByBarcode(temeraire);

    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();

    proxyRelationshipsFixture.currentProxyFor(steve, charlotte);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withRequestDate(requestDate)
        .forItem(temeraire)
        .by(steve)
        .proxiedBy(charlotte)
        .fulfilToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

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
}
