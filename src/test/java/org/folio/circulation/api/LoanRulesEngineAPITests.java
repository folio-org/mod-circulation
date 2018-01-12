package org.folio.circulation.api;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.http.InterfaceUrls;
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

import static org.folio.circulation.api.support.http.InterfaceUrls.loanRulesUrl;
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
    client.put(InterfaceUrls.loanRulesUrl(), json, ResponseHandler.any(completed));
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
      URL url = loanRulesUrl(
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
  
  //Test data to work with our defined values
  ItemType mDefault = new ItemType("96d4bdf1-5fc2-40ef-9ace-6d7e3e48ec4d");
  ItemType mVariant = new ItemType("b6375fcb-caaf-4b94-944d-b1a6bb589425");
  LoanType tDefault = new LoanType("2e6f51b9-d00a-4f1d-9960-49b1977acfca");
  LoanType tVariant = new LoanType("220e2dad-e3c7-42f3-bb46-515ba29ba65f");
  PatronGroup gDefault = new PatronGroup("0122feae-bd0e-4405-88de-525d93ba7cfd");
  PatronGroup gVariant = new PatronGroup("87d14197-6de3-4ba5-9201-6c4129504adf");
  ShelvingLocation sDefault = new ShelvingLocation("cdc0b09d-dd56-4377-ae10-a20b50121dc4");
  ShelvingLocation sVariant = new ShelvingLocation("fe91de23-6bf5-4179-a90e-3e87769af86e");
  LoanPolicy pFallback = new LoanPolicy("6a475259-8a97-4992-a415-76440f5f7c23");
  LoanPolicy pFirst = new LoanPolicy("f6f88da8-2aaf-48c7-944e-0de3f4cc2368");
  LoanPolicy pSecond = new LoanPolicy("c42e3a01-eb61-4edd-8cb0-8c7ecc0b4ca2");
  LoanPolicy pThird = new LoanPolicy("f0c8d755-0e56-4d38-9a45-9cd9248b1ae8");
  LoanPolicy pFourth = new LoanPolicy("0122feae-bd0e-4405-88de-525d93ba7cfd");

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
  
  private String rules2 = String.join("\n",
      "fallback-policy: " + pFallback,
      "m " + mDefault + ": " + pFirst,
      "m " + mDefault + " + t " + tDefault + " : " + pSecond,
      "m " + mDefault + " + t " + tDefault + " + g " + gDefault + " : " + pThird//,
      //"m " + mDefault + " + t " + tDefault + " + g " + gDefault + " + s " +  sDefault + " : " + pFourth
      );

  @Test
  public void test1() throws Exception {
    setRules(rules1);
    assertThat(apply(m1, t1, g1, s1), is(p4));
    assertThat(apply(m1, t1, g2, s1), is(p3));
    assertThat(apply(m2, t1, g2, s1), is(p2));
  }
  
  @Test
  public void test2() throws Exception {
    setRules(rules2);
    assertThat(apply(mVariant, tVariant, gVariant, sVariant), is(pFallback));
    assertThat(apply(mDefault, tVariant, gVariant, sVariant), is(pFirst));
    assertThat(apply(mDefault, tDefault, gVariant, sVariant), is(pSecond));
    assertThat(apply(mDefault, tDefault, gDefault, sVariant), is(pThird));
    //assertThat(apply(mDefault, tDefault, gDefault, sDefault), is(pFourth));
  }
}
