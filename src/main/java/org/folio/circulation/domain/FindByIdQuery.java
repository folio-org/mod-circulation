package org.folio.circulation.domain;

public interface FindByIdQuery extends UserRelatedQuery {
  String getItemId();
  String getUserId();
}
