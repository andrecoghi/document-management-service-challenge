package com.clara.ops.challenge.document_management_service_challenge.document.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clara.ops.challenge.document_management_service_challenge.document.model.DocumentEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class DocumentSpecificationsTest {

  @Test
  void withUser_appliesLowerAndEqual() {
    String user = "Alice";
    @SuppressWarnings("unchecked")
    Root<DocumentEntity> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder builder = mock(CriteriaBuilder.class);
    @SuppressWarnings("unchecked")
    Path<String> userPath = mock(Path.class);
    Path<String> lowerPath = mock(Path.class);
    Predicate equalPred = mock(Predicate.class);

    when(root.<String>get("user")).thenReturn(userPath);
    when(builder.lower(userPath)).thenReturn(lowerPath);
    when(builder.equal(lowerPath, user.toLowerCase())).thenReturn(equalPred);

    Specification<DocumentEntity> spec = DocumentSpecifications.withFilters(user, null, null);
    Predicate result = spec.toPredicate(root, query, builder);

    assertThat(result).isSameAs(equalPred);
  }

  @Test
  void withName_appliesLowerAndLike() {
    String name = "Report";
    @SuppressWarnings("unchecked")
    Root<DocumentEntity> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder builder = mock(CriteriaBuilder.class);
    @SuppressWarnings("unchecked")
    Path<String> namePath = mock(Path.class);
    Path<String> lowerPath = mock(Path.class);
    Predicate likePred = mock(Predicate.class);
    String search = "%" + name.toLowerCase() + "%";

    when(root.<String>get("name")).thenReturn(namePath);
    when(builder.lower(namePath)).thenReturn(lowerPath);
    when(builder.like(lowerPath, search)).thenReturn(likePred);

    Specification<DocumentEntity> spec = DocumentSpecifications.withFilters(null, name, null);
    Predicate result = spec.toPredicate(root, query, builder);

    assertThat(result).isSameAs(likePred);
  }

  @Test
  void withTags_streamsTagsAndCombinesWithAnd() {
    List<String> tags = List.of("finance", "legal");
    @SuppressWarnings("unchecked")
    Root<DocumentEntity> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder builder = mock(CriteriaBuilder.class);
    @SuppressWarnings("unchecked")
    Path<Collection<String>> tagsPath = mock(Path.class);
    Predicate p1 = mock(Predicate.class);
    Predicate p2 = mock(Predicate.class);
    Predicate combined = mock(Predicate.class);

    when(root.<Collection<String>>get("tags")).thenReturn(tagsPath);
    when(builder.isMember("finance", tagsPath)).thenReturn(p1);
    when(builder.isMember("legal", tagsPath)).thenReturn(p2);
    when(builder.and(p1, p2)).thenReturn(combined);

    Specification<DocumentEntity> spec = DocumentSpecifications.withFilters(null, null, tags);
    Predicate result = spec.toPredicate(root, query, builder);

    assertThat(result).isSameAs(combined);
  }

  @Test
  void withTags_emptyCollectionReturnsNull() {
    List<String> tags = List.of();
    @SuppressWarnings("unchecked")
    Root<DocumentEntity> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder builder = mock(CriteriaBuilder.class);
    Predicate conj = mock(Predicate.class);

    when(builder.conjunction()).thenReturn(conj);

    Specification<DocumentEntity> spec = DocumentSpecifications.withFilters(null, null, tags);
    Predicate result = spec.toPredicate(root, query, builder);

    assertThat(result).isNull();
  }

  @Test
  void withFilters_allNullReturnsNullPredicate() {
    @SuppressWarnings("unchecked")
    Root<DocumentEntity> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder builder = mock(CriteriaBuilder.class);

    Specification<DocumentEntity> spec = DocumentSpecifications.withFilters(null, null, null);
    Predicate result = spec.toPredicate(root, query, builder);

    assertThat(result).isNull();
  }
}
