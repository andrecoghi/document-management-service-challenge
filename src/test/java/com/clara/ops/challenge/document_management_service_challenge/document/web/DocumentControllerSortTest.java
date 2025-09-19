package com.clara.ops.challenge.document_management_service_challenge.document.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.clara.ops.challenge.document_management_service_challenge.document.mapper.DocumentMapper;
import com.clara.ops.challenge.document_management_service_challenge.document.service.DocumentService;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class DocumentControllerSortTest {

  private final DocumentController controller =
      new DocumentController(mock(DocumentService.class), mock(DocumentMapper.class));

  private Sort invokeBuildSort(List<String> sortParams) throws Exception {
    Method method = DocumentController.class.getDeclaredMethod("buildSort", List.class);
    method.setAccessible(true);
    return (Sort) method.invoke(controller, sortParams);
  }

  @Test
  void defaultSortWhenNull() throws Exception {
    Sort sort = invokeBuildSort(null);
    assertThat(sort).hasSize(1);
    assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void defaultSortWhenEmpty() throws Exception {
    Sort sort = invokeBuildSort(List.of());
    assertThat(sort).hasSize(1);
    assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void skipBlankParams() throws Exception {
    Sort sort = invokeBuildSort(List.of(" ", ""));
    assertThat(sort).hasSize(1);
    assertThat(sort.getOrderFor("createdAt")).isNotNull();
  }

  @Test
  void parseValidParams() throws Exception {
    Sort sort = invokeBuildSort(List.of("name,asc", "date,desc"));
    assertThat(sort).hasSize(2);
    assertThat(sort.getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
    assertThat(sort.getOrderFor("date").getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void invalidDirectionDefaultsToDesc() throws Exception {
    Sort sort = invokeBuildSort(List.of("name,invalid"));
    assertThat(sort.getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.DESC);
  }

  @Test
  void mixedParamsIncludesValidAndInvalidAndNull() throws Exception {
    Sort sort = invokeBuildSort(Arrays.asList("name,asc", null, "invalidParam", "date"));
    assertThat(sort.getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
    assertThat(sort.getOrderFor("date").getDirection()).isEqualTo(Sort.Direction.DESC);
  }
}
