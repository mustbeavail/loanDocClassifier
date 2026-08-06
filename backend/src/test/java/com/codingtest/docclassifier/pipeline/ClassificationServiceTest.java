package com.codingtest.docclassifier.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.codingtest.docclassifier.accuracy.AccuracyEvaluator;
import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.classify.RuleClassifier;
import com.codingtest.docclassifier.group.DocumentGroup;
import com.codingtest.docclassifier.group.DocumentGrouper;
import com.codingtest.docclassifier.group.GroupingResult;
import com.codingtest.docclassifier.group.PageMarkerParser;
import com.codingtest.docclassifier.llm.GeminiClassifier;
import com.codingtest.docclassifier.llm.LlmDecision;
import com.codingtest.docclassifier.ocr.OcrFallback;
import com.codingtest.docclassifier.pdf.PdfPage;
import com.codingtest.docclassifier.pdf.PdfPageReader;
import com.codingtest.docclassifier.pipeline.ClassificationResult.DocumentResult;
import com.codingtest.docclassifier.pipeline.ClassificationResult.PageResult;

/**
 * {@link ClassificationService}가 단계를 올바른 조건에서만 부르는지 확인한다.
 *
 * <p>PDF 읽기·OCR·LLM·그룹핑은 가짜(mock)로 바꾸고, 판정 규칙과 순번 파서는 진짜를 쓴다.
 * 여기서 보려는 것은 각 단계가 <b>제 역할을 하는지</b>가 아니라
 * (그건 STEP 1~5에서 이미 각자 테스트했다) <b>어떤 페이지가 어느 단계로 흘러가는지</b>다.
 * OCR을 전 페이지에 돌리거나 규칙이 이미 판정한 페이지를 LLM에 다시 묻는 실수는
 * 결과가 맞아 보여도 시간과 비용을 몇 배로 쓰게 만들어, 결과만 봐서는 드러나지 않는다.
 */
class ClassificationServiceTest {

	/** 가짜 PDF 경로. 실제로 열지 않는다 (PDF를 읽는 단계가 가짜로 바뀌어 있다) */
	private static final Path PDF_PATH = Path.of("shuffled.pdf");

	private final PdfPageReader pageReader = mock(PdfPageReader.class);
	private final OcrFallback ocrFallback = mock(OcrFallback.class);
	private final GeminiClassifier geminiClassifier = mock(GeminiClassifier.class);
	private final DocumentGrouper documentGrouper = mock(DocumentGrouper.class);

	private final ClassificationService service = new ClassificationService(
			pageReader, ocrFallback, new RuleClassifier(), geminiClassifier,
			new PageMarkerParser(), documentGrouper, new AccuracyEvaluator());

	/**
	 * PDF가 주어진 페이지들로 읽히도록 준비하고, 그룹핑은 빈 결과를 내도록 둔다.
	 *
	 * @param pages 읽힌 것으로 할 페이지들
	 * @throws IOException 가짜 설정이라 실제로는 발생하지 않는다
	 */
	private void givenPages(PdfPage... pages) throws IOException {
		when(pageReader.read(PDF_PATH)).thenReturn(List.of(pages));
		when(documentGrouper.group(any())).thenReturn(new GroupingResult(List.of(), List.of()));
	}

	/**
	 * 텍스트 레이어가 있는 페이지를 만든다.
	 *
	 * @param pageNumber 페이지 번호
	 * @param text       페이지 텍스트
	 * @return 만들어진 페이지
	 */
	private PdfPage textPage(int pageNumber, String text) {
		return new PdfPage(pageNumber, text, 0, 612, 792);
	}

	/**
	 * 텍스트 레이어가 없는 스캔 페이지를 만든다.
	 *
	 * @param pageNumber 페이지 번호
	 * @return 텍스트가 비어 있고 이미지를 가진 페이지
	 */
	private PdfPage scannedPage(int pageNumber) {
		return new PdfPage(pageNumber, "", 1, 612, 792);
	}

	@Test
	@DisplayName("텍스트가 있는 페이지에는 OCR을 돌리지 않는다")
	void skipsOcrWhenTextLayerExists() throws Exception {
		givenPages(textPage(1, "Uniform Residential Loan Application"));

		ClassificationResult result = service.classify(PDF_PATH, "test.pdf", null);

		verify(ocrFallback, never()).readText(any(), anyInt());
		assertThat(result.pages().get(0).ocrUsed()).isFalse();
	}

