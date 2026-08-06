package com.codingtest.docclassifier.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.codingtest.docclassifier.accuracy.AccuracyEvaluator;
import com.codingtest.docclassifier.accuracy.AccuracyReport;
import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.classify.RuleClassifier;
import com.codingtest.docclassifier.classify.RuleDecision;
import com.codingtest.docclassifier.groundtruth.GroundTruth;
import com.codingtest.docclassifier.group.ClassifiedPage;
import com.codingtest.docclassifier.group.DocumentGroup;
import com.codingtest.docclassifier.group.DocumentGrouper;
import com.codingtest.docclassifier.group.GroupingResult;
import com.codingtest.docclassifier.group.PageMarker;
import com.codingtest.docclassifier.group.PageMarkerParser;
import com.codingtest.docclassifier.llm.GeminiClassifier;
import com.codingtest.docclassifier.llm.LlmDecision;
import com.codingtest.docclassifier.ocr.OcrFallback;
import com.codingtest.docclassifier.pdf.PdfPage;
import com.codingtest.docclassifier.pdf.PdfPageReader;
import com.codingtest.docclassifier.pipeline.ClassificationResult.DocumentResult;
import com.codingtest.docclassifier.pipeline.ClassificationResult.PageResult;

import tools.jackson.databind.json.JsonMapper;

/**
 * 지금까지 만든 단계들을 하나로 이어 붙여 PDF 한 개를 끝까지 처리한다.
 *
 * <p>순서는 <b>텍스트 추출 → (필요하면) OCR → 규칙 분류 → (보류면) LLM 분류 → 문서 그룹핑</b> 이다.
 * 뒤 단계일수록 느리고 비싸므로, 앞 단계가 답을 낸 페이지는 뒤 단계로 넘기지 않는다.
 * OCR은 텍스트가 없고 이미지가 있는 페이지에만, LLM은 규칙이 보류한 페이지에만 돌린다.
 */
@Service
public class ClassificationService {

	private final PdfPageReader pageReader;
	private final OcrFallback ocrFallback;
	private final RuleClassifier ruleClassifier;
	private final GeminiClassifier geminiClassifier;
	private final PageMarkerParser markerParser;
	private final DocumentGrouper documentGrouper;
	private final AccuracyEvaluator accuracyEvaluator;

	/** 정답지를 읽을 때 쓴다 */
	private final JsonMapper json = JsonMapper.builder().build();

	/**
	 * @param pageReader        PDF에서 페이지별 텍스트를 뽑는다
	 * @param ocrFallback       텍스트 레이어가 없는 페이지를 이미지로 읽는다
	 * @param ruleClassifier    키워드로 1차 판정한다
	 * @param geminiClassifier  규칙이 보류한 페이지를 2차 판정한다
	 * @param markerParser      페이지에 인쇄된 순번 표기("3 of 11")를 읽는다
	 * @param documentGrouper   페이지를 원래 문서 단위로 다시 묶는다
	 * @param accuracyEvaluator 정답지를 지정한 업로드에서 판정 결과를 채점한다
	 */
	public ClassificationService(
			PdfPageReader pageReader,
			OcrFallback ocrFallback,
			RuleClassifier ruleClassifier,
			GeminiClassifier geminiClassifier,
			PageMarkerParser markerParser,
			DocumentGrouper documentGrouper,
			AccuracyEvaluator accuracyEvaluator) {
		this.pageReader = pageReader;
		this.ocrFallback = ocrFallback;
		this.ruleClassifier = ruleClassifier;
		this.geminiClassifier = geminiClassifier;
		this.markerParser = markerParser;
		this.documentGrouper = documentGrouper;
		this.accuracyEvaluator = accuracyEvaluator;
	}

	/**
	 * PDF 한 개를 페이지 분류부터 문서 그룹핑까지 처리한다.
	 *
	 * <p>정답지가 있는 패키지를 올렸다면 {@code groundTruthId}로 그 정답지를 지정할 수 있다.
	 * 그러면 방금 낸 판정을 정답지와 대조한 정확도를 결과에 함께 담는다.
	 * 저장된 수치를 읽어 오는 것이 아니라 이번 실행으로 채점한 값이다.
	 *
	 * @param pdfPath       처리할 PDF 경로
	 * @param fileName      결과에 적어 둘 파일 이름
	 * @param groundTruthId 대조할 정답지 이름 (예: package_01). 채점하지 않으려면 null
	 * @return 페이지별 판정과 문서 그룹핑, 정답지를 지정했다면 정확도까지 담긴 결과
	 * @throws IOException              PDF를 열지 못하거나 읽는 중 실패한 경우
	 * @throws IllegalArgumentException 지정한 정답지가 없거나 이름이 올바르지 않은 경우
	 */
	public ClassificationResult classify(Path pdfPath, String fileName, String groundTruthId) throws IOException {
		List<PageResult> pageResults = new ArrayList<>();
		List<ClassifiedPage> pagesForGrouping = new ArrayList<>();

		for (PdfPage page : pageReader.read(pdfPath)) {
			// 텍스트가 비어 있으면서 이미지가 있는 페이지가 스캔 페이지다. 그때만 OCR을 돌린다.
			// 텍스트도 이미지도 없는 빈 페이지에 OCR을 걸면 렌더링 시간만 쓰고 결과는 그대로 빈 문자열이다
			boolean ocrUsed = page.text().isBlank() && page.imageCount() > 0;
			String text = ocrUsed ? ocrFallback.readText(pdfPath, page.pageNumber()) : page.text();

			PageMarker marker = markerParser.parse(text);
			PageResult result = decideLabel(page.pageNumber(), text, ocrUsed, marker);

			pageResults.add(result);
			pagesForGrouping.add(new ClassifiedPage(page.pageNumber(), result.label(), marker, text));
		}

		GroupingResult grouping = documentGrouper.group(pagesForGrouping);
		return new ClassificationResult(
				fileName,
				pageResults.size(),
				pageResults,
				toDocumentResults(grouping.documents()),
				grouping.ungroupedPages(),
				groundTruthId == null ? null : measureAccuracy(groundTruthId, pageResults));
	}

