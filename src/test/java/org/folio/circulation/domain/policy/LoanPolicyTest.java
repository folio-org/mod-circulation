package org.folio.circulation.domain.policy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class LoanPolicyTest {

  @Test
  void testToString() {
    var loanPolicyId = UUID.randomUUID();
    var loanPolicyName = "Test Loan Policy";
    var representation = new JsonObject()
      .put("id", loanPolicyId)
      .put("name", loanPolicyName);
    var expectedValue = String.format("LoanPolicy(representation={\"id\":\"%s\",\"name\":\"%s\"})",
      loanPolicyId, loanPolicyName);

    assertThat(new LoanPolicy(representation, null, null, null)
      .toString(), is(expectedValue));
  }
}
