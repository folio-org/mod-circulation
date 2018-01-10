/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.folio.circulation.api.loans;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.api.support.APITests;
import org.folio.circulation.api.support.builders.LoanRequestBuilder;
import org.folio.circulation.api.support.http.InterfaceUrls;
import org.folio.circulation.api.support.http.ResourceClient;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;


/**
 *
 * @author kurt
 */
public class LoanAPIPolicyTests extends APITests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  static UUID pFallback;
  static UUID p1;
  static UUID p2;
  static UUID p3;

  protected static final OkapiHttpClient client = APITestSuite.createClient(exception -> {
    log.error("Request to circulation module failed:", exception);
  });

  public LoanAPIPolicyTests() {
    super(false);
  }

  @Test
  public void canRetrieveLoanPolicyId()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    UnsupportedEncodingException,
    ExecutionException {
    JsonObject itemJson1 = itemsFixture.basedUponInterestingTimes().getJson();
    
    JsonObject user1 = APITestSuite.userRecord1();
    JsonObject user2 = APITestSuite.userRecord2();
    UUID group1 = UUID.fromString(user1.getString("patronGroup"));
    UUID itemId1 = UUID.fromString(itemJson1.getString("id"));
    UUID materialType1 = UUID.fromString(itemJson1.getString("materialTypeId"));
    UUID loanType1 = UUID.fromString(itemJson1.getString("permanentLoanTypeId"));

    createLoanPolicies();

    //Set the loan rules
    String rules = String.join("\n",
      "fallback-policy: " + pFallback,
      "m " + APITestSuite.bookMaterialTypeId() + ": " + p1,
      "m " + materialType1 + " + t " + loanType1 + " : " + p2,
      "m " + materialType1 + " + t " + loanType1 + " + g " + group1 + " : " + p3//,
      );
    JsonObject newRulesRequest = new JsonObject().put("loanRulesAsTextFile", rules);
    CompletableFuture<Response> completed = new CompletableFuture<>();

    client.put(InterfaceUrls.loanRulesUrl(), newRulesRequest,
      ResponseHandler.any(completed));

    Response response = completed.get(5, TimeUnit.SECONDS);

    assertThat(String.format(
      "Failed to set loan rules: %s", response.getBody()),
      response.getStatusCode(), is(204));

    //Get the loan rules
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();
    client.get(InterfaceUrls.loanRulesUrl(), ResponseHandler.any(getCompleted));
    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    JsonObject rulesJson = new JsonObject(getResponse.getBody());

    String loanRules = rulesJson.getString("loanRulesAsTextFile");
    assertThat("Returned rules match submitted rules", loanRules, is(rules));

    System.out.println(String.format("Loan rules: %s", loanRules));

    //Temporarily, wait for as long as the rules take to reload
    TimeUnit.SECONDS.sleep(5);

    warmUpApplyEndpoint();

    DateTime loanDate = new DateTime(2017, 2, 27, 10, 23, 43, DateTimeZone.UTC);
    DateTime dueDate = new DateTime(2017, 3, 29, 10, 23, 43, DateTimeZone.UTC);
    
    testLoanPolicy(UUID.randomUUID(), UUID.fromString(user1.getString("id")),
      itemId1, loanDate, dueDate, "Open", "Policy 3");
    
    assertThat("We have different group ids", user1.getString("patronGroup"), 
      not(user2.getString("patronGroup")));
    
    testLoanPolicy(UUID.randomUUID(), UUID.fromString(user2.getString("id")),
      itemId1, loanDate, dueDate, "Open", "Policy 2");
    
    
    
  }
  
  private void testLoanPolicy(UUID id, UUID userId, UUID itemId, DateTime loanDate,
    DateTime dueDate, String status, String policyName)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {
    IndividualResource loanResponse = loansClient.create(new LoanRequestBuilder()
      .withId(id)
      .withUserId(userId)
      .withItemId(itemId)
      .withLoanDate(loanDate)
      .withDueDate(dueDate)
      .withStatus(status));

    JsonObject loanJson = loanResponse.getJson();
    ResourceClient policyResourceClient = ResourceClient.forLoanPolicies(client);
    JsonObject policyJson = policyResourceClient.getById(UUID.fromString(loanJson.getString("loanPolicyId"))).getJson();
    assertThat("policy is third policy", policyJson.getString("name"), is(policyName));
  }

  private static void createLoanPolicies()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    ResourceClient policyResourceClient = ResourceClient.forLoanPolicies(client);

    //policyResourceClient.deleteAll(); //Clear existing

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

    p1 = policyResourceClient.create(p1Json).getId();

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

    p2 = policyResourceClient.create(p2Json).getId();

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

    p3 = policyResourceClient.create(p3Json).getId();

    JsonObject pFallbackJson = new JsonObject()
       .put("name", "Fallback")
       .put("description", "Fallback!!!")
       .put("loanable", false)
       .put("renewable", false)
       .put("loansPolicy", new JsonObject()
         .put("profileId", "ROLLING")
         .put("closedLibraryDueDateManagementId", "KEEP_CURRENT_DATE"))
       .put("renewalsPolicy", new JsonObject()
         .put("renewFromId", "CURRENT_DUE_DATE")
         .put("differentPeriod", false));

    pFallback = policyResourceClient.create(pFallbackJson).getId();

  }

  private static void deleteLoanPolicies()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    ResourceClient policyResourceClient = ResourceClient.forLoanPolicies(client);
    policyResourceClient.delete(p1);
    policyResourceClient.delete(p2);
    policyResourceClient.delete(p3);
    policyResourceClient.delete(pFallback);
  }
}
