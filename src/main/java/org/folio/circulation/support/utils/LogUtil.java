package org.folio.circulation.support.utils;

import static com.google.common.primitives.Ints.min;
import static java.lang.String.format;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.json.JsonObject;

public class LogUtil {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Logger log = LogManager.getLogger(LogUtil.class);
  private static final int MAX_OBJECT_JSON_LENGTH = 10 * 1024;
  private static final int DEFAULT_NUM_OF_LIST_ELEMENTS_TO_LOG = 10;

  private LogUtil() {
    throw new IllegalStateException("Utility class");
  }

  public static String asJson(Object object) {
    if (object == null) {
      return null;
    }

    if (object instanceof JsonObject) {
      try {
        return crop(((JsonObject) object).encode());
      } catch (Exception ex) {
        log.warn("logAsJson:: Error while logging JsonObject", ex);
        return null;
      }
    }

    try {
      return crop(MAPPER.writeValueAsString(object));
    } catch (JsonProcessingException jsonProcessingException) {
      log.warn("logAsJson:: Error while logging an object of type {}",
        object.getClass().getCanonicalName(), jsonProcessingException);
      return null;
    } catch (Exception ex) {
      log.warn("logAsJson:: Unexpected error while logging an object of type {}",
        object.getClass().getCanonicalName(), ex);
      return null;
    }
  }

  public static String asJson(List<?> list) {
    return asJson(list, DEFAULT_NUM_OF_LIST_ELEMENTS_TO_LOG);
  }

  public static String asJson(List<?> list, int maxNumberOfElementsToLog) {
    try {
      if (list == null) {
        return null;
      } else {
        int numberOfElementsToLog = min(list.size(), maxNumberOfElementsToLog);
        return format("list(size: %d, %s: [%s])", list.size(),
          numberOfElementsToLog == list.size() ? "elements"
            : format("first %d element%s", numberOfElementsToLog, plural(numberOfElementsToLog)),
          list.subList(0, numberOfElementsToLog).stream()
            .map(LogUtil::asJson)
            .collect(Collectors.joining(", ")));
      }
    } catch (Exception ex) {
      log.warn("logList:: Failed to log a list", ex);
      return null;
    }
  }

  private static String plural(int number) {
    return number == 1 ? "" : "s";
  }

  public static String headersAsString(Map<String, String> okapiHeaders) {
    try {
      Map<String, String> headersCopy= new CaseInsensitiveMap<>(okapiHeaders);
      headersCopy.remove("x-okapi-token");
      return headersCopy.toString();
    } catch (Exception ex) {
      log.warn("logOkapiHeaders:: Failed to log Okapi headers", ex);
      return null;
    }
  }

  protected static String crop(String str) {
    try {
      return str.substring(0, min(str.length(), MAX_OBJECT_JSON_LENGTH));
    } catch (Exception ex) {
      log.warn("crop:: Failed to crop a string", ex);
      return null;
    }
  }
}
