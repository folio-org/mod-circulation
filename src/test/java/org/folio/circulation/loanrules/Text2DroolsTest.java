package org.folio.circulation.loanrules;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class Text2DroolsTest {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private String text1 = String.join("\n",
     "fallback-policy: no-loan",
     "m book cd dvd: policy-a",
     "m newspaper: policy-c",
     "m streaming-subscription: policy-c",
     "    g visitor: in-house",
     "    g undergrad: in-house",
     "m book cd dvd + t special-items : in-house",
     "t special-items: policy-d",
     "    g visitor: in-house"
     );
  private String drools1file = Text2Drools.convert(text1);
  private Drools drools1 = new Drools(drools1file);
  private String [][] test1 = new String[][] {
    // item type,   loan type,      patron type,   loan policies
    { "foo",       "foo",           "foo",                                                        "no-loan" },
    { "book",      "regular",       "undergrad",                                      "policy-a", "no-loan" },
    { "book",      "special-items", "undergrad",  "in-house",             "policy-a", "policy-d", "no-loan" },
    { "book",      "special-items", "visitor",    "in-house", "in-house", "policy-a", "policy-d", "no-loan" },
    { "newspaper", "regular",       "undergrad",                                      "policy-c", "no-loan" },
    { "newspaper", "special-items", "undergrad",                          "policy-c", "policy-d", "no-loan" },
    { "newspaper", "special-items", "visitor",                "in-house", "policy-c", "policy-d", "no-loan" }
  };

  private String first3(String [] s) {
    return s[0] + " " + s[1] + " " + s[2];
  }

  private String[] expected(String [] s) {
    return Arrays.copyOfRange(s, 3, s.length);
  }

  @Test
  public void test1static() {
    log.debug(drools1file);
    for (String [] s : test1) {
      assertThat(first3(s), Drools.loanPolicy(drools1file, s[0], s[1], s[2]), is(s[3]));
    }
  }

  @Test
  public void test1() {
    for (String [] s : test1) {
      assertThat(first3(s), drools1.loanPolicy(s[0], s[1], s[2]), is(s[3]));
    }
  }

  @Test
  public void test1list() {
    for (String [] s : test1) {
      assertThat(first3(s), drools1.loanPolicies(s[0], s[1], s[2]), contains(expected(s)));
    }
  }

  @Test
  public void tab() {
    try {
      Text2Drools.convert("fallback-policy: no-loan\n  \t m book: policy-a");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e.getMessage(), containsString("tab"));
      assertThat(e.getLine(), is(1));
      assertThat(e.getColumn(), is(2));
    }
  }

  @Test
  public void indentedFallbackPolicy() {
    try {
      Text2Drools.convert("m book: policy-a\n  fallback-policy: policy-b");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e.getMessage(), containsString("top level"));
      assertThat(e.getLine(), is(1));
      assertThat(e.getColumn(), is(2));
    }
  }

  @Ignore("Requires ! operator")
  @Test
  public void fallbackPolicyNotFirstToken() {
    try {
      Text2Drools.convert("! fallback-policy: policy-a");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e.getMessage(), containsString("first token"));
      assertThat(e.getLine(), is(0));
      assertThat(e.getColumn(), is(2));
    }
  }

  @Test
  public void emptyNameList() {
    Text2Drools.convert("m: policy-a");
  }

  @Test
  public void duplicateLoanPolicy() {
    try {
      Text2Drools.convert("m book: policy-a policy-b");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e.getMessage(), containsString("Unexpected token after loan policy: policy-b"));
      assertThat(e.getLine(), is(0));
      assertThat(e.getColumn(), is(17));
    }
  }

  @Test
  public void comment() {
    String drools = Text2Drools.convert("fallback-policy: no-loan\n# fallback-policy: loan-anyhow");
    assertThat(drools, not(containsString("loan-anyhow")));
  }

  @Test
  public void invalidToken() {
    try {
      Text2Drools.convert("foo");
      fail();
    } catch (LoanRulesException e) {
      assertThat(e.getMessage(), containsString("Expected"));
      assertThat(e.getLine(), is(0));
      assertThat(e.getColumn(), is(0));
    }
  }

  @Test
  public void run100() {
    long start = System.currentTimeMillis();
    int n = 0;
    while (n < 100) {
      for (String [] s : test1) {
        drools1.loanPolicy(s[0], s[1], s[2]);
        n++;
      }
    }
    long millis = System.currentTimeMillis() - start;
    float perSecond = 1000f * n / millis;
    log.debug("{} loan policy calculations per second", perSecond);
    assertThat("loan policy calculations per second", perSecond, is(greaterThan(100f)));
  }
}
