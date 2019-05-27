package org.folio.circulation.rules;

import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.ITEM_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.LOAN_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.PATRON_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.SHELVING_LOCATION_ID_NAME;
import static org.folio.circulation.rules.CirculationRulesExceptionMatcher.matches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.fail;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.MultiMap;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.json.JsonArray;

public class Text2DroolsTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String HEADER = "priority: last-line\nfallback-policy: l no-loan r no-hold n basic-notice\n";

  @Test
  public void headerFallbackPolicy() {
    String droolsText = Text2Drools.convert(HEADER);
    Drools drools = new Drools(droolsText);
    assertThat(drools.loanPolicy(params("foo", "bar", "biz", "shelf")), is("no-loan"));
  }

  private String test1 = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy: l no-loan r no-hold n basic-notice",
      "m book cd dvd: l policy-a r request-1 n notice-1",
      "m newspaper + g all: l policy-c r request-2 n notice-2",
      "m streaming-subscription: l policy-c r request-3 n notice-3",
      "    g visitor: l in-house r request-4 n notice-4",
      "    g undergrad: l in-house r request-5 n notice-5",
      "m book cd dvd + t special-items: l in-house r request-6 n notice-6",
      "t special-items: l policy-d r request-7 n notice-7",
      "    g visitor alumni: l in-house r request-8 n notice-8"
      );
  private String [][] loanTestCases = new String[][] {
    // item type,   loan type,      patron type,   loan policies
    { "foo",       "foo",           "foo",                                                        "no-loan" },
    { "book",      "regular",       "undergrad",                                      "policy-a", "no-loan" },
    { "book",      "special-items", "undergrad",  "in-house",             "policy-d", "policy-a", "no-loan" },
    { "book",      "special-items", "visitor",    "in-house", "in-house", "policy-d", "policy-a", "no-loan" },
    { "newspaper", "regular",       "undergrad",                                      "policy-c", "no-loan" },
    { "newspaper", "special-items", "undergrad",                          "policy-d", "policy-c", "no-loan" },
    { "newspaper", "special-items", "visitor",                "in-house", "policy-d", "policy-c", "no-loan" },
    { "dvd",       "special-items", "undergrad",  "in-house",             "policy-d", "policy-a", "no-loan" },
    { "map",       "special-items", "alumni",                 "in-house", "policy-d",             "no-loan" },
  };

  private String[][] requestTestCases = new String[][] {
    // item type,   request type,   patron type,   request policies
    { "foo",        "foo",          "foo",                                                            "no-hold" },
    { "book",       "regular",      "undergrad",                                         "request-1", "no-hold" },
    { "book",      "special-items", "undergrad",  "request-6",              "request-7", "request-1", "no-hold" },
    { "book",      "special-items", "visitor",    "request-8", "request-6", "request-7", "request-1", "no-hold" },
    { "newspaper", "regular",       "undergrad",                                         "request-2", "no-hold" },
    { "newspaper", "special-items", "undergrad",                            "request-7", "request-2", "no-hold" },
    { "newspaper", "special-items", "visitor",                 "request-8", "request-7", "request-2", "no-hold" },
  };

  /** @return s[0] + " " + s[1] + " " + s[2] */
  private String first3(String [] s) {
    return s[0] + " " + s[1] + " " + s[2];
  }

  @Test
  public void test1static() {
    String drools = Text2Drools.convert(test1);
    log.debug("drools = {}" + drools);
    for (String [] s : loanTestCases) {
      assertThat(first3(s), Drools.loanPolicy(drools, params(s[0], s[1], s[2], "shelf")), is(s[3]));
    }
  }

  @Test
  public void test1() {
    Drools drools = new Drools(Text2Drools.convert(test1));
    for (String [] s : loanTestCases) {
      assertThat(first3(s), drools.loanPolicy(params(s[0], s[1], s[2], "shelf")), is(s[3]));
    }
  }

  @Test
  public void testRequestPolicyList() {
      testRequestPolicies(test1, requestTestCases);
  }

  @Test public void testRequestPolicy() {
    String drools = Text2Drools.convert(test1);
    for (String[] s : requestTestCases) {
      assertThat(first3(s), Drools.requestPolicy(drools, params(s[0], s[1], s[2], "shelf")), is(s[3]));
    }
  }

  /** s without the first 3 elements, converted to a String.
   * <p>
   * expected({"a", "b", "c", "d", "e", "f", "g"}) = "[d, e, f, g]"
   * */
  private String expected(String [] s) {
    return Arrays.toString(Arrays.copyOfRange(s, 3, s.length));
  }

  /**
   * Test that Drools.loanPolicies(...) with the circulationRules work for all cases.
   * <p>
   * The first 3 element of a case are the parameters for Drools.loanPolicies(...),
   * the other parameters are the expected result.
   */
  private void testLoanPolicies(String circulationRules, String [][] cases) {
    Drools drools = new Drools(Text2Drools.convert(circulationRules));
    for (String [] s : cases) {
      JsonArray array = drools.loanPolicies(params(s[0], s[1], s[2], "shelf"));
      String [] policies = new String[array.size()];
      for (int i=0; i<array.size(); i++) {
        policies[i] = array.getJsonObject(i).getString("loanPolicyId");
      }
      assertThat(first3(s), Arrays.toString(policies), is(expected(s)));
    }
  }

  private void testRequestPolicies(String circulationRules, String[][] cases) {
      Drools drools = new Drools(Text2Drools.convert(circulationRules));
      for (String [] s : cases) {
        JsonArray array = drools.requestPolicies(params(s[0], s[1], s[2], "shelf"));
        String [] policies = new String[array.size()];
        for (int i=0; i<array.size(); i++) {
          policies[i] = array.getJsonObject(i).getString("requestPolicyId");
        }
        assertThat(first3(s), Arrays.toString(policies), is(expected(s)));
      }
  }

  @Test
  public void test1list() {
    testLoanPolicies(test1, loanTestCases);
  }

  @Test
  public void firstLineMisplacedFallbackPolicy() {
    try {
      Text2Drools.convert(String.join("\n",
          "priority: first-line",
          "fallback-policy: l no-loan r no-hold n basic-notice",
          "m book: l policy-a r no-hold n basic-notice"));
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("fallback-policy", 3, 1));
    }
  }

  @Test
  public void firstLine() {
    String circulationRules = String.join("\n",
        "priority: first-line",
        "g visitor",
        "  t special-items: l in-house r no-hold n basic-notice",
        "m book: l policy-b r no-hold n basic-notice",
        "fallback-policy: l no-loan r no-hold n basic-notice",
        "",
        ""
    );
    String [][] cases = {
        { "book", "special-items", "visitor",     "in-house", "policy-b", "no-loan" },
        { "book", "special-items", "undergrad",               "policy-b", "no-loan" },
        { "dvd",  "special-items", "undergrad",                           "no-loan" },
    };
    testLoanPolicies(circulationRules, cases);
  }

  @Test
  public void tab() {
    try {
      Text2Drools.convert(HEADER + "  \t m book: l policy-a r no-hold n basic-notice");
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("Tab", 3, 4));
    }
  }

  @Test
  public void missingPriority() {
    try {
      Text2Drools.convert("fallback-policy: l no-loan r no-hold n basic-notice");
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("priority", 1, 1));
    }
  }

  @Test
  public void reject6CriteriumTypes() {
    try {
      Text2Drools.convert("priority: t g m a b c\nfallback-policy: l no-loan r no-hold n basic-notice");
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("7 letters expected, found only 6", 1, 11));
    }
  }

  @Test
  public void reject8CriteriumTypes() {
    try {
      Text2Drools.convert("priority: t g m a b c s s\nfallback-policy: l no-loan r no-hold n basic-notice");
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("Only 7 letters expected, found 8", 1, 11));
    }
  }

  @Test
  public void duplicateCriteriumType() {
    try {
      Text2Drools.convert("priority: t g m a b s s\nfallback-policy: l no-loan r no-hold n basic-notice r no-hold n basic-notice");
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("Duplicate letter s", 1, 23));
    }
  }

  @Test
  public void duplicatePriorityType() {
    try {
      Text2Drools.convert("priority: number-of-criteria, number-of-criteria, last-line\nfallback-policy: l no-loan r no-hold n basic-notice");
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("Duplicate priority", 1, 31));
    }
  }

  @Test
  public void twoPriorities() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority: number-of-criteria, first-line",
        "fallback-policy: l no-loan r no-hold n basic-notice",
        "m book: l policy-a r no-hold n basic-notice",
        "g student: l policy-b r no-hold n basic-notice",
        "m dvd: l policy-c r no-hold n basic-notice",
        "     g visitor: l policy-d r no-hold n basic-notice"
        )));
    assertThat(drools.loanPolicy(params("book", "regular", "student", "shelf")), is("policy-a"));
    assertThat(drools.loanPolicy(params("dvd",  "regular", "student", "shelf")), is("policy-b"));
    assertThat(drools.loanPolicy(params("dvd",  "regular", "visitor", "shelf")), is("policy-d"));
  }

  @Test
  public void threePriorities() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority: criterium(t, s, c, b, a, m, g), number-of-criteria, first-line",
        "fallback-policy: l no-loan r no-hold n basic-notice",
        "m book: l policy-a r no-hold n basic-notice",
        "g student: l policy-b r no-hold n basic-notice",
        "m dvd: l policy-c r no-hold n basic-notice",
        "     g visitor: l policy-d r no-hold n basic-notice"
        )));
    assertThat(drools.loanPolicy(params("book", "regular", "student", "shelf")), is("policy-a"));
    assertThat(drools.loanPolicy(params("dvd",  "regular", "student", "shelf")), is("policy-c"));
    assertThat(drools.loanPolicy(params("dvd",  "regular", "visitor", "shelf")), is("policy-d"));
  }

  @Test
  public void missingFallbackPolicies() {
    try {
      Text2Drools.convert("priority: last-line\nm book: l policy-a r no-hold n basic-notice");
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("fallback", 2, 1));
    }
  }

  @Test
  public void missingFallbackPolicyFirstLine() {
    try {
      Text2Drools.convert("priority: first-line\nm book: l policy-a r no-hold n basic-notice");
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("fallback", 2, 44));
    }
  }

  @Test
  public void indentedFallbackPolicies() {
    try {
      Text2Drools.convert(HEADER + "m book\n  fallback-policy: l policy-b r no-hold n basic-notice");
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("mismatched input 'fallback-policy'", 4, 3));
    }
  }

  @Test
  public void exclamationBeforePriority() {
    try {
      Text2Drools.convert("! fallback-policy: l policy-a r no-hold n basic-notice");
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("mismatched input '!'", 1, 1));
    }
  }

  @Test
  public void emptyNameList() {
    try {
      Text2Drools.convert(HEADER + "m: l policy-a r no-hold n basic-notice");
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("Name missing", 3, 2));
    }
  }

  @Test
  public void noSpaceAroundColon() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority:last-line",
        "fallback-policy:l no-loan r no-hold n basic-notice",
        "s new:l policy-a r no-hold n basic-notice")));
    assertThat(drools.loanPolicy(params("dvd", "regular", "student", "shelf")), is("no-loan"));
    assertThat(drools.loanPolicy(params("dvd", "regular", "student", "new"  )), is("policy-a"));
  }

  @Test
  public void multiSpaceAroundColon() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority   :   last-line",
        "fallback-policy   :   l no-loan r no-hold n basic-notice",
        "s new   :   l policy-a r no-hold n basic-notice")));
    assertThat(drools.loanPolicy(params("dvd", "regular", "student", "shelf")), is("no-loan"));
    assertThat(drools.loanPolicy(params("dvd", "regular", "student", "new"  )), is("policy-a"));
  }

  @Test
  public void negation() {
    Drools drools = new Drools(Text2Drools.convert(HEADER + "m !dvd !music: l policy-a r no-hold n basic-notice"));
    assertThat(drools.loanPolicy(params("dvd",       "regular", "student", "shelf")), is("no-loan"));
    assertThat(drools.loanPolicy(params("music",     "regular", "student", "shelf")), is("no-loan"));
    assertThat(drools.loanPolicy(params("newspaper", "regular", "student", "shelf")), is("policy-a"));
  }

  @Test
  public void negationSingle() {
    Drools drools = new Drools(Text2Drools.convert(HEADER + "m !dvd: l policy-a r no-hold n basic-notice"));
    assertThat(drools.loanPolicy(params("dvd",       "regular", "student", "shelf")), is("no-loan"));
    assertThat(drools.loanPolicy(params("newspaper", "regular", "student", "shelf")), is("policy-a"));
  }

  @Test
  public void shelvingLocation() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority: last-line",
        "fallback-policy: l no-loan r no-hold n basic-notice",
        "s new: l policy-a r no-hold n basic-notice",
        "m book: l policy-b r no-hold n basic-notice",
        "a new: l policy-c r no-hold n basic-notice",
        "b new: l policy-d r no-hold n basic-notice",
        "c new: l policy-e r no-hold n basic-notice")));
    assertThat(drools.loanPolicy(params("dvd",  "regular", "student",  "new")),   is("policy-a"));
    assertThat(drools.loanPolicy(params("book", "regular", "student",  "new")),   is("policy-b"));
    assertThat(drools.loanPolicy(params("book", "regular", "student",  "shelf")), is("policy-b"));
  }

  @Test
  public void shelvingLocationDefaultPriority() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority: t, s, c, b, a, m, g",
        "fallback-policy: l no-loan r no-hold n basic-notice",
        "s new: l policy-new r no-hold n basic-notice",
        "t special-items: l policy-special r no-hold n basic-notice",
        "m book: l policy-book r no-hold n basic-notice",
        "s stacks: l policy-stacks r no-hold n basic-notice")));
    assertThat(drools.loanPolicy(params("book", "regular",       "student", "new")),         is("policy-new"));
    assertThat(drools.loanPolicy(params("book", "regular",       "student", "open-stacks")), is("policy-book"));
    assertThat(drools.loanPolicy(params("book", "regular",       "student", "stacks")),      is("policy-stacks"));
    assertThat(drools.loanPolicy(params("book", "special-items", "student", "new")),         is("policy-special"));
    assertThat(drools.loanPolicy(params("book", "special-items", "student", "stacks")),      is("policy-special"));
    assertThat(drools.loanPolicy(params("book", "special-items", "student", "open-stacks")), is("policy-special"));
  }

  @Test
  public void missingLoanPolicy() {
    try {
      Text2Drools.convert(HEADER + "m book:");
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("Policy missing after ':'", 3, 8));
    }
  }

  @Test
  public void duplicateLoanPolicy() {
    try {
      Text2Drools.convert(HEADER + "m book: l policy-a l policy-b r no-hold n basic-notice");
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("Only one policy of type l allowed", 3, 6));
    }
  }

  @Test
  public void comment() {
    String drools = Text2Drools.convert(HEADER + "# m book: l loan-anyhow r no-hold n basic-notice");
    assertThat(drools, not(containsString("loan-anyhow")));
  }

  @Test
  public void commentWithoutSpace() {
    String drools = Text2Drools.convert(HEADER + "#m book: l loan-anyhow r no-hold n basic-notice");
    assertThat(drools, not(containsString("loan-anyhow")));
  }

  @Test
  public void invalidToken() {
    try {
      Text2Drools.convert("foo");
      fail();
    } catch (CirculationRulesException e) {
      assertThat(e, matches("extraneous input 'foo'", 1, 1));
    }
  }

  @Test
  public void run100() {
    Drools drools = new Drools(Text2Drools.convert(test1));
    long start = System.currentTimeMillis();
    int n = 0;
    while (n < 100) {
      for (String [] s : loanTestCases) {
        drools.loanPolicy(params(s[0], s[1], s[2], "shelf"));
        n++;
      }
    }
    long millis = System.currentTimeMillis() - start;
    float perSecond = 1000f * n / millis;
    log.debug("{} loan policy calculations per second", perSecond);
    assertThat("loan policy calculations per second", perSecond, is(greaterThan(100f)));
  }

  @Test(expected=IllegalArgumentException.class)
  public void unknownCriteriumType() throws ReflectiveOperationException {
    Method criteriumTypeClassnameMethod =
        Text2Drools.class.getDeclaredMethod("criteriumTypeClassname", String.class);
    criteriumTypeClassnameMethod.setAccessible(true);
    criteriumTypeClassnameMethod.invoke("q");
  }

  @Test
  public void missingFallbackPolicy() {
    try {
      Text2Drools.convert("priority: last-line\nfallback-policy: l no-loan r no-hold\n");
    } catch (CirculationRulesException e) {
      assertThat(e, matches("fallback", 2, 0));
    }
  }

  @Test
  public void duplicateFallbackPolicy() {
    try {
      Text2Drools.convert("priority: last-line\nfallback-policy: l no-loan r no-hold n basic-notice r no-hold");
    } catch (CirculationRulesException e) {
      assertThat(e, matches("Only one fallback policy of type r is allowed", 2, 0));
    }
  }

  @Test
  public void missingRequestPolicy() {
    try {
      Text2Drools.convert(HEADER + "m book: l no-loan n basic-notice");
    } catch (CirculationRulesException e) {
      assertThat(e, matches("Must contain one of each policy type, missing type r", 3, 6));
    }
  }

  @Test
  public void alternatePolicyOrder() {
    try {
      Text2Drools.convert(String.join("\n",
        "priority: first-line",
        "m book: r allow-hold n general-notice l two-week",
        "fallback-policy: l no-loan r no-hold n basic-notice"
      ));
    } catch (CirculationRulesException e) {
      fail("circulation rules should build correctly in any order");
    }
  }

  private MultiMap params(String itId, String ltId, String ptId, String slId) {
    MultiMap params = new CaseInsensitiveHeaders();
    params.add(ITEM_TYPE_ID_NAME, itId);
    params.add(LOAN_TYPE_ID_NAME, ltId);
    params.add(PATRON_TYPE_ID_NAME, ptId);
    params.add(SHELVING_LOCATION_ID_NAME, slId);
    return params;
  }

}
