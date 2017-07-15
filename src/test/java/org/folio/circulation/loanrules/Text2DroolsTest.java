package org.folio.circulation.loanrules;

import static org.hamcrest.core.Is.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class Text2DroolsTest {

  @Test
  public void test() {
    String text = ""
        + "m book cd dvd: regular-loan\n"
        + "  t rare: no-loan\n"
        + "  t heavy-demand : one-day\n"
        + "m newspaper : readingroom\n"
        + "t special-item : special-loan\n";
    String drools = Text2Drools.convert(text);
    System.out.println(drools);
    assertThat(Drools.loanPolicy(drools, "book", "regular"), is("regular-loan"));
    assertThat(Drools.loanPolicy(drools, "book", "rare"), is("no-loan"));
    assertThat(Drools.loanPolicy(drools, "dvd", "regular"), is("regular-loan"));
    assertThat(Drools.loanPolicy(drools, "dvd", "rare"), is("no-loan"));
    assertThat(Drools.loanPolicy(drools, "book", "heavy-demand"), is("one-day"));
    assertThat(Drools.loanPolicy(drools, "newspaper", "heavy-demand"), is("readingroom"));
    assertThat(Drools.loanPolicy(drools, "book", "special-item"), is("regular-loan"));
    assertThat(Drools.loanPolicy(drools, "laptop", "special-item"), is("special-loan"));
  }
}
