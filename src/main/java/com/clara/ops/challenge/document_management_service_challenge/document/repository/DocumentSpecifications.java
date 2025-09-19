package com.clara.ops.challenge.document_management_service_challenge.document.repository;

import com.clara.ops.challenge.document_management_service_challenge.document.model.DocumentEntity;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;

public final class DocumentSpecifications {

  private DocumentSpecifications() {}

  public static Specification<DocumentEntity> withFilters(
      String user, String name, Collection<String> tags) {
    Specification<DocumentEntity> specification = Specification.where(null);
    if (user != null && !user.isBlank()) {
      specification = specification.and(withUser(user));
    }
    if (name != null && !name.isBlank()) {
      specification = specification.and(withName(name));
    }
    if (tags != null && !tags.isEmpty()) {
      specification = specification.and(withTags(tags));
    }
    return specification;
  }

  private static Specification<DocumentEntity> withUser(String user) {
    return (root, query, builder) ->
        builder.equal(builder.lower(root.get("user")), user.toLowerCase());
  }

  private static Specification<DocumentEntity> withName(String name) {
    String search = "%" + name.toLowerCase() + "%";
    return (root, query, builder) -> builder.like(builder.lower(root.get("name")), search);
  }

  private static Specification<DocumentEntity> withTags(Collection<String> tags) {
    return (root, query, builder) -> {
      query.distinct(true);
      return tags.stream()
          .map(tag -> builder.isMember(tag, root.get("tags")))
          .reduce(builder::and)
          .orElseGet(builder::conjunction);
    };
  }
}
