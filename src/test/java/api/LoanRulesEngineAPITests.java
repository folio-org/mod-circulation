package api;

import static api.support.http.InterfaceUrls.loanRulesUrl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;

import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.loanrules.ItemType;
import org.folio.circulation.loanrules.LoanPolicy;
import org.folio.circulation.loanrules.LoanType;
import org.folio.circulation.loanrules.PatronGroup;
import org.folio.circulation.loanrules.ShelvingLocation;
import org.folio.circulation.resources.LoanRulesEngineResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.http.InterfaceUrls;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class LoanRulesEngineAPITests extends APITests {
  private void setRules(String rules) {
    JsonObject json = new JsonObject();
    json.put("loanRulesAsTextFile", rules);
    CompletableFuture<Response> completed = new CompletableFuture<>();
    client.put(InterfaceUrls.loanRulesUrl(), json, ResponseHandler.any(completed));
    try {
      completed.get(5, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private LoanPolicy apply(ItemType itemType, LoanType loanType,
      PatronGroup patronGroup, ShelvingLocation shelvingLocation) {
    try {
      CompletableFuture<Response> completed = new CompletableFuture<>();
      URL url = loanRulesUrl(
          "/apply"
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
      return new LoanPolicy(loanPolicyId);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  //Test data to work with our defined values
  ItemType m1 = new ItemType("96d4bdf1-5fc2-40ef-9ace-6d7e3e48ec4d");
  ItemType m2 = new ItemType("b6375fcb-caaf-4b94-944d-b1a6bb589425");
  LoanType t1 = new LoanType("2e6f51b9-d00a-4f1d-9960-49b1977acfca");
  LoanType t2 = new LoanType("220e2dad-e3c7-42f3-bb46-515ba29ba65f");
  PatronGroup g1 = new PatronGroup("0122feae-bd0e-4405-88de-525d93ba7cfd");
  PatronGroup g2 = new PatronGroup("87d14197-6de3-4ba5-9201-6c4129504adf");
  ShelvingLocation s1 = new ShelvingLocation("cdc0b09d-dd56-4377-ae10-a20b50121dc4");
  ShelvingLocation s2 = new ShelvingLocation("fe91de23-6bf5-4179-a90e-3e87769af86e");
  LoanPolicy p6 = new LoanPolicy("6a475259-8a97-4992-a415-76440f5f7c23");
  LoanPolicy p7 = new LoanPolicy("7b586360-8ba8-4aa3-b526-875510608d34");
  LoanPolicy p1 = new LoanPolicy("f6f88da8-2aaf-48c7-944e-0de3f4cc2368");
  LoanPolicy p2 = new LoanPolicy("c42e3a01-eb61-4edd-8cb0-8c7ecc0b4ca2");
  LoanPolicy p3 = new LoanPolicy("f0c8d755-0e56-4d38-9a45-9cd9248b1ae8");
  LoanPolicy p4 = new LoanPolicy("0122feae-bd0e-4405-88de-525d93ba7cfd");

  private String rulesFallback =  "priority: t, s, c, b, a, m, g\nfallback-policy: " + p6;
  private String rulesFallback2 = "priority: t, s, c, b, a, m, g\nfallback-policy: " + p7;

  private String rules1 = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy: " + p2,
      "m " + m2 + ": " + p3,
      "    g " + g2 + ": " + p4
      );

  private String rules2 = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy: " + p6,
      "m " + m1 + ": " + p1,
      "m " + m1 + " + t " + t1 + " : " + p2,
      "m " + m1 + " + t " + t1 + " + g " + g1 + " : " + p3
      );

  @Before
  public void setUp() {
    LoanRulesEngineResource.dropCache();
    LoanRulesEngineResource.setCacheTime(1000000, 1000000);  // 1000 seconds
  }

  @Test
  public void applyWithoutParameters() throws Exception {
    CompletableFuture<Response> completed = new CompletableFuture<>();
    URL url = loanRulesUrl("/apply");
    client.get(url, ResponseHandler.any(completed));
    Response response = completed.get(10, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(400));
  }

  private void applyOneParameterMissing(String p1, String p2, String p3, String missing) throws Exception {
    String name = missing.substring(0, missing.indexOf("="));
    CompletableFuture<Response> completed = new CompletableFuture<>();
    URL url = loanRulesUrl("/apply?" + p1 + "&" + p2 + "&" + p3);
    client.get(url, ResponseHandler.any(completed));
    Response response = completed.get(10, TimeUnit.SECONDS);
    assertThat(response.getStatusCode(), is(400));
    assertThat(response.getBody(), containsString(name));
  }

  @Test
  public void applyOneParameterMissing() throws Exception {
    String [] p = {
        "item_type_id=" + m1,
        "loan_type_id=" + t1,
        "patron_type_id=" + p1,
        "shelving_location_id=" + s1
    };

    applyOneParameterMissing(p[1], p[2], p[3],  p[0]);
    applyOneParameterMissing(p[0], p[2], p[3],  p[1]);
    applyOneParameterMissing(p[0], p[1], p[3],  p[2]);
    applyOneParameterMissing(p[0], p[1], p[2],  p[3]);
  }

  private void applyInvalidUuid(String i, String l, String p, String s) {
    CompletableFuture<Response> completed = new CompletableFuture<>();
    URL url = loanRulesUrl("/apply"
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
    applyInvalidUuid( uuid, t1.id, p1.id, s1.id);
    applyInvalidUuid(m1.id,  uuid, p1.id, s1.id);
    applyInvalidUuid(m1.id, t1.id,  uuid, s1.id);
    applyInvalidUuid(m1.id, t1.id, p1.id,  uuid);
  }

  @Test
  public void applyInvalidUuid() {
    applyInvalidUuid("");
    applyInvalidUuid("0");
    applyInvalidUuid("f");
    applyInvalidUuid("-");
    applyInvalidUuid(  "0000000-0000-1000-8000-000000000000");
    applyInvalidUuid( "00000000-0000-1000-8000-00000000000");
    applyInvalidUuid("000000000-0000-1000-8000-000000000000");
    applyInvalidUuid( "00000000-0000-1000-8000-0000000000000");
    applyInvalidUuid( "00000000-0000-0000-8000-000000000000");
    applyInvalidUuid( "g0000000-0000-1000-0000-000000000000");
    applyInvalidUuid( "00000000-0000-1000-8000-00000000000g");
    applyInvalidUuid( "00000000000010008000000000000000");
  }

  @Test
  public void fallback() {
    setRules(rulesFallback);
    assertThat(apply(m1, t1, g1, s1), is(p6));
  }

  @Test
  public void test1() {
    setRules(rules1);
    assertThat(apply(m2, t2, g2, s2), is(p4));
    assertThat(apply(m2, t2, g1, s2), is(p3));
    assertThat(apply(m1, t2, g1, s2), is(p2));
  }

  @Test
  public void test2() {
    setRules(rules2);
    assertThat(apply(m2, t2, g2, s2), is(p6));
    assertThat(apply(m1, t2, g2, s2), is(p1));
    assertThat(apply(m1, t1, g2, s2), is(p2));
    assertThat(apply(m1, t1, g1, s2), is(p3));
  }

  private void matches(JsonArray array, int match, LoanPolicy policy, int line) {
    JsonObject o = array.getJsonObject(match);
    assertThat("["+match+"].loanPolicyId of "+o, o.getString("loanPolicyId"), is(policy.id));
    assertThat("["+match+"].loanRuleLine of "+o, o.getInteger("loanRuleLine"), is(line));
  }

  @Test
  public void test1ApplyAll() throws Exception {
    setRules(rules1);
    CompletableFuture<Response> completed = new CompletableFuture<>();
    URL url = loanRulesUrl(
        "/apply-all"
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
    JsonArray array = json.getJsonArray("loanRuleMatches");
    matches(array, 0, p4, 4);
    matches(array, 1, p3, 3);
    matches(array, 2, p2, 2);
    assertThat(array.size(), is(3));
  }

  @Test
  public void setRulesInvalidatesCache() {
    setRules(rulesFallback);
    assertThat(apply(m1, t1, g1, s1), is(p6));
    setRules(rulesFallback2);
    assertThat(apply(m1, t1, g1, s1), is(p7));
    setRules(rulesFallback);
    assertThat(apply(m1, t1, g1, s1), is(p6));
    setRules(rulesFallback2);
    assertThat(apply(m1, t1, g1, s1), is(p7));
  }

  @Test
  public void cache() throws Exception {
    setRules(rulesFallback);
    assertThat(apply(m1, t1, g1, s1), is(p6));

    // change loan rules in the storage backend without
    // invalidating the cache
    APITestSuite.setLoanRules(rulesFallback2);
    assertThat(apply(m1, t1, g1, s1), is(p6));

    // reduce cache time to trigger reload from storage backend
    LoanRulesEngineResource.setCacheTime(0, 0);
    assertThat(apply(m1, t1, g1, s1), is(p7));
  }
}
