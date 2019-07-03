package api;

import static api.support.http.InterfaceUrls.circulationRulesUrl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.resources.LoanCirculationRulesEngineResource;
import org.folio.circulation.rules.ItemType;
import org.folio.circulation.rules.LoanType;
import org.folio.circulation.rules.PatronGroup;
import org.folio.circulation.rules.Policy;
import org.folio.circulation.rules.ShelvingLocation;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CirculationRulesEngineAPITests extends APITests {
  private void setRules(String rules) {
    try {
      circulationRulesFixture.updateCirculationRules(rules);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Policy applyLoanPolicy(ItemType itemType, LoanType loanType,
      PatronGroup patronGroup, ShelvingLocation shelvingLocation) {
    try {
      CompletableFuture<Response> completed = new CompletableFuture<>();
      URL url = circulationRulesUrl(
          "/loan-policy"
          + "?item_type_id="         + itemType.id
          + "&loan_type_id="         + loanType.id
          + "&patron_type_id="       + patronGroup.id
          + "&shelving_location_id=" + shelvingLocation.id
          );
      client.get(url, ResponseHandler.any(completed));
      Response response = completed.get(10, TimeUnit.SECONDS);
      assert response.getStatusCode() == 200;
      JsonObject json = new JsonObject(response.getBody());
      String loanPolicyId = json.getString("loanPolicyId");
      assert loanPolicyId != null;
      return new Policy(loanPolicyId);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Policy applyRequestPolicy(ItemType itemType, String requestType,
      PatronGroup patronGroup, ShelvingLocation shelvingLocation) {
    try {
      CompletableFuture<Response> completed = new CompletableFuture<>();
      URL url = circulationRulesUrl(
          "/request-policy"
          + "?item_type_id="         + itemType.id
          + "&loan_type_id="         + requestType
          + "&patron_type_id="       + patronGroup.id
          + "&shelving_location_id=" + shelvingLocation.id
          );
      client.get(url, ResponseHandler.any(completed));
      Response response = completed.get(10, TimeUnit.SECONDS);
      assert response.getStatusCode() == 200;
      JsonObject json = new JsonObject(response.getBody());
      String loanPolicyId = json.getString("requestPolicyId");
      assert loanPolicyId != null;
      return new Policy(loanPolicyId);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Policy applyNoticePolicy(ItemType itemType, String noticeType,
          PatronGroup patronGroup, ShelvingLocation shelvingLocation) {
        try {
          CompletableFuture<Response> completed = new CompletableFuture<>();
          URL url = circulationRulesUrl(
              "/notice-policy"
              + "?item_type_id="         + itemType.id
              + "&loan_type_id="         + noticeType
              + "&patron_type_id="       + patronGroup.id
              + "&shelving_location_id=" + shelvingLocation.id
              );
          client.get(url, ResponseHandler.any(completed));
          Response response = completed.get(10, TimeUnit.SECONDS);
          assert response.getStatusCode() == 200;
          JsonObject json = new JsonObject(response.getBody());
          String noticePolicyId = json.getString("noticePolicyId");
          assert noticePolicyId != null;
          return new Policy(noticePolicyId);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

  //Test data to work with our defined values
  private ItemType m1 = new ItemType("96d4bdf1-5fc2-40ef-9ace-6d7e3e48ec4d");
  private ItemType m2 = new ItemType("b6375fcb-caaf-4b94-944d-b1a6bb589425");
  private LoanType t1 = new LoanType("2e6f51b9-d00a-4f1d-9960-49b1977acfca");
  private LoanType t2 = new LoanType("220e2dad-e3c7-42f3-bb46-515ba29ba65f");
  private PatronGroup g1 = new PatronGroup("0122feae-bd0e-4405-88de-525d93ba7cfd");
  private PatronGroup g2 = new PatronGroup("87d14197-6de3-4ba5-9201-6c4129504adf");
  private ShelvingLocation s1 = new ShelvingLocation("cdc0b09d-dd56-4377-ae10-a20b50121dc4");
  private ShelvingLocation s2 = new ShelvingLocation("fe91de23-6bf5-4179-a90e-3e87769af86e");
  // Loan Policies
  private Policy lp6 = new Policy("6a475259-8a97-4992-a415-76440f5f7c23");
  private Policy lp7 = new Policy("7b586360-8ba8-4aa3-b526-875510608d34");
  private Policy lp1 = new Policy("f6f88da8-2aaf-48c7-944e-0de3f4cc2368");
  private Policy lp2 = new Policy("c42e3a01-eb61-4edd-8cb0-8c7ecc0b4ca2");
  private Policy lp3 = new Policy("f0c8d755-0e56-4d38-9a45-9cd9248b1ae8");
  private Policy lp4 = new Policy("0122feae-bd0e-4405-88de-525d93ba7cfd");
  // Request Policies
  private Policy rp1 = new Policy("52069c78-75f6-4de5-bec0-85b87fbf9414");
  private Policy rp2 = new Policy("921d07d7-2869-4add-9cdd-98a1c4b9de64");
  // Notice Policies
  private Policy np1 = new Policy("662cb440-c66b-46ba-b70f-7ebff4026644");
  private Policy np2 = new Policy("b6bcddac-7c6c-4b29-a6fe-4627ef78c782");

  private String rulesFallback =  "priority: t, m, g\nfallback-policy: l " + lp6 + " r " + rp1 + " n " + np1;
  private String rulesFallback2 = "priority: t, m, g\nfallback-policy: l " + lp7 + " r " + rp2 + " n " + np2;

  private String rules1 = String.join("\n",
      "priority: t, m, g",
      "fallback-policy: l " + lp2 + " r " + rp1 + " n " + np1,
      "m " + m2 + ": l " + lp3 + " r " + rp1 + " n " + np1,
      "    g " + g2 + ": l " + lp4 + " r " + rp1 + " n " + np1
      );

  private String rules2 = String.join("\n",
      "priority: t, m, g",
      "fallback-policy: l " + lp6 + " r " + rp1 + " n " + np1,
      "m " + m1 + ": l " + lp1 + " r " + rp1 + " n " + np1,
      "m " + m1 + " + t " + t1 + " : l " + lp2 + " r " + rp1 + " n " + np1,
      "m " + m1 + " + t " + t1 + " + g " + g1 + " : l " + lp3 + " r " + rp1 + " n " + np1
      );

  @Before
  public void setUp() {
    LoanCirculationRulesEngineResource.dropCache();
    LoanCirculationRulesEngineResource.setCacheTime(1000000, 1000000);  // 1000 seconds
  }

  @Test
  public void applyLoanWithoutParameters() throws Exception {
    CompletableFuture<Response> completed = new CompletableFuture<>();
    URL url = circulationRulesUrl("/loan-policy");
    client.get(url, ResponseHandler.any(completed));
    Response response = completed.get(10, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(400));
  }

  @Test
  public void applyRequestWithoutParameters() throws Exception {
    CompletableFuture<Response> completed = new CompletableFuture<>();
    URL url = circulationRulesUrl("/request-policy");
    client.get(url, ResponseHandler.any(completed));
    Response response = completed.get(10, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(400));
  }

  @Test
  public void applyNoticeWithoutParameters() throws Exception {
    CompletableFuture<Response> completed = new CompletableFuture<>();
    URL url = circulationRulesUrl("/notice-policy");
    client.get(url, ResponseHandler.any(completed));
    Response response = completed.get(10, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(400));
  }

  private void applyOneLoanParameterMissing(String p1, String p2, String p3, String missing) throws Exception {
    String name = missing.substring(0, missing.indexOf("="));
    CompletableFuture<Response> completed = new CompletableFuture<>();
    URL url = circulationRulesUrl("/loan-policy?" + p1 + "&" + p2 + "&" + p3);
    client.get(url, ResponseHandler.any(completed));
    Response response = completed.get(10, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString(name));
  }

  private void applyOneRequestParameterMissing(String p1, String p2, String p3, String missing) throws Exception {
    String name = missing.substring(0, missing.indexOf("="));
    CompletableFuture<Response> completed = new CompletableFuture<>();
    URL url = circulationRulesUrl("/request-policy?" + p1 + "&" + p2 + "&" + p3);
    client.get(url, ResponseHandler.any(completed));
    Response response = completed.get(10, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString(name));
  }

  private void applyOneNoticeParameterMissing(String p1, String p2, String p3, String missing) throws Exception {
      String name = missing.substring(0, missing.indexOf("="));
      CompletableFuture<Response> completed = new CompletableFuture<>();
      URL url = circulationRulesUrl("/notice-policy?" + p1 + "&" + p2 + "&" + p3);
      client.get(url, ResponseHandler.any(completed));
      Response response = completed.get(10, TimeUnit.SECONDS);
      assertThat(response.getStatusCode(), is(400));
      assertThat(response.getBody(), containsString(name));
    }

  @Test
  public void applyOneLoanParameterMissing() throws Exception {
    String [] p = {
        "item_type_id=" + m1,
        "loan_type_id=" + t1,
        "patron_type_id=" + lp1,
        "shelving_location_id=" + s1
    };

    applyOneLoanParameterMissing(p[1], p[2], p[3],  p[0]);
    applyOneLoanParameterMissing(p[0], p[2], p[3],  p[1]);
    applyOneLoanParameterMissing(p[0], p[1], p[3],  p[2]);
    applyOneLoanParameterMissing(p[0], p[1], p[2],  p[3]);
  }

  @Test
  public void applyOneRequestParameterMissing() throws Exception {
    String[] p = {
        "item_type_id=" + m1,
        "loan_type_id=" + t1,
        "patron_type_id=" + lp1,
        "shelving_location_id=" + s1
    };

    applyOneRequestParameterMissing(p[1], p[2], p[3],  p[0]);
    applyOneRequestParameterMissing(p[0], p[2], p[3],  p[1]);
    applyOneRequestParameterMissing(p[0], p[1], p[3],  p[2]);
    applyOneRequestParameterMissing(p[0], p[1], p[2],  p[3]);
  }

  @Test
  public void applyOneNoticeParameterMissing() throws Exception {
    String[] p = {
        "item_type_id=" + m1,
        "loan_type_id=" + t1,
        "patron_type_id=" + lp1,
        "shelving_location_id=" + s1
    };

    applyOneNoticeParameterMissing(p[1], p[2], p[3],  p[0]);
    applyOneNoticeParameterMissing(p[0], p[2], p[3],  p[1]);
    applyOneNoticeParameterMissing(p[0], p[1], p[3],  p[2]);
    applyOneNoticeParameterMissing(p[0], p[1], p[2],  p[3]);
  }

  private void applyInvalidUuid(String i, String l, String p, String s, String type) {
    CompletableFuture<Response> completed = new CompletableFuture<>();
    URL url = circulationRulesUrl("/" + type + "-policy"
        + "?item_type_id=" + i
        + "&loan_type_id=" + l
        + "&patron_type_id=" + p
        + "&shelving_location_id=" + s);
    client.get(url, ResponseHandler.any(completed));
    Response response;
    try {
      response = completed.get(10, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException (e);
    }
    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString("uuid"));
  }

  private void applyInvalidUuid(String uuid) {
    applyInvalidUuid( uuid, t1.id, lp1.id, s1.id, "loan");
    applyInvalidUuid(m1.id,  uuid, lp1.id, s1.id, "loan");
    applyInvalidUuid(m1.id, t1.id,  uuid,  s1.id, "loan");
    applyInvalidUuid(m1.id, t1.id, lp1.id,  uuid, "loan");

    applyInvalidUuid( uuid, t1.id, lp1.id, s1.id, "request");
    applyInvalidUuid(m1.id,  uuid, lp1.id, s1.id, "request");
    applyInvalidUuid(m1.id, t1.id,  uuid,  s1.id, "request");
    applyInvalidUuid(m1.id, t1.id, lp1.id,  uuid, "request");
  }

  @Test
  public void applyInvalidUuid() {
    applyInvalidUuid("");
    applyInvalidUuid("0");
    applyInvalidUuid("f");
    applyInvalidUuid("-");
    applyInvalidUuid("0000000-0000-1000-8000-000000000000");
    applyInvalidUuid("00000000-0000-1000-8000-00000000000");
    applyInvalidUuid("000000000-0000-1000-8000-000000000000");
    applyInvalidUuid("00000000-0000-1000-8000-0000000000000");
    applyInvalidUuid("00000000-0000-0000-8000-000000000000");
    applyInvalidUuid("g0000000-0000-1000-0000-000000000000");
    applyInvalidUuid("00000000-0000-1000-8000-00000000000g");
    applyInvalidUuid("00000000000010008000000000000000");
  }

  @Test
  public void loanFallback() {
    setRules(rulesFallback);
    assertThat(applyLoanPolicy(m1, t1, g1, s1), is(lp6));
  }

  @Test
  public void requestFallback() {
    setRules(rulesFallback);
    assertThat(applyRequestPolicy(m1, t1.id, g1, s1), is(rp1));
  }

  @Test
  public void noticeFallback() {
    setRules(rulesFallback);
    assertThat(applyNoticePolicy(m1, t1.id, g1, s1), is(np1));
  }

  @Test
  public void test1() {
    setRules(rules1);
    assertThat(applyLoanPolicy(m2, t2, g2, s2), is(lp4));
    assertThat(applyLoanPolicy(m2, t2, g1, s2), is(lp3));
    assertThat(applyLoanPolicy(m1, t2, g1, s2), is(lp2));
  }

  @Test
  public void test2() {
    setRules(rules2);
    assertThat(applyLoanPolicy(m2, t2, g2, s2), is(lp6));
    assertThat(applyLoanPolicy(m1, t2, g2, s2), is(lp1));
    assertThat(applyLoanPolicy(m1, t1, g2, s2), is(lp2));
    assertThat(applyLoanPolicy(m1, t1, g1, s2), is(lp3));
  }

  private void matchesLoanPolicy(JsonArray array, int match, Policy policy, int line) {
    JsonObject o = array.getJsonObject(match);
    assertThat("["+match+"].loanPolicyId of "+o, o.getString("loanPolicyId"), is(policy.id));
    assertThat("["+match+"].circulationRuleLine of "+o, o.getInteger("circulationRuleLine"), is(line));
  }

  @Test
  public void testLoanApplyAll() throws Exception {
    setRules(rules1);
    CompletableFuture<Response> completed = new CompletableFuture<>();
    URL url = circulationRulesUrl(
        "/loan-policy-all"
        + "?item_type_id="         + m2
        + "&loan_type_id="         + t2
        + "&patron_type_id="       + g2
        + "&shelving_location_id=" + s2
        );
    client.get(url, ResponseHandler.any(completed));
    Response response = completed.get(10, TimeUnit.SECONDS);
    assertThat(response.getStatusCode() + " " + response.getBody(),
        response.getStatusCode(), is(200));
    JsonObject json = new JsonObject(response.getBody());
    JsonArray array = json.getJsonArray("circulationRuleMatches");
    matchesLoanPolicy(array, 0, lp4, 4);
    matchesLoanPolicy(array, 1, lp3, 3);
    matchesLoanPolicy(array, 2, lp2, 2);
    assertThat(array.size(), is(3));
  }

  private void matchesRequestPolicy(JsonArray array, int match, Policy policy, int line) {
    JsonObject o = array.getJsonObject(match);
    assertThat("["+match+"].requestPolicyId of "+o, o.getString("requestPolicyId"), is(policy.id));
    assertThat("["+match+"].circulationRuleLine of "+o, o.getInteger("circulationRuleLine"), is(line));
  }

  private void matchesNoticePolicy(JsonArray array, int match, Policy policy, int line) {
    JsonObject o = array.getJsonObject(match);
    assertThat("["+match+"].noticePolicyId of "+o, o.getString("noticePolicyId"), is(policy.id));
    assertThat("["+match+"].circulationRuleLine of "+o, o.getInteger("circulationRuleLine"), is(line));
  }

  @Test
  public void testRequestApplyAll() throws Exception {
    setRules(rules1);
    CompletableFuture<Response> completed = new CompletableFuture<>();
    URL url = circulationRulesUrl(
        "/request-policy-all"
        + "?item_type_id="         + m2
        + "&loan_type_id="         + t2
        + "&patron_type_id="       + g2
        + "&shelving_location_id=" + s2
        );
    client.get(url, ResponseHandler.any(completed));
    Response response = completed.get(10, TimeUnit.SECONDS);
    assertThat(response.getStatusCode() + " " + response.getBody(),
        response.getStatusCode(), is(200));
    JsonObject json = new JsonObject(response.getBody());
    JsonArray array = json.getJsonArray("circulationRuleMatches");
    matchesRequestPolicy(array, 0, rp1, 4);
    matchesRequestPolicy(array, 1, rp1, 3);
    matchesRequestPolicy(array, 2, rp1, 2);
    assertThat(array.size(), is(3));
  }

  @Test
  public void canDetermineAllPatronNoticePolicyMatches() throws Exception {
    setRules(rules1);

    CompletableFuture<Response> completed = new CompletableFuture<>();

    URL url = circulationRulesUrl(
      "/notice-policy-all"
        + "?item_type_id="         + m2
        + "&loan_type_id="         + t2
        + "&patron_type_id="       + g2
        + "&shelving_location_id=" + s2
    );

    client.get(url, ResponseHandler.any(completed));

    Response response = completed.get(10, TimeUnit.SECONDS);
    assertThat(response.getStatusCode() + " " + response.getBody(),
      response.getStatusCode(), is(200));
    JsonObject json = new JsonObject(response.getBody());
    JsonArray array = json.getJsonArray("circulationRuleMatches");

    matchesNoticePolicy(array, 0, np1, 4);
    matchesNoticePolicy(array, 1, np1, 3);
    matchesNoticePolicy(array, 2, np1, 2);

    assertThat(array.size(), is(3));
  }

  @Test
  public void setRulesInvalidatesCache() {
    setRules(rulesFallback);
    assertThat(applyLoanPolicy(m1, t1, g1, s1), is(lp6));
    setRules(rulesFallback2);
    assertThat(applyLoanPolicy(m1, t1, g1, s1), is(lp7));
    setRules(rulesFallback);
    assertThat(applyLoanPolicy(m1, t1, g1, s1), is(lp6));
    setRules(rulesFallback2);
    assertThat(applyLoanPolicy(m1, t1, g1, s1), is(lp7));
  }

  @Test
  public void cache() throws Exception {
    setRules(rulesFallback);
    assertThat(applyLoanPolicy(m1, t1, g1, s1), is(lp6));

    updateCirculationRulesInStorageWithoutInvalidatingCache(rulesFallback2);

    assertThat(applyLoanPolicy(m1, t1, g1, s1), is(lp6));

    // reduce cache time to trigger reload from storage backend
    LoanCirculationRulesEngineResource.setCacheTime(0, 0);

    assertThat(applyLoanPolicy(m1, t1, g1, s1), is(lp7));
  }

  private void updateCirculationRulesInStorageWithoutInvalidatingCache(String rules)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    ResourceClient circulationRulesClient = ResourceClient.forCirculationRules(client);

    JsonObject json = new JsonObject().put("rulesAsText", rules);

    circulationRulesClient.replace(null, json);
  }
}