	/**
	 * 방금 낸 판정을 정답지와 대조해 정확도를 낸다.
	 *
	 * @param groundTruthId 정답지 이름
	 * @param pageResults   이번 실행의 페이지별 판정
	 * @return 전체 정확도·라벨별 지표·혼동행렬
	 * @throws IOException 정답지를 읽지 못한 경우
	 */
	private AccuracyReport measureAccuracy(String groundTruthId, List<PageResult> pageResults) throws IOException {
		Map<Integer, DocumentType> predictions = new LinkedHashMap<>();
		for (PageResult page : pageResults) {
			predictions.put(page.pageNumber(), page.label());
		}
		return accuracyEvaluator.evaluate(loadGroundTruth(groundTruthId), predictions);
	}

	/**
	 * 저장소에 커밋된 정답지를 읽는다.
	 *
	 * <p>이름을 그대로 경로에 넣으므로 영문·숫자·밑줄·붙임표만 허용한다.
	 * 바깥에서 들어온 값이라 이 검사가 없으면 클래스패스의 다른 파일을 지목할 수 있다.
	 *
	 * @param packageId 정답지 이름 (예: package_01)
	 * @return 정답지
	 * @throws IOException              정답지를 읽지 못한 경우
	 * @throws IllegalArgumentException 이름에 쓸 수 없는 문자가 있거나 그런 정답지가 없는 경우
	 */
	private GroundTruth loadGroundTruth(String packageId) throws IOException {
		if (!packageId.matches("[A-Za-z0-9_-]+")) {
			throw new IllegalArgumentException("정답지 이름에 쓸 수 없는 문자가 있습니다: " + packageId);
		}

		try (InputStream stored = getClass().getResourceAsStream("/ground-truth/" + packageId + ".json")) {
			if (stored == null) {
				throw new IllegalArgumentException("정답지가 없는 패키지입니다: " + packageId);
			}
			return json.readValue(stored, GroundTruth.class);
		}
	}

	/**
	 * 페이지 한 장의 라벨을 정한다. 규칙이 판정하면 그대로 쓰고, 보류한 것만 LLM에 넘긴다.
	 *
	 * <p>규칙이 판정한 페이지를 LLM에 다시 물어보지 않는 이유는, 비용과 시간만 늘고
	 * 확실히 맞던 답이 흔들릴 수 있기 때문이다.
	 *
	 * @param pageNumber 페이지 번호
	 * @param text       판정에 쓸 텍스트 (스캔 페이지면 OCR 결과)
	 * @param ocrUsed    이 텍스트가 OCR로 얻은 것인지 여부
	 * @param marker     페이지에 인쇄된 순번 표기. 없으면 null
	 * @return 라벨과 판정 경로·근거가 담긴 결과
	 */
	private PageResult decideLabel(int pageNumber, String text, boolean ocrUsed, PageMarker marker) {
		String markerText = (marker == null) ? null : marker.text();

		// 글자가 하나도 없으면 규칙도 LLM도 판단할 근거가 없다. 빈 텍스트로 API를 부르는 것은 낭비다
		if (text.isBlank()) {
			return new PageResult(pageNumber, DocumentType.UNDECIDED, DecidedBy.NONE, ocrUsed,
					"페이지에서 글자를 찾지 못했습니다", markerText);
		}

		RuleDecision rule = ruleClassifier.classify(text);
		if (rule.label() != DocumentType.UNDECIDED) {
			return new PageResult(pageNumber, rule.label(), DecidedBy.RULE, ocrUsed, rule.evidence(), markerText);
		}

		LlmDecision llm = geminiClassifier.classify(text);
		// LLM도 판정하지 못하면 reason에 그 사유("API 키 없음" 등)가 들어 있다
		DecidedBy decidedBy = (llm.label() == DocumentType.UNDECIDED) ? DecidedBy.NONE : DecidedBy.LLM;
		return new PageResult(pageNumber, llm.label(), decidedBy, ocrUsed, llm.reason(), markerText);
	}

	/**
	 * 그룹핑 결과를 응답용 형태로 바꾼다. 이때 시작·끝 페이지를 값으로 계산해 넣는다.
	 *
	 * @param documents 그룹핑이 만들어 낸 문서들
	 * @return 응답에 담을 문서 목록
	 */
	private List<DocumentResult> toDocumentResults(List<DocumentGroup> documents) {
		List<DocumentResult> results = new ArrayList<>();
		for (DocumentGroup document : documents) {
			results.add(new DocumentResult(
					document.label(),
					document.totalPages(),
					document.startPage(),
					document.endPage(),
					document.pages()));
		}
		return results;
	}
}
