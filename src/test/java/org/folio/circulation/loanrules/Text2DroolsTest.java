package org.folio.circulation.loanrules;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonArray;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.folio.circulation.loanrules.LoanRulesExceptionMatcher.matches;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

public class Text2DroolsTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String HEADER = "priority: last-line\nfallback-policy: l no-loan\nfallback-policy: r no-hold\nfallback-policy: n basic-notice\n";

  @Test
  public void headerFallbackPolicy() {
    String droolsText = Text2Drools.convert(HEADER);
    Drools drools = new Drools(droolsText);
    assertThat(drools.loanPolicy("foo", "bar", "baz", "shelf"), is("no-loan"));
  }

  private String test1 = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy: l no-loan",
      "fallback-policy: r no-hold",
      "fallback-policy: n basic-notice",
      "m book cd dvd: l policy-a r no-hold n basic-notice",
      "m newspaper + g all: l policy-c r no-hold n basic-notice",
      "m streaming-subscription: l policy-c r no-hold n basic-notice",
      "    g visitor: l in-house r no-hold n basic-notice",
      "    g undergrad: l in-house r no-hold n basic-notice",
      "m book cd dvd + t special-items: l in-house r no-hold n basic-notice",
      "t special-items: l policy-d r no-hold n basic-notice",
      "    g visitor: l in-house r no-hold n basic-notice"
      );
  private String [][] test1cases = new String[][] {
    // item type,   loan type,      patron type,   loan policies
    { "foo",       "foo",           "foo",                                                        "no-loan" },
    { "book",      "regular",       "undergrad",                                      "policy-a", "no-loan" },
    { "book",      "special-items", "undergrad",  "in-house",             "policy-d", "policy-a", "no-loan" },
    { "book",      "special-items", "visitor",    "in-house", "in-house", "policy-d", "policy-a", "no-loan" },
    { "newspaper", "regular",       "undergrad",                                      "policy-c", "no-loan" },
    { "newspaper", "special-items", "undergrad",                          "policy-d", "policy-c", "no-loan" },
    { "newspaper", "special-items", "visitor",                "in-house", "policy-d", "policy-c", "no-loan" },
  };

  /** @return s[0] + " " + s[1] + " " + s[2] */
  private String first3(String [] s) {
    return s[0] + " " + s[1] + " " + s[2];
  }

  @Test
  public void test1static() {
    String drools = Text2Drools.convert(test1);
    log.debug("drools = {}" + drools);
    for (String [] s : test1cases) {
      assertThat(first3(s), Drools.loanPolicy(drools, s[0], s[1], s[2], "shelf"), is(s[3]));
    }
  }

  @Test
  public void test1() {
    Drools drools = new Drools(Text2Drools.convert(test1));
    for (String [] s : test1cases) {
      assertThat(first3(s), drools.loanPolicy(s[0], s[1], s[2], "shelf"), is(s[3]));
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
   * Test that Drools.loanPolicies(...) with the loanRules work for all cases.
   * <p>
   * The first 3 element of a case are the parameters for Drools.loanPolicies(...),
   * the other parameters are the expected result.
   */
  private void testLoanPolicies(String loanRules, String [][] cases) {
    Drools drools = new Drools(Text2Drools.convert(loanRules));
    for (String [] s : cases) {
      JsonArray array = drools.loanPolicies(s[0], s[1], s[2], "shelf");
      String [] policies = new String[array.size()];
      for (int i=0; i<array.size(); i++) {
        policies[i] = array.getJsonObject(i).getString("loanPolicyId");
      }
      assertThat(first3(s), Arrays.toString(policies), is(expected(s)));
    }
  }

  @Test
  public void test1list() {
    testLoanPolicies(test1, test1cases);
  }

  @Test
  public void firstLineMisplacedFallbackPolicy() {
    try {
      Text2Drools.convert(String.join("\n",
          "priority: first-line",
          "fallback-policy: l no-loan",
          "m book: l policy-a"));
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("fallback-policy", 3, 1));
    }
  }

  @Test
  public void firstLine() {
    String loanRules = String.join("\n",
        "priority: first-line",
        "g visitor",
        "  t special-items: l in-house r no-hold n basic-notice",
        "m book: l policy-b r no-hold n basic-notice",
        "fallback-policy: l no-loan",
        "fallback-policy: r no-hold",
        "fallback-policy: n basic-notice",
        "",
        ""
    );
    String [][] cases = {
        { "book", "special-items", "visitor",     "in-house", "policy-b", "no-loan" },
        { "book", "special-items", "undergrad",               "policy-b", "no-loan" },
        { "dvd",  "special-items", "undergrad",                           "no-loan" },
    };
    testLoanPolicies(loanRules, cases);
  }

  @Test
  public void tab() {
    try {
      Text2Drools.convert(HEADER + "  \t m book: l policy-a r no-hold n basic-notice");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("Tab", 5, 4));
    }
  }

  @Test
  public void missingPriority() {
    try {
      Text2Drools.convert("fallback-policy: l no-loan\nfallback-policy: r no-hold\nfallback-policy: n basic-notice");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("priority", 1, 1));
    }
  }

  @Test
  public void reject6CriteriumTypes() {
    try {
      Text2Drools.convert("priority: t g m a b c\nfallback-policy: l no-loan\nfallback-policy: r no-hold\nfallback-policy: n basic-notice");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("7 letters expected, found only 6", 1, 11));
    }
  }

  @Test
  public void reject8CriteriumTypes() {
    try {
      Text2Drools.convert("priority: t g m a b c s s\nfallback-policy: l no-loan\nfallback-policy: r no-hold\nfallback-policy: n basic-notice");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("Only 7 letters expected, found 8", 1, 11));
    }
  }

  @Test
  public void duplicateCriteriumType() {
    try {
      Text2Drools.convert("priority: t g m a b s s\nfallback-policy: l no-loan\nfallback-policy: r no-hold\nfallback-policy: n basic-notice r no-hold n basic-notice");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("Duplicate letter s", 1, 23));
    }
  }

  @Test
  public void duplicatePriorityType() {
    try {
      Text2Drools.convert("priority: number-of-criteria, number-of-criteria, last-line\nfallback-policy: l no-loan\nfallback-policy: r no-hold\nfallback-policy: n basic-notice r no-hold n basic-notice");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("Duplicate priority", 1, 31));
    }
  }

  @Test
  public void twoPriorities() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority: number-of-criteria, first-line",
        "fallback-policy: l no-loan",
        "fallback-policy: r no-hold",
        "fallback-policy: n basic-notice",
        "m book: l policy-a r no-hold n basic-notice",
        "g student: l policy-b r no-hold n basic-notice",
        "m dvd: l policy-c r no-hold n basic-notice",
        "     g visitor: l policy-d r no-hold n basic-notice"
        )));
    assertThat(drools.loanPolicy("book", "regular", "student", "shelf"), is("policy-a"));
    assertThat(drools.loanPolicy("dvd",  "regular", "student", "shelf"), is("policy-b"));
    assertThat(drools.loanPolicy("dvd",  "regular", "visitor", "shelf"), is("policy-d"));
  }

  @Test
  public void threePriorities() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority: criterium(t, s, c, b, a, m, g), number-of-criteria, first-line",
        "fallback-policy: l no-loan",
        "fallback-policy: r no-hold",
        "fallback-policy: n basic-notice",
        "m book: l policy-a r no-hold n basic-notice",
        "g student: l policy-b r no-hold n basic-notice",
        "m dvd: l policy-c r no-hold n basic-notice",
        "     g visitor: l policy-d r no-hold n basic-notice"
        )));
    assertThat(drools.loanPolicy("book", "regular", "student", "shelf"), is("policy-a"));
    assertThat(drools.loanPolicy("dvd",  "regular", "student", "shelf"), is("policy-c"));
    assertThat(drools.loanPolicy("dvd",  "regular", "visitor", "shelf"), is("policy-d"));
  }

  @Test
  public void missingFallbackPolicies() {
    try {
      Text2Drools.convert("priority: last-line\nm book: l policy-a r no-hold n basic-notice");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("fallback", 2, 1));
    }
  }

  @Test
  public void missingFallbackPolicyFirstLine() {
    try {
      Text2Drools.convert("priority: first-line\nm book: l policy-a r no-hold n basic-notice");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("fallback", 2, 44));
    }
  }

  @Test
  public void indentedFallbackPolicies() {
    try {
      Text2Drools.convert(HEADER + "m book\n  fallback-policy: l policy-b\n  fallback-policy: r no-hold\n  fallback-policy: n basic-notice");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("mismatched input 'fallback-policy'", 6, 3));
    }
  }

  @Test
  public void exclamationBeforePriority() {
    try {
      Text2Drools.convert("! fallback-policy: l policy-a\nfallback-policy: r no-hold\nfallback-policy: n basic-notice");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("mismatched input '!'", 1, 1));
    }
  }

  @Test
  public void emptyNameList() {
    try {
      Text2Drools.convert(HEADER + "m: l policy-a r no-hold n basic-notice");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("Name missing", 5, 2));
    }
  }

  @Test
  public void noSpaceAroundColon() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority:last-line",
        "fallback-policy:l no-loan",
        "fallback-policy:r no-hold",
        "fallback-policy:n basic-notice",
        "s new:l policy-a r no-hold n basic-notice")));
    assertThat(drools.loanPolicy("dvd", "regular", "student", "shelf"), is("no-loan"));
    assertThat(drools.loanPolicy("dvd", "regular", "student", "new"  ), is("policy-a"));
  }

  @Test
  public void multiSpaceAroundColon() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority   :   last-line",
        "fallback-policy   :   l no-loan",
        "fallback-policy   :   r no-hold",
        "fallback-policy   :   n basic-notice",
        "s new   :   l policy-a r no-hold n basic-notice")));
    assertThat(drools.loanPolicy("dvd", "regular", "student", "shelf"), is("no-loan"));
    assertThat(drools.loanPolicy("dvd", "regular", "student", "new"  ), is("policy-a"));
  }

  @Test
  public void negation() {
    Drools drools = new Drools(Text2Drools.convert(HEADER + "m !dvd !music: l policy-a r no-hold n basic-notice"));
    assertThat(drools.loanPolicy("dvd",       "regular", "student", "shelf"), is("no-loan"));
    assertThat(drools.loanPolicy("music",     "regular", "student", "shelf"), is("no-loan"));
    assertThat(drools.loanPolicy("newspaper", "regular", "student", "shelf"), is("policy-a"));
  }

  @Test
  public void negationSingle() {
    Drools drools = new Drools(Text2Drools.convert(HEADER + "m !dvd: l policy-a r no-hold n basic-notice"));
    assertThat(drools.loanPolicy("dvd",       "regular", "student", "shelf"), is("no-loan"));
    assertThat(drools.loanPolicy("newspaper", "regular", "student", "shelf"), is("policy-a"));
  }

  @Test
  public void shelvingLocation() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority: last-line",
        "fallback-policy: l no-loan",
        "fallback-policy: r no-hold",
        "fallback-policy: n basic-notice",
        "s new: l policy-a r no-hold n basic-notice",
        "m book: l policy-b r no-hold n basic-notice",
        "a new: l policy-c r no-hold n basic-notice",
        "b new: l policy-d r no-hold n basic-notice",
        "c new: l policy-e r no-hold n basic-notice")));
    assertThat(drools.loanPolicy("dvd",  "regular", "student",  "new"),   is("policy-a"));
    assertThat(drools.loanPolicy("book", "regular", "student",  "new"),   is("policy-b"));
    assertThat(drools.loanPolicy("book", "regular", "student",  "shelf"), is("policy-b"));
  }

  @Test
  public void shelvingLocationDefaultPriority() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority: t, s, c, b, a, m, g",
        "fallback-policy: l no-loan",
        "fallback-policy: r no-hold",
        "fallback-policy: n basic-notice",
        "s new: l policy-new r no-hold n basic-notice",
        "t special-items: l policy-special r no-hold n basic-notice",
        "m book: l policy-book r no-hold n basic-notice",
        "s stacks: l policy-stacks r no-hold n basic-notice")));
    assertThat(drools.loanPolicy("book", "regular",       "student", "new"),         is("policy-new"));
    assertThat(drools.loanPolicy("book", "regular",       "student", "open-stacks"), is("policy-book"));
    assertThat(drools.loanPolicy("book", "regular",       "student", "stacks"),      is("policy-stacks"));
    assertThat(drools.loanPolicy("book", "special-items", "student", "new"),         is("policy-special"));
    assertThat(drools.loanPolicy("book", "special-items", "student", "stacks"),      is("policy-special"));
    assertThat(drools.loanPolicy("book", "special-items", "student", "open-stacks"), is("policy-special"));
  }

  @Test
  public void missingLoanPolicy() {
    try {
      Text2Drools.convert(HEADER + "m book:");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("Policy missing after ':'", 5, 8));
    }
  }

  @Test
  public void duplicateLoanPolicy() {
    try {
      Text2Drools.convert(HEADER + "m book: l policy-a l policy-b r no-hold n basic-notice");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("Only one policy of type l allowed", 5, 6));
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
    } catch (LoanRulesException e) {
      assertThat(e, matches("extraneous input 'foo'", 1, 1));
    }
  }

  @Test
  public void run100() {
    Drools drools = new Drools(Text2Drools.convert(test1));
    long start = System.currentTimeMillis();
    int n = 0;
    while (n < 100) {
      for (String [] s : test1cases) {
        drools.loanPolicy(s[0], s[1], s[2], "shelf");
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
      Text2Drools.convert("priority: last-line\nfallback-policy: l no-loan\nfallback-policy: r no-hold\n");
    } catch (LoanRulesException e) {
      assertThat(e, matches("fallback", 2, 0));
    }
  }

  @Test
  public void duplicateFallbackPolicy() {
    try {
      Text2Drools.convert(HEADER + "fallback-policy: r no-hold");
    } catch (LoanRulesException e) {
      assertThat(e, matches("Only one fallback policy of type r is allowed", 2, 0));
    }
  }

  @Test
  public void missingRequestPolicy() {
    try {
      Text2Drools.convert(HEADER + "m book: l no-loan n basic-notice");
    } catch (LoanRulesException e) {
      assertThat(e, matches("Must contain one of each policy type, missing type r", 5, 6));
    }
  }
}
