package api.loans;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.LoanBuilder;
import api.support.http.InterfaceUrls;
import io.vertx.core.json.JsonObject;

public class LoanAPIPolicyTests extends APITests {
  private static UUID lpFallback;
  private static UUID rpFallback;
  private static UUID npFallback;

  private static UUID lp1;
  private static UUID lp2;
  private static UUID lp3;
  private static UUID lp4;

  public LoanAPIPolicyTests() {
    super(false);
  }

  //TODO: Split into multiple tests
  @Test
  public void canRetrieveLoanPolicyId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {


    final UUID readingRoom = loanTypesFixture.readingRoom().getId();

    JsonObject itemJson1 = itemsFixture.basedUponInterestingTimes().getJson();
    JsonObject itemJson2 = itemsFixture.basedUponDunkirk().getJson();
    JsonObject itemJson3 = itemsFixture.basedUponInterestingTimes().getJson();
    JsonObject itemJson4 = itemsFixture.basedUponDunkirk().getJson();
    JsonObject itemJson5 = itemsFixture.basedUponTemeraire(
      builder -> builder.withTemporaryLoanType(readingRoom)).getJson();

    final IndividualResource alternative = patronGroupsFixture.alternative();

    JsonObject user1 = usersFixture.jessica().getJson();

    JsonObject user2 = usersFixture.charlotte(
      userBuilder -> userBuilder.inGroupFor(alternative))
      .getJson();

    UUID group1 = UUID.fromString(user1.getString("patronGroup"));

    UUID itemId1 = UUID.fromString(itemJson1.getString("id"));
    UUID itemId2 = UUID.fromString(itemJson2.getString("id"));
    UUID itemId3 = UUID.fromString(itemJson3.getString("id"));
    UUID itemId4 = UUID.fromString(itemJson4.getString("id"));
    UUID itemId5 = UUID.fromString(itemJson5.getString("id"));

    createLoanPolicies();

    //Set the loan rules
    final UUID book = materialTypesFixture.book().getId();
    final UUID videoRecording = materialTypesFixture.videoRecording().getId();

    final UUID canCirculate = loanTypesFixture.canCirculate().getId();

    String rules = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy: l " + lpFallback,
      "fallback-policy: r " + rpFallback,
      "fallback-policy: n " + npFallback,
      "m " + videoRecording + " + g " + group1 + " : l " + lp1,
      "m " + book + " + t " + canCirculate + " : l " + lp2,
      "m " + book + " + t " + readingRoom + " : l " + lp3,
      "m " + book + " + t " + canCirculate + " + g " + group1 + " : l " + lp4);

    loanRulesFixture.updateLoanRules(rules);

    //Get the loan rules
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(InterfaceUrls.loanRulesUrl(), ResponseHandler.any(getCompleted));
    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    JsonObject rulesJson = new JsonObject(getResponse.getBody());

    String loanRules = rulesJson.getString("loanRulesAsTextFile");
    assertThat("Returned rules match submitted rules", loanRules, is(rules));

    warmUpApplyEndpoint();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);

    testLoanPolicy(UUID.randomUUID(), UUID.fromString(user1.getString("id")),
      itemId1, loanDate, dueDate, "Open", "Policy 4");

    assertThat("We have different group ids", user1.getString("patronGroup"),
      not(user2.getString("patronGroup")));

    testLoanPolicy(UUID.randomUUID(), UUID.fromString(user2.getString("id")),
      itemId3, loanDate, dueDate, "Open", "Policy 2");

    testLoanPolicy(UUID.randomUUID(), UUID.fromString(user1.getString("id")),
      itemId2, loanDate, dueDate, "Open", "Policy 1");

    testLoanPolicy(UUID.randomUUID(), UUID.fromString(user2.getString("id")),
      itemId4, loanDate, dueDate, "Open", "Fallback");

    testLoanPolicy(UUID.randomUUID(), UUID.fromString(user1.getString("id")),
      itemId5, loanDate, dueDate, "Open", "Policy 3");
  }

  private void testLoanPolicy(UUID id, UUID userId, UUID itemId, DateTime loanDate,
    DateTime dueDate, String status, String policyName)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    IndividualResource loanResponse = loansClient.create(new LoanBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withStatus(status));

    JsonObject loanJson = loanResponse.getJson();

    JsonObject policyJson = loanPolicyClient.getById(
      UUID.fromString(loanJson.getString("loanPolicyId")))
      .getJson();

    assertThat(policyJson.getString("name"), is(policyName));
  }

  private void createLoanPolicies()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    JsonObject p1Json = new JsonObject()
       .put("name", "Policy 1")
       .put("description", "Policy 1!!!")
       .put("loanable", true)
       .put("renewable", true)
       .put("loansPolicy", new JsonObject()
         .put("profileId", "ROLLING")
         .put("closedLibraryDueDateManagementId", "KEEP_CURRENT_DATE"))
       .put("renewalsPolicy", new JsonObject()
         .put("renewFromId", "CURRENT_DUE_DATE")
         .put("differentPeriod", false));

    lp1 = loanPoliciesFixture.create(p1Json).getId();

    JsonObject p2Json = new JsonObject()
       .put("name", "Policy 2")
       .put("description", "Policy 2!!!")
       .put("loanable", true)
       .put("renewable", true)
       .put("loansPolicy", new JsonObject()
         .put("profileId", "ROLLING")
         .put("closedLibraryDueDateManagementId", "KEEP_CURRENT_DATE"))
       .put("renewalsPolicy", new JsonObject()
         .put("renewFromId", "CURRENT_DUE_DATE")
         .put("differentPeriod", false));

    lp2 = loanPoliciesFixture.create(p2Json).getId();

    JsonObject p3Json = new JsonObject()
      .put("name", "Policy 3")
      .put("description", "Policy 3!!!")
      .put("loanable", true)
      .put("renewable", true)
      .put("loansPolicy", new JsonObject()
        .put("profileId", "ROLLING")
        .put("closedLibraryDueDateManagementId", "KEEP_CURRENT_DATE"))
      .put("renewalsPolicy", new JsonObject()
        .put("renewFromId", "CURRENT_DUE_DATE")
        .put("differentPeriod", false));

    lp3 = loanPoliciesFixture.create(p3Json).getId();

    JsonObject p4Json = new JsonObject()
       .put("name", "Policy 4")
       .put("description", "Policy 4!!!")
       .put("loanable", true)
       .put("renewable", true)
       .put("loansPolicy", new JsonObject()
         .put("profileId", "ROLLING")
         .put("closedLibraryDueDateManagementId", "KEEP_CURRENT_DATE"))
       .put("renewalsPolicy", new JsonObject()
         .put("renewFromId", "CURRENT_DUE_DATE")
         .put("differentPeriod", false));

    lp4 = loanPoliciesFixture.create(p4Json).getId();

    JsonObject lpFallbackJson = new JsonObject()
       .put("name", "Fallback")
       .put("description", "Fallback!!!")
       .put("loanable", true) //Workaround for policy validation in mod-circulation-storage
       .put("renewable", true) //Workaround for policy validation in mod-circulation-storage
       .put("loansPolicy", new JsonObject()
         .put("profileId", "ROLLING")
         .put("closedLibraryDueDateManagementId", "KEEP_CURRENT_DATE"))
       .put("renewalsPolicy", new JsonObject()
         .put("renewFromId", "CURRENT_DUE_DATE")
         .put("differentPeriod", false));

    lpFallback = loanPoliciesFixture.create(lpFallbackJson).getId();
  }
}
