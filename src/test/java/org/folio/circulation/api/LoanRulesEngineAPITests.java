package org.folio.circulation.api;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.loanrules.*;
import org.folio.circulation.resources.LoanRulesEngineResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.folio.circulation.api.support.http.InterfaceUrls.loanRulesURL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class LoanRulesEngineAPITests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  OkapiHttpClient client = APITestSuite.createClient(exception -> {
    log.error("Request to circulation module failed:", exception);
  });

  private void setRules(String rules) {
    JsonObject json = new JsonObject();
    json.put("loanRulesAsTextFile", rules);
    CompletableFuture<Response> completed = new CompletableFuture<>();
    client.put(loanRulesURL(), json, ResponseHandler.any(completed));
    try {
      completed.get(5, TimeUnit.SECONDS);
      LoanRulesEngineResource.clearCache();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private LoanPolicy apply(ItemType itemType, LoanType loanType,
      PatronGroup patronGroup, ShelvingLocation shelvingLocation) {
    try {
      CompletableFuture<Response> completed = new CompletableFuture<>();
      URL url = loanRulesURL(
          "/apply"
          + "?item_type_id="         + itemType.id
          + "&loan_type_id="         + loanType.id
          + "&patron_type_id="       + patronGroup.id
          + "&shelving_location_id=" + shelvingLocation.id
          );
      client.get(url, ResponseHandler.any(completed));
      Response response = completed.get(10, TimeUnit.SECONDS);
      JsonObject json = new JsonObject(response.getBody());
      String loanPolicyId = json.getString("loanPolicyId");
      assert loanPolicyId != null;
      return new LoanPolicy(loanPolicyId);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  ItemType m1 = new ItemType("aaaaaaaa-1111-4b5e-a7bd-064b8d177231");
  ItemType m2 = new ItemType("aaaaaaaa-2222-4b5e-a7bd-064b8d177231");
  LoanType t1 = new LoanType("bbbbbbbb-1111-4b5e-a7bd-064b8d177231");
  LoanType t2 = new LoanType("bbbbbbbb-2222-4b5e-a7bd-064b8d177231");
  PatronGroup g1 = new PatronGroup("cccccccc-1111-4b5e-a7bd-064b8d177231");
  PatronGroup g2 = new PatronGroup("cccccccc-2222-4b5e-a7bd-064b8d177231");
  ShelvingLocation s1 = new ShelvingLocation("dddddddd-1111-4b5e-a7bd-064b8d177231");
  ShelvingLocation s2 = new ShelvingLocation("dddddddd-2222-4b5e-a7bd-064b8d177231");
  LoanPolicy p1 = new LoanPolicy("ffffffff-1111-4b5e-a7bd-064b8d177231");
  LoanPolicy p2 = new LoanPolicy("ffffffff-2222-4b5e-a7bd-064b8d177231");
  LoanPolicy p3 = new LoanPolicy("ffffffff-3333-4b5e-a7bd-064b8d177231");
  LoanPolicy p4 = new LoanPolicy("ffffffff-4444-4b5e-a7bd-064b8d177231");

  @Test
  public void fallback() throws Exception {
    setRules("fallback-policy: " + p1);
    assertThat(apply(m1, t1, g1, s1), is(p1));
  }

  private String rules1 = String.join("\n",
      "fallback-policy: " + p2,
      "m " + m1 + ": " + p3,
      "    g " + g1 + ": " + p4
      );

  @Test
  public void test1() throws Exception {
    setRules(rules1);
    assertThat(apply(m1, t1, g1, s1), is(p4));
    assertThat(apply(m1, t1, g2, s1), is(p3));
    assertThat(apply(m2, t1, g2, s1), is(p2));
  }
}
