package org.folio.circulation.support.logging;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.resources.context.RenewalContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class RenewalPerformanceLoggerTest {
  private static final Logger logger = LogManager.getLogger(RenewalPerformanceLoggerTest.class);

  private TestAppender appender;
  private org.apache.logging.log4j.core.Logger coreLogger;

  @BeforeEach
  void setUp() {
    appender = new TestAppender();
    appender.start();

    coreLogger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(
      RenewalPerformanceLoggerTest.class);
    coreLogger.addAppender(appender);
    coreLogger.setLevel(Level.INFO);
  }

  @AfterEach
  void tearDown() {
    if (coreLogger != null && appender != null) {
      coreLogger.removeAppender(appender);
    }

    if (appender != null) {
      appender.stop();
    }
  }

  @Test
  void shouldCalculateDeltaMillis() {
    assertThat(RenewalPerformanceLogger.deltaMillis(125L, 150L), is(25L));
  }

  @Test
  void shouldAdvanceContextTimestamp() {
    final RenewalContext context = RenewalContext.create(null, null, "staff-id",
      "analysis-id", 125L);

    final RenewalContext advanced = RenewalPerformanceLogger.advance(context, 150L);

    assertThat(advanced.getLastPerformanceTimestampMillis(), is(150L));
    assertThat(advanced.getPerformanceAnalysisId(), is("analysis-id"));
  }

  @Test
  void shouldLogExplicitPerformanceEntryWithRequiredFields() {
    RenewalPerformanceLogger.log(logger, "analysis-id", 100L, 140L, "loan-id",
      "step={} outcome={}", "loaded-loan", "success");

    final LogEvent event = appender.getSingleEvent();

    assertThat(event.getMarker(), is(RenewalPerformanceLogger.RENEWAL_PERF_ANALYSIS));
    assertThat(event.getMessage().getFormattedMessage(),
      containsString("RENEWAL_PERF_ANALYSIS"));
    assertThat(event.getMessage().getFormattedMessage(), containsString("id=analysis-id"));
    assertThat(event.getMessage().getFormattedMessage(), containsString("nowMillis=140"));
    assertThat(event.getMessage().getFormattedMessage(), containsString("deltaMillis=40"));
    assertThat(event.getMessage().getFormattedMessage(), containsString("loanId=loan-id"));
    assertThat(event.getMessage().getFormattedMessage(), containsString("step=loaded-loan"));
    assertThat(event.getMessage().getFormattedMessage(), containsString("outcome=success"));
  }

  @Test
  void shouldLogAndAdvanceUsingContext() {
    final String loanId = UUID.randomUUID().toString();
    final RenewalContext context = RenewalContext.create(
      Loan.from(new JsonObject().put("id", loanId)), null, "staff-id", "analysis-id", 200L);

    final RenewalContext updatedContext = RenewalPerformanceLogger.logAndAdvance(logger,
      context, 260L, "step={} outcome={}", "validated-request", "success");

    final LogEvent event = appender.getSingleEvent();

    assertThat(updatedContext, notNullValue());
    assertThat(updatedContext.getLastPerformanceTimestampMillis(), is(260L));
    assertThat(event.getMarker(), is(RenewalPerformanceLogger.RENEWAL_PERF_ANALYSIS));
    assertThat(event.getMessage().getFormattedMessage(),
      containsString("RENEWAL_PERF_ANALYSIS"));
    assertThat(event.getMessage().getFormattedMessage(), containsString("id=analysis-id"));
    assertThat(event.getMessage().getFormattedMessage(), containsString("nowMillis=260"));
    assertThat(event.getMessage().getFormattedMessage(), containsString("deltaMillis=60"));
    assertThat(event.getMessage().getFormattedMessage(), containsString("loanId=" + loanId));
    assertThat(event.getMessage().getFormattedMessage(),
      containsString("step=validated-request"));
    assertThat(event.getMessage().getFormattedMessage(), containsString("outcome=success"));
  }

  private static final class TestAppender extends AbstractAppender {
    private final List<LogEvent> events = new ArrayList<>();

    private TestAppender() {
      super("RenewalPerformanceLoggerTestAppender", null, PatternLayout.createDefaultLayout(),
        false, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
      events.add(event.toImmutable());
    }

    private LogEvent getSingleEvent() {
      assertThat(events.size(), is(1));
      return events.get(0);
    }
  }
}