	@Test
	@DisplayName("텍스트가 비어 있는 스캔 페이지만 OCR로 읽고 그 결과로 판정한다")
	void usesOcrOnlyForScannedPages() throws Exception {
		givenPages(
				textPage(1, "Uniform Residential Loan Application"),
				scannedPage(2));
		when(ocrFallback.readText(PDF_PATH, 2)).thenReturn("COMMITMENT FOR TITLE INSURANCE");

		ClassificationResult result = service.classify(PDF_PATH, "test.pdf", null);

		verify(ocrFallback, times(1)).readText(PDF_PATH, 2);

		PageResult scanned = result.pages().get(1);
		assertThat(scanned.ocrUsed()).isTrue();
		// OCR로 읽은 글자가 규칙 분류까지 이어져야 스캔 페이지가 분류에 포함된다
		assertThat(scanned.label()).isEqualTo(DocumentType.TITLE_REPORT);
		assertThat(scanned.decidedBy()).isEqualTo(DecidedBy.RULE);
	}

	@Test
	@DisplayName("규칙이 판정한 페이지는 LLM에 다시 묻지 않는다")
	void doesNotAskLlmWhenRuleDecided() throws Exception {
		givenPages(textPage(1, "Freddie Mac Form 65"));

		ClassificationResult result = service.classify(PDF_PATH, "test.pdf", null);

		verify(geminiClassifier, never()).classify(anyString());
		assertThat(result.pages().get(0).label()).isEqualTo(DocumentType.URLA_1003);
		assertThat(result.pages().get(0).decidedBy()).isEqualTo(DecidedBy.RULE);
		assertThat(result.pages().get(0).evidence()).isEqualTo("freddie mac form 65");
	}

	@Test
	@DisplayName("규칙이 보류한 페이지만 LLM으로 넘어간다")
	void asksLlmOnlyForUndecidedPages() throws Exception {
		givenPages(
				textPage(1, "Freddie Mac Form 65"),
				textPage(2, "어느 키워드에도 걸리지 않는 본문"));
		when(geminiClassifier.classify("어느 키워드에도 걸리지 않는 본문"))
				.thenReturn(new LlmDecision(DocumentType.INCOME_DOC, 0.9, "급여 항목이 나열되어 있다"));

		ClassificationResult result = service.classify(PDF_PATH, "test.pdf", null);

		verify(geminiClassifier, times(1)).classify(anyString());

		PageResult byLlm = result.pages().get(1);
		assertThat(byLlm.label()).isEqualTo(DocumentType.INCOME_DOC);
		assertThat(byLlm.decidedBy()).isEqualTo(DecidedBy.LLM);
		assertThat(byLlm.evidence()).isEqualTo("급여 항목이 나열되어 있다");
	}

	@Test
	@DisplayName("LLM도 판정하지 못하면 미판정으로 남기고 사유를 함께 돌려준다")
	void keepsPageUndecidedWithReasonWhenLlmFails() throws Exception {
		givenPages(textPage(1, "어느 키워드에도 걸리지 않는 본문"));
		when(geminiClassifier.classify(anyString())).thenReturn(LlmDecision.undecided("API 키 없음"));

		ClassificationResult result = service.classify(PDF_PATH, "test.pdf", null);

		PageResult undecided = result.pages().get(0);
		assertThat(undecided.label()).isEqualTo(DocumentType.UNDECIDED);
		assertThat(undecided.decidedBy()).isEqualTo(DecidedBy.NONE);
		// 모델이 못 고른 것인지 호출이 실패한 것인지 구분할 수 있어야 한다
		assertThat(undecided.evidence()).isEqualTo("API 키 없음");
	}

	@Test
	@DisplayName("OCR까지 했는데 글자가 없으면 LLM을 부르지 않고 미판정으로 둔다")
	void skipsLlmWhenNoTextAtAll() throws Exception {
		givenPages(scannedPage(1));
		when(ocrFallback.readText(PDF_PATH, 1)).thenReturn("");

		ClassificationResult result = service.classify(PDF_PATH, "test.pdf", null);

		// 빈 텍스트를 보내 봐야 답이 나올 수 없다. 호출 한 번이 그대로 비용이다
		verify(geminiClassifier, never()).classify(anyString());
		assertThat(result.pages().get(0).label()).isEqualTo(DocumentType.UNDECIDED);
		assertThat(result.pages().get(0).decidedBy()).isEqualTo(DecidedBy.NONE);
	}

