package org.folio.circulation.domain.notice;

import static java.util.stream.Collectors.toCollection;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.User;

public class PatronNoticeConfigurationResolver {
  private static final Logger log = LogManager.getLogger(PatronNoticeConfigurationResolver.class);

  public static List<NoticeConfiguration> firstMatchGroup(List<NoticeConfiguration> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    var key = candidates.getFirst().matchKey();
    return candidates.stream()
      .filter(candidate -> key.equals(candidate.matchKey()))
      .toList();
  }

  public List<NoticeConfiguration> select(List<NoticeConfiguration> matchGroup, User recipient) {
    if (matchGroup == null || matchGroup.isEmpty()) {
      return List.of();
    }

    var group = firstMatchGroup(matchGroup);
    if (group.size() != matchGroup.size()) {
      log.warn("select:: received {} configurations spanning several match groups, " +
        "narrowing to the first group of {}", matchGroup.size(), group.size());
    }

    var byFormat = indexByFormat(group);
    if (byFormat.isEmpty()) {
      return List.of();
    }

    if (byFormat.size() == 1) {
      return List.copyOf(byFormat.values());
    }

    var selected = selectByPreferences(byFormat, recipient);
    if (selected.isEmpty()) {
      log.warn("select:: no notice configuration selected for recipient {}, event {}, " +
          "available formats {} - sending nothing",
        recipient == null ? null : recipient.getId(), eventTypeOf(group), byFormat.keySet());
    }
    return selected;
  }

  public Set<NoticeFormat> resolveFormats(List<NoticeConfiguration> matchGroup, User recipient) {
    return select(matchGroup, recipient).stream()
      .map(NoticeConfiguration::getNoticeFormat)
      .collect(toCollection(LinkedHashSet::new));
  }

  public boolean requiresPreference(List<NoticeConfiguration> matchGroup) {
    return indexByFormat(firstMatchGroup(matchGroup)).size() > 1;
  }

  private List<NoticeConfiguration> selectByPreferences(
    Map<NoticeFormat, NoticeConfiguration> byFormat, User recipient) {

    var preferredFormats =
      UserPreferredNoticeFormats.fromPreferredContactTypeIds(recipient);

    if (!preferredFormats.isEmpty()) {
      return preferredFormats.stream()
        .map(byFormat::get)
        .filter(Objects::nonNull)
        .toList();
    }

    var deprecatedFormat =
      UserPreferredNoticeFormats.fromDeprecatedPreferredContactTypeId(recipient);

    if (deprecatedFormat.isPresent()) {
      var configuration = byFormat.get(deprecatedFormat.get());

      return configuration == null
        ? List.of()
        : List.of(configuration);
    }

    return emailOnly(byFormat);
  }

  private static List<NoticeConfiguration> emailOnly(
    Map<NoticeFormat, NoticeConfiguration> byFormat) {
    var emailConfiguration = byFormat.get(NoticeFormat.EMAIL);
    return emailConfiguration == null ? List.of() : List.of(emailConfiguration);
  }

  private static Map<NoticeFormat, NoticeConfiguration> indexByFormat(
    List<NoticeConfiguration> matchGroup) {

    var byFormat = new LinkedHashMap<NoticeFormat, NoticeConfiguration>();
    for (NoticeConfiguration configuration : matchGroup) {
      NoticeFormat format = configuration.getNoticeFormat();
      if (format == null || !format.isDeliverable()) {
        log.warn("indexByFormat:: skipping notice configuration {} with undeliverable format {}",
          configuration.getTemplateId(), format);
        continue;
      }
      byFormat.putIfAbsent(format, configuration);
    }
    return byFormat;
  }

  private static NoticeEventType eventTypeOf(List<NoticeConfiguration> group) {
    return group.isEmpty() ? null : group.getFirst().getNoticeEventType();
  }
}
