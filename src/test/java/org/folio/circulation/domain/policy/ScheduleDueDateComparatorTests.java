package org.folio.circulation.domain.policy;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import io.vertx.core.json.JsonObject;
public class ScheduleDueDateComparatorTests {
  @Test
  public void compareTest() {
    
    DateTime dateTime1 = new DateTime(2018, 3, 14, 11, 14, 54, DateTimeZone.UTC);
    DateTime dateTime2 = new DateTime(2019, 3, 14, 11, 14, 54, DateTimeZone.UTC);
    Map<String, Object> dateTimeMap1 = new HashMap<String, Object>();
    Map<String, Object> dateTimeMap2 = new HashMap<String, Object>();
    dateTimeMap1.put("due", dateTime1.toString());
    dateTimeMap2.put("due", dateTime2.toString());
    JsonObject schedule1 = new JsonObject(dateTimeMap1);
    JsonObject schedule2 = new JsonObject(dateTimeMap2);
    ScheduleDueDateComparator comparator = new ScheduleDueDateComparator();

    assertThat(comparator.compare(schedule1, schedule2), is(-1));
    assertThat(comparator.compare(schedule1, schedule1), is(0));
    assertThat(comparator.compare(schedule2, schedule1), is(1));
  }
}
