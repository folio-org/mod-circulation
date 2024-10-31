package org.folio.circulation.rules;

import static java.util.Collections.emptyList;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.ITEM_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.LOAN_TYPE_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.LOCATION_ID_NAME;
import static org.folio.circulation.resources.AbstractCirculationRulesEngineResource.PATRON_TYPE_ID_NAME;
import static org.folio.circulation.rules.CirculationRulesExceptionMatcher.matches;
import static org.folio.circulation.support.utils.ClockUtil.getInstant;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Campus;
import org.folio.circulation.domain.Institution;
import org.folio.circulation.domain.Library;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.ServicePoint;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;

class Text2DroolsTest {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String HEADER = "priority: last-line\nfallback-policy: l no-loan r no-hold n basic-notice o overdue i lost-item\n";
  private static final String FIRST_INSTITUTION_ID = "3d22d91c-cf1d-11e9-bb65-2a2ae2dbcce4";
  private static final String SECOND_INSTITUTION_ID = "3d22d91c-cf1d-11e9-bb65-2a2ae2dbcce5";
  private static final String FIRST_LIBRARY_ID = "aa59f830-cfea-11e9-bb65-2a2ae2dbcce4";
  private static final String SECOND_LIBRARY_ID = "2125c4ea-9c9a-462e-84d2-90e3fcdbf1eb";
  private static final String FIRST_CAMPUS_ID = "692dbd8c-9804-4281-9fd1-8ce601d7c6a3";
  private static final String SECOND_CAMPUS_ID = "04163907-8f63-41f3-888d-f2d2888a4dd0";

  @Test
  void headerFallbackPolicy() {
    String droolsText = Text2Drools.convert(HEADER);
    Drools drools = new Drools("test-tenant-id", droolsText);

    assertThat(drools.loanPolicy(params("foo", "bar", "biz", "shelf"),
      createLocation(FIRST_INSTITUTION_ID, FIRST_LIBRARY_ID, FIRST_CAMPUS_ID)).getPolicyId(),
      is("no-loan"));
  }

