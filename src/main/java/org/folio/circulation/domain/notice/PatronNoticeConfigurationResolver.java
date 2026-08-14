package org.folio.circulation.domain.notice;

import static java.util.stream.Collectors.toCollection;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.User;

public class PatronNoticeConfigurationResolver {
  private static final Logger log = LogManager.getLogger(PatronNoticeConfigurationResolver.class);

  private PatronNoticeConfigurationResolver() {
  }

  public static List<NoticeConfiguration> matchGroupOf(
    List<NoticeConfiguration> candidates, NoticeConfiguration anchor) {

    if (CollectionUtils.isEmpty(candidates) || anchor == null) {
      return List.of();
    }

    var key = anchor.matchKey();

    return candidates.stream()
      .filter(candidate -> key.equals(candidate.matchKey()))
      .toList();
  }

  public static List<NoticeConfiguration> firstMatchGroup(List<NoticeConfiguration> candidates) {
    return CollectionUtils.isEmpty(candidates)
      ? List.of()
      : matchGroupOf(candidates, candidates.getFirst());
  }

  public static List<NoticeConfiguration> select(
    List<NoticeConfiguration> matchGroup, User recipient) {

    var byFormat = indexByFormat(matchGroup);

    if (byFormat.isEmpty()) {
      return List.of();
    }

    if (byFormat.size() == 1) {
      return List.copyOf(byFormat.values());
    }

    return selectByPreferences(byFormat, recipient);
  }

  public static Set<NoticeFormat> resolveFormats(
    List<NoticeConfiguration> matchGroup, User recipient) {

    return select(matchGroup, recipient).stream()
      .map(NoticeConfiguration::getNoticeFormat)
      .collect(toCollection(LinkedHashSet::new));
  }

  public static boolean requiresPreference(List<NoticeConfiguration> matchGroup) {
    return indexByFormat(matchGroup).size() > 1;
  }

  private static List<NoticeConfiguration> selectByPreferences(
    Map<NoticeFormat, NoticeConfiguration> byFormat, User recipient) {

    var preferredFormats = UserPreferredNoticeFormats.fromPreferredContactTypeIds(recipient);

    if (!preferredFormats.isEmpty()) {
      var matched = preferredFormats.stream()
        .map(byFormat::get)
        .filter(Objects::nonNull)
        .toList();

      if (!matched.isEmpty()) {
        return matched;
      }

      log.info("selectByPreferences:: preferred formats {} are not configured, available {} - " +
        "falling back to default channel", preferredFormats, byFormat.keySet());
    } else {
      var deprecatedFormat =
        UserPreferredNoticeFormats.fromDeprecatedPreferredContactTypeId(recipient);

      if (deprecatedFormat.isPresent()) {
        var configuration = byFormat.get(deprecatedFormat.get());

        if (configuration != null) {
          return List.of(configuration);
        }

        log.info("selectByPreferences:: preferred format {} is not configured, available {} - " +
          "falling back to default channel", deprecatedFormat.get(), byFormat.keySet());
      }
    }
    return defaultChannel(byFormat);
  }

  private static List<NoticeConfiguration> defaultChannel(
    Map<NoticeFormat, NoticeConfiguration> byFormat) {

    return List.of(ObjectUtils.getIfNull(byFormat.get(NoticeFormat.EMAIL),
      () -> byFormat.values().iterator().next()));
  }

  private static Map<NoticeFormat, NoticeConfiguration> indexByFormat(
    List<NoticeConfiguration> matchGroup) {

    var byFormat = new LinkedHashMap<NoticeFormat, NoticeConfiguration>();

    if (CollectionUtils.isEmpty(matchGroup)) {
      return byFormat;
    }

    NoticeConfigurationMatchKey groupKey = null;

    for (NoticeConfiguration configuration : matchGroup) {
      if (groupKey == null) {
        groupKey = configuration.matchKey();
      } else if (!groupKey.equals(configuration.matchKey())) {
        continue;
      }

      var format = configuration.getNoticeFormat();

      if (format == null || !format.isDeliverable()) {
        log.debug("indexByFormat:: skipping configuration with template {}, format {} is not " +
          "deliverable", configuration.getTemplateId(), format);
        continue;
      }

      var previous = byFormat.putIfAbsent(format, configuration);

      if (previous != null) {
        log.warn("indexByFormat:: match group contains several configurations for format {}, " +
            "using template {} and ignoring template {}", format, previous.getTemplateId(),
          configuration.getTemplateId());
      }
    }

    return byFormat;
  }
}
