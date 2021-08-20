package org.folio.circulation.domain.notice.session;

import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ACTION_TYPE;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.LOAN_ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.PATRON_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class PatronSessionRecordTest {

  @Test
  void shouldMapJsonRepresentationToPatronSessionRecord() {
    String id = UUID.randomUUID().toString();
    String patronId = UUID.randomUUID().toString();
    String loanId = UUID.randomUUID().toString();

    JsonObject representation = new JsonObject()
      .put(ID, id)
      .put(PATRON_ID, patronId)
      .put(LOAN_ID, loanId)
      .put(ACTION_TYPE, "Check-in");

    PatronSessionRecord sessionRecord = PatronSessionRecord.from(representation);

    assertThat(sessionRecord.getId().toString(), is(id));
    assertThat(sessionRecord.getPatronId().toString(), is(patronId));
    assertThat(sessionRecord.getLoanId().toString(), is(loanId));
    assertThat(sessionRecord.getActionType(), is(PatronActionType.CHECK_IN));
  }
}