  private final String test1 = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy: l no-loan r no-hold n basic-notice o overdue i lost-item",
      "m book cd dvd: l policy-a r request-1 n notice-1 o overdue-1 i lost-item-1",
      "m newspaper + g all: l policy-c r request-2 n notice-2 o overdue-2 i lost-item-2",
      "m streaming-subscription: l policy-c r request-3 n notice-3 o overdue-3 i lost-item-3",
      "    g visitor: l in-house r request-4 n notice-4 o overdue-4 i lost-item-4",
      "    g undergrad: l in-house r request-5 n notice-5 o overdue-5 i lost-item-5",
      "m book cd dvd + t special-items: l in-house r request-6 n notice-6 o overdue-6 i lost-item-6",
      "t special-items: l policy-d r request-7 n notice-7 o overdue-7 i lost-item-7",
      "    g visitor alumni: l in-house r request-8 n notice-8 o overdue-8 i lost-item-8",
      "a " + FIRST_INSTITUTION_ID + ": l in-university r request-9 n notice-9 o overdue-9 i lost-item-9");

  private final String [][] loanTestCases = new String[][] {
    // item type,   loan type,      patron type, institution id,   loan policies
    { "foo",       "foo",           "foo",       SECOND_INSTITUTION_ID,                                                      "no-loan" },
    { "book",      "regular",       "undergrad", FIRST_INSTITUTION_ID,                        "in-university",   "policy-a", "no-loan" },
    { "book",      "special-items", "undergrad", FIRST_INSTITUTION_ID,  "in-house", "policy-d", "in-university", "policy-a", "no-loan" },
    { "book",      "special-items", "visitor",   SECOND_INSTITUTION_ID,      "in-house", "in-house", "policy-d", "policy-a", "no-loan" },
    { "newspaper", "regular",       "undergrad", FIRST_INSTITUTION_ID,                          "in-university", "policy-c", "no-loan" },
    { "newspaper", "special-items", "undergrad", FIRST_INSTITUTION_ID,              "policy-d", "in-university", "policy-c", "no-loan" },
    { "newspaper", "special-items", "visitor",   FIRST_INSTITUTION_ID,  "in-house", "policy-d", "in-university", "policy-c", "no-loan" },
    { "dvd",       "special-items", "undergrad", FIRST_INSTITUTION_ID,  "in-house", "policy-d", "in-university", "policy-a", "no-loan" },
    { "map",       "special-items", "alumni",    FIRST_INSTITUTION_ID,              "in-house", "policy-d", "in-university", "no-loan" },
    { "foo",       "foo",           "foo",       FIRST_INSTITUTION_ID,                                      "in-university", "no-loan" },
  };

  private final String[][] requestTestCases = new String[][] {
    // item type,   request type,   patron type, institution id,  request policies
    { "foo",        "foo",          "foo",       SECOND_INSTITUTION_ID,                                                     "no-hold" },
    { "book",       "regular",      "undergrad", FIRST_INSTITUTION_ID,                            "request-9", "request-1", "no-hold" },
    { "book",      "special-items", "undergrad", FIRST_INSTITUTION_ID,  "request-6", "request-7", "request-9", "request-1", "no-hold" },
    { "book",      "special-items", "visitor",   SECOND_INSTITUTION_ID, "request-8", "request-6", "request-7", "request-1", "no-hold" },
    { "newspaper", "regular",       "undergrad", FIRST_INSTITUTION_ID,                            "request-9", "request-2", "no-hold" },
    { "newspaper", "special-items", "undergrad", FIRST_INSTITUTION_ID,               "request-7", "request-9", "request-2", "no-hold" },
    { "newspaper", "special-items", "visitor",   FIRST_INSTITUTION_ID,  "request-8", "request-7", "request-9", "request-2", "no-hold" },
    { "foo",       "foo",           "foo",       FIRST_INSTITUTION_ID,                                         "request-9", "no-hold" },
  };

  private final String[][] overdueTestCases = new String[][] {
    // item type,   request type,   patron type, institution id,   overdue policies
    { "foo",       "foo",           "foo",       SECOND_INSTITUTION_ID,                                                     "overdue" },
    { "book",      "regular",       "undergrad", FIRST_INSTITUTION_ID,                            "overdue-9", "overdue-1", "overdue" },
    { "book",      "special-items", "undergrad", FIRST_INSTITUTION_ID,  "overdue-6", "overdue-7", "overdue-9", "overdue-1", "overdue" },
    { "book",      "special-items", "visitor",   SECOND_INSTITUTION_ID, "overdue-8", "overdue-6", "overdue-7", "overdue-1", "overdue" },
    { "newspaper", "regular",       "undergrad", FIRST_INSTITUTION_ID,                            "overdue-9", "overdue-2", "overdue" },
    { "newspaper", "special-items", "undergrad", FIRST_INSTITUTION_ID,               "overdue-7", "overdue-9", "overdue-2", "overdue" },
    { "newspaper", "special-items", "visitor",   FIRST_INSTITUTION_ID,  "overdue-8", "overdue-7", "overdue-9", "overdue-2", "overdue" },
    { "foo",       "foo",           "foo",       FIRST_INSTITUTION_ID,                                         "overdue-9", "overdue" },
  };

  private final String[][] lostItemTestCases = new String[][] {
    // item type,   request type,   patron type, institution id,   lost item policies
    { "foo",       "foo",           "foo",       SECOND_INSTITUTION_ID,                                                             "lost-item" },
    { "book",      "regular",       "undergrad", FIRST_INSTITUTION_ID,                                "lost-item-9", "lost-item-1", "lost-item" },
    { "book",      "special-items", "undergrad", FIRST_INSTITUTION_ID,  "lost-item-6", "lost-item-7", "lost-item-9", "lost-item-1", "lost-item" },
    { "book",      "special-items", "visitor",   SECOND_INSTITUTION_ID, "lost-item-8", "lost-item-6", "lost-item-7", "lost-item-1", "lost-item" },
    { "newspaper", "regular",       "undergrad", FIRST_INSTITUTION_ID,                                "lost-item-9", "lost-item-2", "lost-item" },
    { "newspaper", "special-items", "undergrad", FIRST_INSTITUTION_ID,                 "lost-item-7", "lost-item-9", "lost-item-2", "lost-item" },
    { "newspaper", "special-items", "visitor",   FIRST_INSTITUTION_ID,  "lost-item-8", "lost-item-7", "lost-item-9", "lost-item-2", "lost-item" },
    { "foo",       "foo",           "foo",       FIRST_INSTITUTION_ID,                                               "lost-item-9", "lost-item" },
  };

  /** @return s[0] + " " + s[1] + " " + s[2] + " " + s[3] */
  private String first4(String [] s) {
    return s[0] + " " + s[1] + " " + s[2] + " " + s[3];
  }

  @Test
  void test1static() {
    String drools = Text2Drools.convert(test1);

    for (String [] s : loanTestCases) {
      assertThat(first4(s), Drools.loanPolicy(drools, params(s[0], s[1], s[2], s[3]),
        createLocation(s[3], SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)),
        is(s[4]));
    }
  }

  @Test
  void test1() {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(test1));

    for (String [] s : loanTestCases) {
        assertThat(first4(s), drools.loanPolicy(params(s[0], s[1], s[2], s[3]),
          createLocation(s[3], SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is(s[4]));
    }
  }

  @Test
  void testRequestPolicyList() {
    testRequestPolicies(test1, requestTestCases);
  }

  @Test
  void testRequestPolicy() {
    String drools = Text2Drools.convert(test1);

    for (String[] s : requestTestCases) {
      assertThat(first4(s), Drools.requestPolicy(drools, params(s[0], s[1], s[2], s[3]),
        createLocation(s[3], SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)), is(s[4]));
    }
  }

  @Test
  void testOverdueFinePolicyList() {
    testOverdueFinePolicies(test1, overdueTestCases);
  }

  @Test
  void testLostItemFeePolicyList() {
    testLostItemFeePolicies(test1, lostItemTestCases);
  }

  @Test
  void test1list() {
    testLoanPolicies(test1, loanTestCases);
  }

  @Test
  void firstLineMisplacedFallbackPolicy() {
    expectException(String.join("\n",
        "priority: first-line",
        "fallback-policy: l no-loan r no-hold n basic-notice o overdue i lost-item",
        "m book: l policy-a r no-hold n basic-notice o overdue i lost-item"),
      matches("fallback-policy", 3, 1));
  }

  @Test
  void firstLine() {
    String circulationRules = String.join("\n",
        "priority: first-line",
        "g visitor",
        "  t special-items: l in-house r no-hold n basic-notice o overdue i lost-item",
        "m book: l policy-b r no-hold n basic-notice o overdue i lost-item",
        "fallback-policy: l no-loan r no-hold n basic-notice o overdue i lost-item",
        "",
        ""
    );
    String [][] cases = {
        { "book", "special-items", "visitor",   FIRST_INSTITUTION_ID,     "in-house", "policy-b", "no-loan" },
        { "book", "special-items", "undergrad", FIRST_INSTITUTION_ID,                 "policy-b", "no-loan" },
        { "dvd",  "special-items", "undergrad", FIRST_INSTITUTION_ID,                             "no-loan" },
    };
    testLoanPolicies(circulationRules, cases);
  }

  @Test
  void tab() {
    expectException(
      HEADER + "  \t m book: l policy-a r no-hold n basic-notice o overdue i lost-item",
      matches("Tab", 3, 4));
  }

  @Test
  void missingPriority() {
    expectException(
      "fallback-policy: l no-loan r no-hold n basic-notice o overdue i lost-item",
      matches("priority", 1, 1));
  }

  @Test
  void reject6CriteriumTypes() {
    expectException(
      "priority: t g m a b c\nfallback-policy: l no-loan r no-hold n basic-notice o overdue i lost-item",
      matches("7 letters expected, found only 6", 1, 11));
  }

  @Test
  void reject8CriteriumTypes() {
    expectException(
      "priority: t g m a b c s s\nfallback-policy: l no-loan r no-hold n basic-notice o overdue i lost-item",
      matches("Only 7 letters expected, found 8", 1, 11));
  }

  @Test
  void duplicateCriteriumType() {
    expectException(
      "priority: t g m a b s s\nfallback-policy: l no-loan r no-hold n basic-notice r no-hold n basic-notice o overdue i lost-item",
      matches("Duplicate letter s", 1, 23));
  }

  @Test
  void duplicatePriorityType() {
    expectException(
      "priority: number-of-criteria, number-of-criteria, last-line\nfallback-policy: l no-loan r no-hold n basic-notice o overdue i lost-item",
      matches("Duplicate priority", 1, 31));
  }

  @Test
  void twoPriorities() {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(String.join("\n",
        "priority: number-of-criteria, first-line",
        "fallback-policy: l no-loan r no-hold n basic-notice o overdue i lost-item",
        "m book: l policy-a r no-hold n basic-notice o overdue i lost-item",
        "g student: l policy-b r no-hold n basic-notice o overdue i lost-item",
        "m dvd: l policy-c r no-hold n basic-notice o overdue i lost-item",
        "     g visitor: l policy-d r no-hold n basic-notice o overdue i lost-item"
        )));
    assertThat(drools.loanPolicy(params("book", "regular", "student", "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-a"));
    assertThat(drools.loanPolicy(params("dvd",  "regular", "student", "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-b"));
    assertThat(drools.loanPolicy(params("dvd",  "regular", "visitor", "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-d"));
  }

  @Test
  void threePriorities() {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(String.join("\n",
        "priority: criterium(t, s, c, b, a, m, g), number-of-criteria, first-line",
        "fallback-policy: l no-loan r no-hold n basic-notice o overdue i lost-item",
        "m book: l policy-a r no-hold n basic-notice o overdue i lost-item",
        "g student: l policy-b r no-hold n basic-notice o overdue i lost-item",
        "m dvd: l policy-c r no-hold n basic-notice o overdue i lost-item",
        "     g visitor: l policy-d r no-hold n basic-notice o overdue i lost-item"
        )));
    assertThat(drools.loanPolicy(params("book", "regular", "student", "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-a"));
    assertThat(drools.loanPolicy(params("dvd",  "regular", "student", "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-c"));
    assertThat(drools.loanPolicy(params("dvd",  "regular", "visitor", "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-d"));
  }

  @Test
  void missingFallbackPolicies() {
    expectException(
      "priority: last-line\nm book: l policy-a r no-hold n basic-notice o overdue i lost-item",
      matches("fallback", 2, 1));
  }

  @Test
  void missingFallbackPolicyFirstLine() {
    expectException(
      "priority: first-line\nm book: l policy-a r no-hold n basic-notice o overdue i lost-item",
      matches("fallback", 2, 66));
  }

  @Test
  void indentedFallbackPolicies() {
    expectException(
      HEADER + "m book\n  fallback-policy: l policy-b r no-hold n basic-notice o overdue i lost-item",
      matches("mismatched input 'fallback-policy'", 4, 3));
  }

  @Test
  void exclamationBeforePriority() {
    expectException(
      "! fallback-policy: l policy-a r no-hold n basic-notice o overdue i lost-item",
      matches("mismatched input '!'", 1, 1));
  }

  @Test
  void emptyNameList() {
    expectException(
      HEADER + "m: l policy-a r no-hold n basic-notice o overdue i lost-item",
      matches("Name missing", 3, 2));
  }

  @Test
  void noSpaceAroundColon() {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(String.join("\n",
        "priority:last-line",
        "fallback-policy:l no-loan r no-hold n basic-notice o overdue i lost-item",
        "s new:l policy-a r no-hold n basic-notice o overdue i lost-item",
        "a " + FIRST_INSTITUTION_ID + ":l policy-b r hold n basic-notice o overdue i lost-item",
        "c " + FIRST_LIBRARY_ID + ":l policy-c r hold n basic-notice o overdue i lost-item",
        "b " + FIRST_CAMPUS_ID + ":l policy-d r hold n basic-notice o overdue i lost-item")));
    assertThat(drools.loanPolicy(params("dvd", "regular", "student", "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("no-loan"));
    assertThat(drools.loanPolicy(params("dvd", "regular", "student", "new"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-a"));
    assertThat(drools.loanPolicy(params("dvd", "regular", "student", "new"),
      createLocation(FIRST_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-b"));
    assertThat(drools.loanPolicy(params("dvd", "regular", "student", "new"),
      createLocation(FIRST_INSTITUTION_ID, FIRST_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-c"));
    assertThat(drools.loanPolicy(params("dvd", "regular", "student", "new"),
      createLocation(FIRST_INSTITUTION_ID, FIRST_LIBRARY_ID, FIRST_CAMPUS_ID)).getPolicyId(), is("policy-d"));
  }

 @Test
  void multiSpaceAroundColon() {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(String.join("\n",
        "priority   :   last-line",
        "fallback-policy   :   l no-loan r no-hold n basic-notice o overdue i lost-item",
        "s new   :   l policy-a r no-hold n basic-notice o overdue i lost-item",
        "a " + FIRST_INSTITUTION_ID + "   : l policy-b r hold n basic-notice o overdue i lost-item",
        "c " + FIRST_LIBRARY_ID + ":l policy-c r hold n basic-notice o overdue i lost-item",
        "b " + FIRST_CAMPUS_ID + ":l policy-d r hold n basic-notice o overdue i lost-item")));
    assertThat(drools.loanPolicy(params("dvd", "regular", "student", "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("no-loan"));
    assertThat(drools.loanPolicy(params("dvd", "regular", "student", "new"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-a"));
   assertThat(drools.loanPolicy(params("dvd", "regular", "student", "new"),
     createLocation(FIRST_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-b"));
   assertThat(drools.loanPolicy(params("dvd", "regular", "student", "new"),
     createLocation(FIRST_INSTITUTION_ID, FIRST_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-c"));
   assertThat(drools.loanPolicy(params("dvd", "regular", "student", "new"),
     createLocation(FIRST_INSTITUTION_ID, FIRST_LIBRARY_ID, FIRST_CAMPUS_ID)).getPolicyId(), is("policy-d"));
  }

  @Test
  void negation() {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(HEADER + "m !dvd !music: l policy-a r no-hold n basic-notice o overdue i lost-item"));
    assertThat(drools.loanPolicy(params("dvd",       "regular", "student", "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("no-loan"));
    assertThat(drools.loanPolicy(params("music",     "regular", "student", "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("no-loan"));
    assertThat(drools.loanPolicy(params("newspaper", "regular", "student", "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-a"));
  }

  @Test
  void negationSingle() {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(HEADER + "m !dvd: l policy-a r no-hold n basic-notice o overdue i lost-item"));
    assertThat(drools.loanPolicy(params("dvd",       "regular", "student", "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("no-loan"));
    assertThat(drools.loanPolicy(params("newspaper", "regular", "student", "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-a"));
  }

  @Test
  void shelvingLocation() {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(String.join("\n",
        "priority: last-line",
        "fallback-policy: l no-loan r no-hold n basic-notice o overdue i lost-item",
        "s new: l policy-a r no-hold n basic-notice o overdue i lost-item",
        "m book: l policy-b r no-hold n basic-notice o overdue i lost-item",
        "a " + FIRST_INSTITUTION_ID + ": l policy-c r no-hold n basic-notice o overdue i lost-item",
        "b new: l policy-d r no-hold n basic-notice o overdue i lost-item",
        "c "+ FIRST_LIBRARY_ID + ": l policy-e r no-hold n basic-notice o overdue i lost-item",
        "b "+ FIRST_CAMPUS_ID + ": l policy-e r no-hold n basic-notice o overdue i lost-item")));
    assertThat(drools.loanPolicy(params("dvd",  "regular", "student",  "new"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),   is("policy-a"));
    assertThat(drools.loanPolicy(params("book", "regular", "student",  "new"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),   is("policy-b"));
    assertThat(drools.loanPolicy(params("book", "regular", "student",  "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-b"));
    assertThat(drools.loanPolicy(params("book", "regular", "student",  "old"),
      createLocation(FIRST_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),   is("policy-c"));
    assertThat(drools.loanPolicy(params("book", "regular", "student",  "old"),
      createLocation(FIRST_INSTITUTION_ID, FIRST_LIBRARY_ID, FIRST_CAMPUS_ID)).getPolicyId(),   is("policy-e"));
  }

  @Test
  void shelvingLocationDefaultPriority() {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(String.join("\n",
        "priority: t, s, c, b, a, m, g",
        "fallback-policy: l no-loan r no-hold n basic-notice o overdue i lost-item",
        "s new: l policy-new r no-hold n basic-notice o overdue i lost-item",
        "t special-items: l policy-special r no-hold n basic-notice o overdue i lost-item",
        "m book: l policy-book r no-hold n basic-notice o overdue i lost-item",
        "s stacks: l policy-stacks r no-hold n basic-notice o overdue i lost-item",
        "a " + FIRST_INSTITUTION_ID + ": l policy-a r no-hold n basic-notice o overdue i lost-item",
        "c " + FIRST_LIBRARY_ID + ": l policy-c r no-hold n basic-notice o overdue i lost-item",
        "b " + FIRST_CAMPUS_ID + ": l policy-d r no-hold n basic-notice o overdue i lost-item")));
    assertThat(drools.loanPolicy(params("book", "regular",       "student", "new"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),         is("policy-new"));
    assertThat(drools.loanPolicy(params("book", "regular",       "student", "open-stacks"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-book"));
    assertThat(drools.loanPolicy(params("book", "regular",       "student", "stacks"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),      is("policy-stacks"));
    assertThat(drools.loanPolicy(params("book", "special-items", "student", "new"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),         is("policy-special"));
    assertThat(drools.loanPolicy(params("book", "special-items", "student", "stacks"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),      is("policy-special"));
    assertThat(drools.loanPolicy(params("book", "special-items", "student", "open-stacks"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-special"));
    assertThat(drools.loanPolicy(params("book", "regular",       "student", "open-stacks"),
      createLocation(FIRST_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("policy-a"));
    assertThat(drools.loanPolicy(params("book", "regular",       "student", "open-stacks"),
      createLocation(FIRST_INSTITUTION_ID, FIRST_LIBRARY_ID, FIRST_CAMPUS_ID)).getPolicyId(), is("policy-c"));
    assertThat(drools.loanPolicy(params("book", "regular",       "student", "open-stacks"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, FIRST_CAMPUS_ID)).getPolicyId(), is("policy-d"));
  }

  @Test
  void overdueFinePolicy() {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(String.join("\n",
      "priority: last-line",
      "fallback-policy: l no-loan r no-hold n basic-notice o fallback i lost-item",
      "s new: l policy-a r no-hold n basic-notice o overdue-a i lost-item",
      "m book: l policy-b r no-hold n basic-notice o overdue-b i lost-item",
      "a " + FIRST_INSTITUTION_ID + ": l policy-c r no-hold n basic-notice o overdue-c i lost-item",
      "b new: l policy-d r no-hold n basic-notice o overdue-d i lost-item",
      "c "+ FIRST_LIBRARY_ID + ": l policy-e r no-hold n basic-notice o overdue-e i lost-item",
      "b "+ FIRST_CAMPUS_ID + ": l policy-e r no-hold n basic-notice o overdue-f i lost-item")));
    assertThat(drools.overduePolicy(params("dvd",  "regular", "student",  "new"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),   is("overdue-a"));
    assertThat(drools.overduePolicy(params("book", "regular", "student",  "new"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),   is("overdue-b"));
    assertThat(drools.overduePolicy(params("book", "regular", "student",  "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("overdue-b"));
    assertThat(drools.overduePolicy(params("book", "regular", "student",  "old"),
      createLocation(FIRST_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),   is("overdue-c"));
    assertThat(drools.overduePolicy(params("book", "regular", "student",  "old"),
      createLocation(FIRST_INSTITUTION_ID, FIRST_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),   is("overdue-e"));
   assertThat(drools.overduePolicy(params("book", "regular", "student",  "old"),
      createLocation(FIRST_INSTITUTION_ID, SECOND_LIBRARY_ID, FIRST_CAMPUS_ID)).getPolicyId(),   is("overdue-f"));
  }

  @Test
  void lostItemFeePolicy() {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(String.join("\n",
      "priority: last-line",
      "fallback-policy: l no-loan r no-hold n basic-notice o fallback i lost-item",
      "s new: l policy-a r no-hold n basic-notice o overdue-a i lost-item-a",
      "m book: l policy-b r no-hold n basic-notice o overdue-b i lost-item-b",
      "a " + FIRST_INSTITUTION_ID + ": l policy-c r no-hold n basic-notice o overdue-c i lost-item-c",
      "b new: l policy-d r no-hold n basic-notice o overdue-d i lost-item-d",
      "c "+ FIRST_LIBRARY_ID + ": l policy-e r no-hold n basic-notice o overdue-e i lost-item-e",
      "b "+ FIRST_CAMPUS_ID + ": l policy-e r no-hold n basic-notice o overdue-f i lost-item-f")));
    assertThat(drools.lostItemPolicy(params("dvd",  "regular", "student",  "new"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),   is("lost-item-a"));
    assertThat(drools.lostItemPolicy(params("book", "regular", "student",  "new"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),   is("lost-item-b"));
    assertThat(drools.lostItemPolicy(params("book", "regular", "student",  "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("lost-item-b"));
    assertThat(drools.lostItemPolicy(params("book", "regular", "student",  "old"),
      createLocation(FIRST_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),   is("lost-item-c"));
    assertThat(drools.lostItemPolicy(params("book", "regular", "student",  "old"),
      createLocation(FIRST_INSTITUTION_ID, FIRST_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(),   is("lost-item-e"));
    assertThat(drools.lostItemPolicy(params("book", "regular", "student",  "old"),
      createLocation(FIRST_INSTITUTION_ID, SECOND_LIBRARY_ID, FIRST_CAMPUS_ID)).getPolicyId(),   is("lost-item-f"));
  }

  @Test
  void missingLoanPolicy() {
    expectException(HEADER + "m book:",
      matches("Policy missing after ':'", 3, 8));
  }

  @Test
  void missingColonAfterCriteriaAndBeforePolicy() {
    expectException(HEADER + "g visitor\n   m book",
      matches("Policy missing", 4, 4));
  }

  @Test
  void missingColonAfterFallbackPolicy() {
    String rulesText = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy");
    expectException(rulesText,
      matches("Policy missing", 2, 16));
  }

  @Test
  void duplicateLoanPolicy() {
    expectException(HEADER + "m book: l policy-a l policy-b r no-hold n basic-notice o overdue i lost-item",
      matches("Only one policy of type l allowed", 3, 6));
  }

  @Test
  void duplicateOverdueFinePolicy() {
    expectException(HEADER + "m book: l policy-a r no-hold n basic-notice o overdue o overdue-1 i lost-item",
      matches("Only one policy of type o allowed", 3, 6));
  }

  @Test
  void duplicateLostItemFeePolicy() {
    expectException(HEADER + "m book: l policy-a r no-hold n basic-notice o overdue i lost-item i lost-item-1",
      matches("Only one policy of type i allowed", 3, 6));
  }

  @Test
  void comment() {
    String drools = Text2Drools.convert(HEADER + "# m book: l loan-anyhow r no-hold n basic-notice o overdue i lost-item");
    assertThat(drools, not(containsString("loan-anyhow")));
  }

  @Test
  void commentWithoutSpace() {
    String drools = Text2Drools.convert(HEADER + "#m book: l loan-anyhow r no-hold n basic-notice o overdue i lost-item");
    assertThat(drools, not(containsString("loan-anyhow")));
  }

  @Test
  void invalidToken() {
    expectException("foo", matches("extraneous input 'foo'", 1, 1));
  }

  @Test
  void run100() {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(test1));
    long start = getInstant().toEpochMilli();
    int n = 0;
    while (n < 100) {
      for (String [] s : loanTestCases) {
        drools.loanPolicy(params(s[0], s[1], s[2], "shelf"),
          createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID));
        n++;
      }
    }
    long millis = getInstant().toEpochMilli() - start;
    float perSecond = 1000f * n / millis;
    log.debug("{} loan policy calculations per second", perSecond);
    assertThat("loan policy calculations per second", perSecond, is(greaterThan(100f)));
  }

  @Test
  void unknownCriteriumType() throws ReflectiveOperationException {
    Method criteriumTypeClassnameMethod =
        Text2Drools.class.getDeclaredMethod("criteriumTypeClassname", String.class);

    assertThrows(IllegalArgumentException.class, () -> {
      criteriumTypeClassnameMethod.setAccessible(true);

      //noinspection JavaReflectionInvocation
      criteriumTypeClassnameMethod.invoke("q");
    });
  }

  @Test
  void missingFallbackPolicy() {
    expectException(
      "priority: last-line\nfallback-policy: l no-loan r no-hold o overdue i lost-item\n",
      matches("fallback", 2, 0));
  }

  @Test
  void duplicateFallbackPolicy() {
    expectException(
      "priority: last-line\nfallback-policy: l no-loan r no-hold n basic-notice r no-hold o overdue i lost-item",
      matches("Only one fallback policy of type r is allowed", 2, 0));
  }

  @Test
  void missingRequestPolicy() {
    expectException(HEADER + "m book: l no-loan n basic-notice o overdue i lost-item",
      matches("Must contain one of each policy type, missing type r", 3, 6));
  }

  @Test
  void missingOverduePolicy() {
    expectException(HEADER + "m book: l no-loan r basic-request n basic-notice i lost-item",
    matches("Must contain one of each policy type, missing type o", 3, 6));
  }

  @Test
  void missingLostItemPolicy() {
    expectException(HEADER + "m book: l no-loan r basic-request n basic-notice o overdue",
      matches("Must contain one of each policy type, missing type i", 3, 6));
  }

  @Test
  void alternatePolicyOrder() {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(String.join("\n",
      "priority: first-line",
      "m book: r allow-hold n general-notice o overdue l two-week i lost-item",
      "fallback-policy: l no-loan r no-hold n basic-notice o overdue i lost-item")));

    assertThat(drools.loanPolicy(params("book", "regular", "student", "shelf"),
      createLocation(SECOND_INSTITUTION_ID, SECOND_LIBRARY_ID, SECOND_CAMPUS_ID)).getPolicyId(), is("two-week"));
  }

  /** s without the first 4 elements, converted to a String.
   * <p>
   * expected({"a", "b", "c", "d", "e", "f", "g"}) = "[e, f, g]"
   * */
  private String expected(String [] s) {
    return Arrays.toString(Arrays.copyOfRange(s, 4, s.length));
  }

  /**
   * Test that Drools.loanPolicies(...) with the circulationRules work for all cases.
   * <p>
   * The first 3 element of a case are the parameters for Drools.loanPolicies(...),
   * the other parameters are the expected result.
   */
  private void testLoanPolicies(String circulationRules, String [][] cases) {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(circulationRules));
    for (String [] s : cases) {
      JsonArray array = drools.loanPolicies(params(s[0], s[1], s[2], s[3]),
        createLocation(s[3], SECOND_LIBRARY_ID, SECOND_CAMPUS_ID));
      String [] policies = new String[array.size()];
      for (int i=0; i<array.size(); i++) {
        policies[i] = array.getJsonObject(i).getString("loanPolicyId");
      }
      assertThat(first4(s), Arrays.toString(policies), is(expected(s)));
    }
  }

  private void testRequestPolicies(String circulationRules, String[][] cases) {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(circulationRules));
    for (String [] s : cases) {
      JsonArray array = drools.requestPolicies(params(s[0], s[1], s[2], s[3]),
        createLocation(s[3], SECOND_LIBRARY_ID, SECOND_CAMPUS_ID));
      String [] policies = new String[array.size()];
      for (int i=0; i<array.size(); i++) {
        policies[i] = array.getJsonObject(i).getString("requestPolicyId");
      }
      assertThat(first4(s), Arrays.toString(policies), is(expected(s)));
    }
  }

  private void testOverdueFinePolicies(String circulationRules, String[][] cases) {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(circulationRules));
    for (String [] s : cases) {
      JsonArray array = drools.overduePolicies(params(s[0], s[1], s[2], s[3]),
        createLocation(s[3], SECOND_LIBRARY_ID, SECOND_CAMPUS_ID));
      String [] policies = new String[array.size()];
      for (int i=0; i<array.size(); i++) {
        policies[i] = array.getJsonObject(i).getString("overduePolicyId");
      }
      assertThat(first4(s), Arrays.toString(policies), is(expected(s)));
    }
  }

  private void testLostItemFeePolicies(String circulationRules, String[][] cases) {
    Drools drools = new Drools("test-tenant-id", Text2Drools.convert(circulationRules));
    for (String [] s : cases) {
      JsonArray array = drools.lostItemPolicies(params(s[0], s[1], s[2], s[3]),
        createLocation(s[3], SECOND_LIBRARY_ID, SECOND_CAMPUS_ID));
      String [] policies = new String[array.size()];
      for (int i=0; i<array.size(); i++) {
        policies[i] = array.getJsonObject(i).getString("lostItemPolicyId");
      }
      assertThat(first4(s), Arrays.toString(policies), is(expected(s)));
    }
  }

  private MultiMap params(String itId, String ltId, String ptId, String lId) {
    MultiMap params = MultiMap.caseInsensitiveMultiMap();

    params.add(ITEM_TYPE_ID_NAME, itId);
    params.add(LOAN_TYPE_ID_NAME, ltId);
    params.add(PATRON_TYPE_ID_NAME, ptId);
    params.add(LOCATION_ID_NAME, lId);

    return params;
  }

  private Location createLocation(String institutionId, String libraryId, String campusId) {
    return new Location(null, null, null, null, emptyList(), null,
      false,
      Institution.unknown(institutionId), Campus.unknown(campusId), Library.unknown(libraryId),
      ServicePoint.unknown());
  }

  private void expectException(String rulesText, Matcher<CirculationRulesException> matches) {
    assertThat(assertThrows(CirculationRulesException.class, () -> Text2Drools.convert(rulesText)),
      matches);
  }
}
