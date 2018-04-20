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

  private static final String HEADER = "priority: last-line\nfallback-policy: no-loan\n";

  @Test
  public void headerFallbackPolicy() {
    String droolsText = Text2Drools.convert(HEADER);
    Drools drools = new Drools(droolsText);
    assertThat(drools.loanPolicy("foo", "bar", "baz", "shelf"), is("no-loan"));
  }

  private String test1 = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy: no-loan",
      "m book cd dvd: policy-a",
      "m newspaper + g all: policy-c",
      "m streaming-subscription: policy-c",
      "    g visitor: in-house",
      "    g undergrad: in-house",
      "m book cd dvd + t special-items : in-house",
      "t special-items: policy-d",
      "    g visitor: in-house"
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
          "fallback-policy: no-loan",
          "m book: policy-a"));
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
        "  t special-items: in-house",
        "m book: policy-b",
        "fallback-policy: no-loan",
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
      Text2Drools.convert(HEADER + "  \t m book: policy-a");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("Tab", 3, 4));
    }
  }

  @Test
  public void missingPriority() {
    try {
      Text2Drools.convert("fallback-policy: no-loan");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("priority", 1, 1));
    }
  }

  @Test
  public void reject6CriteriumTypes() {
    try {
      Text2Drools.convert("priority: t g m a b c\nfallback-policy: no-loan");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("7 letters expected, found only 6", 1, 11));
    }
  }

  @Test
  public void reject8CriteriumTypes() {
    try {
      Text2Drools.convert("priority: t g m a b c s s\nfallback-policy: no-loan");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("Only 7 letters expected, found 8", 1, 11));
    }
  }

  @Test
  public void duplicateCriteriumType() {
    try {
      Text2Drools.convert("priority: t g m a b s s\nfallback-policy: no-loan");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("Duplicate letter s", 1, 23));
    }
  }

  @Test
  public void duplicatePriorityType() {
    try {
      Text2Drools.convert("priority: number-of-criteria, number-of-criteria, last-line\nfallback-policy: no-loan");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("Duplicate priority", 1, 31));
    }
  }

  @Test
  public void twoPriorities() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority: number-of-criteria, first-line",
        "fallback-policy: no-loan",
        "m book: policy-a",
        "g student: policy-b",
        "m dvd: policy-c",
        "     g visitor: policy-d"
        )));
    assertThat(drools.loanPolicy("book", "regular", "student", "shelf"), is("policy-a"));
    assertThat(drools.loanPolicy("dvd",  "regular", "student", "shelf"), is("policy-b"));
    assertThat(drools.loanPolicy("dvd",  "regular", "visitor", "shelf"), is("policy-d"));
  }

  @Test
  public void threePriorities() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority: criterium(t, s, c, b, a, m, g), number-of-criteria, first-line",
        "fallback-policy: no-loan",
        "m book: policy-a",
        "g student: policy-b",
        "m dvd: policy-c",
        "     g visitor: policy-d"
        )));
    assertThat(drools.loanPolicy("book", "regular", "student", "shelf"), is("policy-a"));
    assertThat(drools.loanPolicy("dvd",  "regular", "student", "shelf"), is("policy-c"));
    assertThat(drools.loanPolicy("dvd",  "regular", "visitor", "shelf"), is("policy-d"));
  }

  @Test
  public void missingFallbackPolicy() {
    try {
      Text2Drools.convert("priority: last-line\nm book: policy-a");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("fallback", 2, 1));
    }
  }

  @Test
  public void missingFallbackPolicyFirstLine() {
    try {
      Text2Drools.convert("priority: first-line\nm book: policy-a");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("fallback", 2, 17));
    }
  }

  @Test
  public void indentedFallbackPolicy() {
    try {
      Text2Drools.convert(HEADER + "m book\n  fallback-policy: policy-b");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("mismatched input 'fallback-policy'", 4, 3));
    }
  }

  @Test
  public void exclamationBeforePriority() {
    try {
      Text2Drools.convert("! fallback-policy: policy-a");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("mismatched input '!'", 1, 1));
    }
  }

  @Test
  public void emptyNameList() {
    try {
      Text2Drools.convert(HEADER + "m: policy-a");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("Name missing", 3, 2));
    }
  }

  @Test
  public void negation() {
    Drools drools = new Drools(Text2Drools.convert(HEADER + "m !dvd !music: policy-a"));
    assertThat(drools.loanPolicy("dvd",       "regular", "student", "shelf"), is("no-loan"));
    assertThat(drools.loanPolicy("music",     "regular", "student", "shelf"), is("no-loan"));
    assertThat(drools.loanPolicy("newspaper", "regular", "student", "shelf"), is("policy-a"));
  }

  @Test
  public void negationSingle() {
    Drools drools = new Drools(Text2Drools.convert(HEADER + "m !dvd: policy-a"));
    assertThat(drools.loanPolicy("dvd",       "regular", "student", "shelf"), is("no-loan"));
    assertThat(drools.loanPolicy("newspaper", "regular", "student", "shelf"), is("policy-a"));
  }

  @Test
  public void shelvingLocation() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority: last-line",
        "fallback-policy: no-loan",
        "s new: policy-a",
        "m book: policy-b",
        "a new: policy-c",
        "b new: policy-d",
        "c new: policy-e")));
    assertThat(drools.loanPolicy("dvd",  "regular", "student",  "new"),   is("policy-a"));
    assertThat(drools.loanPolicy("book", "regular", "student",  "new"),   is("policy-b"));
    assertThat(drools.loanPolicy("book", "regular", "student",  "shelf"), is("policy-b"));
  }

  @Test
  public void shelvingLocationDefaultPriority() {
    Drools drools = new Drools(Text2Drools.convert(String.join("\n",
        "priority: t, s, c, b, a, m, g",
        "fallback-policy: no-loan",
        "s new: policy-new",
        "t special-items: policy-special",
        "m book: policy-book",
        "s stacks: policy-stacks")));
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
      assertThat(e, matches("Policy missing after ':'", 3, 8));
    }
  }

  @Test
  public void duplicateLoanPolicy() {
    try {
      Text2Drools.convert(HEADER + "m book: policy-a policy-b");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e, matches("Only one policy allowed", 3, 26));
    }
  }

  @Test
  public void comment() {
    String drools = Text2Drools.convert(HEADER + "# m book: loan-anyhow");
    assertThat(drools, not(containsString("loan-anyhow")));
  }

  @Test
  public void commentWithoutSpace() {
    String drools = Text2Drools.convert(HEADER + "#m book: loan-anyhow");
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
}
