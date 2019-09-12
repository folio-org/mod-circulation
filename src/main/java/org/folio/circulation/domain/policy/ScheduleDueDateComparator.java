package org.folio.circulation.domain.policy;

import java.util.Comparator;

import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class ScheduleDueDateComparator implements Comparator<JsonObject> {

    @Override
    public int compare(JsonObject o1, JsonObject o2) {
        DateTime dateTime1 = DateTime.parse(o1.getString("due"));
        DateTime dateTime2 = DateTime.parse(o2.getString("due"));
        return dateTime1.compareTo(dateTime2);
    }

}