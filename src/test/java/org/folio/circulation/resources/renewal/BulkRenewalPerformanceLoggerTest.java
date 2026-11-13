package org.folio.circulation.resources.renewal;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BulkRenewalPerformanceLoggerTest {
  private static final Logger logger = LogManager.getLogger(
    BulkRenewalPerformanceLoggerTest.class);

  private TestAppender appender;
  private org.apache.logging.log4j.core.Logger coreLogger;

  @BeforeEach
  void setUp() {
    appender = new TestAppender();
    appender.start();

    coreLogger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(
      BulkRenewalPerformanceLoggerTest.class);
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
  void shouldLogPerformanceEntryWithRequiredFields() {
    BulkRenewalPerformanceLogger.log(logger, "job-1", 100L, 117L, 2,
      "item-batch-fetch", 500);

    final LogEvent event = appender.getSingleEvent();
    final String message = event.getMessage().getFormattedMessage();

    assertThat(event.getMarker(), is(BulkRenewalPerformanceLogger.BULK_RENEWAL_PERF_ANALYSIS));
    assertThat(message, containsString("BULK_RENEWAL_PERF_ANALYSIS"));
    assertThat(message, containsString("jobId=job-1"));
    assertThat(message, containsString("page=2"));
    assertThat(message, containsString("step=item-batch-fetch"));
    assertThat(message, containsString("count=500"));
    assertThat(message, containsString("nowMillis=117"));
    assertThat(message, containsString("deltaMillis=17"));
  }

  private static final class TestAppender extends AbstractAppender {
    private final List<LogEvent> events = new ArrayList<>();

    private TestAppender() {
      super("BulkRenewalPerformanceLoggerTestAppender", null,
        PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY);
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
