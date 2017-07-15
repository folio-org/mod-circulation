package org.folio.circulation.loanrules;

import static org.junit.Assert.*;

import static org.hamcrest.core.Is.is;
import org.junit.Test;

public class DroolsTest {
  @Test
  public void test() {
    String drools = "package loanrules\n"
        + "import org.folio.circulation.loanrules.*\n"
        + "rule \"halt on result\"\n"
        + "  salience 99999"
        + "  when\n"
        + "    LoanPolicy()\n"
        + "  then\n"
        + "    drools.halt();\n"
        + "end\n"
        + "rule \"1  \\\"   \"\n"
        + "  when\n"
        + "    LoanType(name == \"one-month\")\n"
        + "  then\n"
        + "    insert(new LoanPolicy(\"rule 1\"));\n"
        + "end\n"
        + "rule \"2\"\n"
        + "  when\n"
        + "    LoanType(name == \"one-week\")\n"
        + "  then\n"
        + "    insert(new LoanPolicy(\"rule 2\"));\n"
        + "end\n"
        + "rule \"3\"\n"
        + "  when\n"
        + "  then\n"
        + "    insert(new LoanPolicy(\"rule 3\"));\n"
        + "end\n"
        + "";
    assertThat(Drools.loanPolicy(drools, null, "one-month"), is("rule 1"));
    assertThat(Drools.loanPolicy(drools, null, "one-week"), is("rule 2"));
    assertThat(Drools.loanPolicy(drools, null, "something-else"), is("rule 3"));
  }
}
