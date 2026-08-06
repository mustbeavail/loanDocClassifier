package com.codingtest.docclassifier.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.pipeline.ClassificationResult;
import com.codingtest.docclassifier.pipeline.ClassificationResult.DocumentResult;
import com.codingtest.docclassifier.pipeline.ClassificationResult.PageResult;
import com.codingtest.docclassifier.pipeline.ClassificationService;
import com.codingtest.docclassifier.pipeline.DecidedBy;

/**
 * {@link ClassificationController}의 요청·응답 규약을 확인한다.
 *
 * <p>파이프라인은 가짜로 바꿔 둔다. 실제 PDF로 끝까지 돌려 보는 검증은
 * {@link ClassifyApiIntegrationTest}가 따로 하고, 여기서는 응답 형태와 잘못된 요청 처리만 본다.
 * 이 둘을 한 테스트에 섞으면 400 응답 하나를 확인하는 데도 PDF 전체 처리가 필요해진다.
 */
@WebMvcTest(ClassificationController.class)
class ClassificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	/** 진짜 파이프라인 대신 주입되는 가짜. 어떤 결과를 낼지 테스트마다 정한다 */
	@MockitoBean
	private ClassificationService classificationService;

	/**
	 * 업로드할 PDF 파일을 흉내 낸다.
	 *
	 * @param content 파일 내용
	 * @return multipart 요청에 실을 가짜 파일
	 */
	private MockMultipartFile pdfFile(byte[] content) {
		return new MockMultipartFile("file", "package_01.pdf", MediaType.APPLICATION_PDF_VALUE, content);
	}

	@Test
	@DisplayName("PDF를 올리면 페이지 판정과 문서 목록이 함께 돌아온다")
	void returnsPagesAndDocuments() throws Exception {
		ClassificationResult stubbed = new ClassificationResult(
				"package_01.pdf",
				2,
				List.of(
						new PageResult(1, DocumentType.URLA_1003, DecidedBy.RULE, false,
								"freddie mac form 65", "1 of 11"),
						new PageResult(2, DocumentType.TITLE_REPORT, DecidedBy.LLM, true,
								"권원 조사 결과가 적혀 있다", null)),
				List.of(new DocumentResult(DocumentType.URLA_1003, 11, 1, 2, List.of(1, 2))),
				List.of(),
				null);
		when(classificationService.classify(any(), anyString(), any())).thenReturn(stubbed);

		mockMvc.perform(multipart("/api/classify").file(pdfFile("가짜 PDF 내용".getBytes())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fileName").value("package_01.pdf"))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.pages.length()").value(2))
				.andExpect(jsonPath("$.pages[0].label").value("URLA_1003"))
				.andExpect(jsonPath("$.pages[0].decidedBy").value("RULE"))
				.andExpect(jsonPath("$.pages[0].ocrUsed").value(false))
				.andExpect(jsonPath("$.pages[0].marker").value("1 of 11"))
				.andExpect(jsonPath("$.pages[1].decidedBy").value("LLM"))
				.andExpect(jsonPath("$.pages[1].ocrUsed").value(true))
				.andExpect(jsonPath("$.documents[0].startPage").value(1))
				.andExpect(jsonPath("$.documents[0].endPage").value(2))
				.andExpect(jsonPath("$.ungroupedPages").isEmpty());
	}

	@Test
	@DisplayName("정답지 이름을 함께 보내면 그대로 파이프라인에 넘긴다")
	void passesGroundTruthToPipeline() throws Exception {
		when(classificationService.classify(any(), anyString(), any())).thenReturn(
				new ClassificationResult("package_01.pdf", 0, List.of(), List.of(), List.of(), null));

		mockMvc.perform(multipart("/api/classify")
				.file(pdfFile("가짜 PDF 내용".getBytes()))
				.param("groundTruth", "package_01"))
				.andExpect(status().isOk());

		verify(classificationService).classify(any(), anyString(), eq("package_01"));
	}

	@Test
	@DisplayName("빈 파일을 올리면 400과 한글 안내 메시지가 온다")
	void rejectsEmptyFile() throws Exception {
		mockMvc.perform(multipart("/api/classify").file(pdfFile(new byte[0])))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("업로드된 파일이 비어 있습니다."));
	}

	@Test
	@DisplayName("PDF가 아닌 파일을 올리면 400과 한글 안내 메시지가 온다")
	void rejectsUnreadablePdf() throws Exception {
		// PDF가 아니거나 깨진 파일이면 파싱 단계에서 IOException이 올라온다
		when(classificationService.classify(any(), anyString(), any()))
				.thenThrow(new IOException("Error: End-of-File, expected line"));

		mockMvc.perform(multipart("/api/classify").file(pdfFile("이건 PDF가 아니다".getBytes())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("PDF를 읽지 못했습니다. 올바른 PDF 파일인지 확인해 주세요."));
	}

	@Test
	@DisplayName("파일을 빠뜨리고 요청하면 400과 한글 안내 메시지가 온다")
	void rejectsRequestWithoutFile() throws Exception {
		mockMvc.perform(multipart("/api/classify"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("PDF 파일을 함께 보내 주세요."));
	}

	@Test
	@DisplayName("multipart가 아닌 요청도 500이 아니라 400으로 답한다")
	void rejectsNonMultipartRequest() throws Exception {
		// 요청이 잘못된 것이므로 서버 오류(500)로 답하면 고칠 쪽을 잘못 가리키게 된다
		mockMvc.perform(post("/api/classify"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("PDF 파일을 함께 보내 주세요."));
	}
}