	@Test
	@DisplayName("텍스트도 이미지도 없는 빈 페이지에는 OCR을 돌리지 않는다")
	void skipsOcrForEmptyPage() throws Exception {
		givenPages(textPage(1, ""));

		ClassificationResult result = service.classify(PDF_PATH, "test.pdf", null);

		// 읽을 이미지가 없으면 렌더링 시간만 쓰고 결과는 빈 문자열로 같다
		verify(ocrFallback, never()).readText(any(), anyInt());
		assertThat(result.pages().get(0).ocrUsed()).isFalse();
		assertThat(result.pages().get(0).label()).isEqualTo(DocumentType.UNDECIDED);
	}

	@Test
	@DisplayName("정답지를 지정하지 않으면 정확도를 계산하지 않는다")
	void skipsAccuracyWhenGroundTruthNotGiven() throws Exception {
		givenPages(textPage(1, "Freddie Mac Form 65"));

		ClassificationResult result = service.classify(PDF_PATH, "test.pdf", null);

		assertThat(result.accuracy()).isNull();
	}

	@Test
	@DisplayName("정답지를 지정하면 이번 판정을 채점해 정확도를 함께 돌려준다")
	void measuresAccuracyAgainstGroundTruth() throws Exception {
		// 커밋된 package_01 정답지의 6페이지는 URLA_1003이다. 그 한 장만 맞히게 해 둔다
		givenPages(textPage(6, "Freddie Mac Form 65"));

		ClassificationResult result = service.classify(PDF_PATH, "test.pdf", "package_01");

		assertThat(result.accuracy()).isNotNull();
		// 정답지 39장 중 올린 것은 1장이므로 나머지는 미판정으로 잡힌다
		assertThat(result.accuracy().totalPages()).isEqualTo(39);
		assertThat(result.accuracy().correctPages()).isEqualTo(1);
		assertThat(result.accuracy().undecidedPages()).isEqualTo(38);
	}

	@Test
	@DisplayName("정답지가 없는 이름을 지정하면 거부한다")
	void rejectsUnknownGroundTruth() throws Exception {
		givenPages(textPage(1, "Freddie Mac Form 65"));

		assertThatThrownBy(() -> service.classify(PDF_PATH, "test.pdf", "package_99"))
				.isInstanceOf(IllegalArgumentException.class);
		// 이름을 그대로 경로에 넣으므로 바깥에서 다른 파일을 지목하지 못하게 막는다
		assertThatThrownBy(() -> service.classify(PDF_PATH, "test.pdf", "../application"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("페이지에 인쇄된 순번 표기를 결과에 함께 담는다")
	void includesPageMarkerInResult() throws Exception {
		givenPages(
				textPage(1, "Uniform Residential Loan Application Page 3 of 11"),
				textPage(2, "Preliminary Report"));

		ClassificationResult result = service.classify(PDF_PATH, "test.pdf", null);

		assertThat(result.pages().get(0).marker()).isEqualTo("3 of 11");
		assertThat(result.pages().get(1).marker()).isNull();
	}

	@Test
	@DisplayName("그룹핑 결과를 시작·끝 페이지와 함께 응답에 옮긴다")
	void copiesGroupingResultIntoResponse() throws Exception {
		givenPages(textPage(1, "Freddie Mac Form 65"), textPage(2, "Freddie Mac Form 65"));
		when(documentGrouper.group(any())).thenReturn(new GroupingResult(
				List.of(new DocumentGroup(DocumentType.URLA_1003, 11, List.of(2, 1))),
				List.of(7)));

		ClassificationResult result = service.classify(PDF_PATH, "shuffled.pdf", null);

		assertThat(result.fileName()).isEqualTo("shuffled.pdf");
		assertThat(result.totalPages()).isEqualTo(2);
		assertThat(result.ungroupedPages()).containsExactly(7);

		DocumentResult document = result.documents().get(0);
		assertThat(document.label()).isEqualTo(DocumentType.URLA_1003);
		assertThat(document.totalPages()).isEqualTo(11);
		assertThat(document.pages()).containsExactly(2, 1);
		// 셔플된 상태에서는 번호가 작은 쪽이 아니라 순번 1번인 페이지가 시작이다
		assertThat(document.startPage()).isEqualTo(2);
		assertThat(document.endPage()).isEqualTo(1);
	}
}
