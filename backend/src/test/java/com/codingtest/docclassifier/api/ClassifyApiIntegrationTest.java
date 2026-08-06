package com.codingtest.docclassifier.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.codingtest.docclassifier.TestMaterials;

/**
 * 실제 PDF를 업로드해 API가 끝까지 동작하는지 확인한다. STEP 6의 통합 검증이다.
 *
 * <p>애플리케이션을 통째로 띄우고 진짜 파이프라인(텍스트 추출 → OCR → 규칙 → LLM → 그룹핑)을 태운다.
 * 단계별 테스트가 모두 통과해도 스프링 배선이나 업로드 처리에서 어긋나면 API는 동작하지 않으므로,
 * 실제 요청 한 번을 끝까지 통과시켜 보는 검증이 따로 필요하다.
 *
 * <p>제공 자료 PDF와 Gemini API 키가 둘 다 있어야 돌아간다. 없으면 실패가 아니라 건너뛴다.
 * package_01은 규칙이 7장을 보류하므로 이 테스트는 매번 그만큼 API를 호출한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClassifyApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	/** 애플리케이션에 실제로 주입된 Gemini 키. 비어 있으면 2차 판정이 동작하지 않아 테스트를 건너뛴다 */
	@Value("${gemini.api-key:}")
	private String apiKey;

	@Test
	@DisplayName("package_01을 정답지와 함께 올리면 39페이지가 분류되고 정확도까지 그 자리에서 나온다")
	void classifiesPackage01EndToEnd() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");
		Assumptions.assumeTrue(!apiKey.isBlank(), "GOOGLE_API_KEY가 없어 건너뜁니다");

		Path shuffledPdf = TestMaterials.package01Shuffled();
		MockMultipartFile upload = new MockMultipartFile(
				"file", "package_01_shuffled.pdf", MediaType.APPLICATION_PDF_VALUE, Files.readAllBytes(shuffledPdf));

		mockMvc.perform(multipart("/api/classify").file(upload).param("groundTruth", "package_01"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalPages").value(39))
				.andExpect(jsonPath("$.pages.length()").value(39))
				// 판정하지 못한 페이지가 남으면 규칙과 LLM 중 어느 쪽도 답을 내지 못했다는 뜻이다
				.andExpect(jsonPath("$.pages[?(@.label == 'UNDECIDED')]").isEmpty())
				// package_01의 원본은 URLA·신용·권원·소득 각 1부씩 4개 문서다
				.andExpect(jsonPath("$.documents.length()").value(4))
				.andExpect(jsonPath("$.ungroupedPages").isEmpty())
				// 저장된 수치가 아니라 이번 업로드를 정답지와 대조해 방금 잰 값이다
				.andExpect(jsonPath("$.accuracy.totalPages").value(39))
				.andExpect(jsonPath("$.accuracy.correctPages").value(39))
				.andExpect(jsonPath("$.accuracy.undecidedPages").value(0));
	}
}
