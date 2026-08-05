package com.codingtest.docclassifier.accuracy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.codingtest.docclassifier.TestMaterials;
import com.codingtest.docclassifier.accuracy.AccuracyReport.LabelMetrics;
import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.classify.RuleClassifier;
import com.codingtest.docclassifier.classify.RuleDecision;
import com.codingtest.docclassifier.groundtruth.GroundTruth;
import com.codingtest.docclassifier.groundtruth.GroundTruth.GroundTruthPage;
import com.codingtest.docclassifier.pdf.PdfPage;
import com.codingtest.docclassifier.pdf.PdfPageReader;

import tools.jackson.databind.json.JsonMapper;

/**
 * package_01에 규칙 분류기만 돌려 실제 정확도를 잰다. STEP 2의 첫 측정이다.
 *
 * <p>여기서 나온 보류(UNDECIDED) 건수가 다음 단계에서 LLM에 넘길 물량이 된다.
 * 콘솔에 출력한 표를 README 정확도 항목에 그대로 쓴다.
 */
class RuleClassificationAccuracyTest {

	private final PdfPageReader pageReader = new PdfPageReader();
	private final RuleClassifier classifier = new RuleClassifier();
	private final AccuracyEvaluator evaluator = new AccuracyEvaluator();

	/** 한글이 깨지지 않게 UTF-8로 출력한다 */
	private final PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);

	/**
	 * 저장소에 커밋된 package_01 정답지를 읽는다.
	 *
	 * @return 정답지
	 */
	private GroundTruth loadGroundTruth() throws Exception {
		try (InputStream json = getClass().getResourceAsStream("/ground-truth/package_01.json")) {
			return JsonMapper.builder().build().readValue(json, GroundTruth.class);
		}
	}

	@Test
	@DisplayName("package_01에 규칙만 적용한 정확도를 측정하고 표로 출력한다")
	void measuresRuleOnlyAccuracy() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");

		GroundTruth groundTruth = loadGroundTruth();
		Map<Integer, DocumentType> predictions = new LinkedHashMap<>();
		Map<Integer, String> evidences = new LinkedHashMap<>();

		for (PdfPage page : pageReader.read(TestMaterials.package01Shuffled())) {
			RuleDecision decision = classifier.classify(page);
			predictions.put(page.pageNumber(), decision.label());
			evidences.put(page.pageNumber(), decision.evidence());
		}

		AccuracyReport report = evaluator.evaluate(groundTruth, predictions);
		printReport(report, groundTruth, predictions, evidences);

		// 규칙은 "틀리느니 보류한다"는 원칙으로 만들었다. 판정한 페이지에 오답이 있으면 규칙이 잘못된 것이다
		int wrongPages = report.totalPages() - report.correctPages() - report.undecidedPages();
		assertThat(wrongPages).as("규칙이 판정했는데 틀린 페이지 수").isZero();
	}

	/**
	 * 측정 결과를 사람이 읽을 수 있는 표로 출력한다.
	 *
	 * @param report      측정 결과
	 * @param groundTruth 정답지
	 * @param predictions 페이지별 판정 라벨
	 * @param evidences   페이지별 판정 근거 키워드
	 */
	private void printReport(AccuracyReport report, GroundTruth groundTruth,
			Map<Integer, DocumentType> predictions, Map<Integer, String> evidences) {

		out.printf("%n=== package_01 규칙 분류 결과 ===%n");
		out.printf("정확도 %d/%d = %.1f%%   보류 %d장%n",
				report.correctPages(), report.totalPages(), report.accuracy() * 100, report.undecidedPages());

		out.printf("%n%-15s %8s %10s %8s %10s %8s %8s%n",
				"라벨", "정답수", "판정수", "맞은수", "정밀도", "재현율", "F1");
		for (LabelMetrics metrics : report.labelMetrics()) {
			out.printf("%-15s %8d %10d %8d %10.3f %8.3f %8.3f%n",
					metrics.label(), metrics.support(), metrics.predicted(), metrics.correct(),
					metrics.precision(), metrics.recall(), metrics.f1());
		}

		out.printf("%n혼동행렬 (행=정답, 열=판정)%n");
		for (Map.Entry<DocumentType, Map<DocumentType, Integer>> row : report.confusionMatrix().entrySet()) {
			out.printf("  %-15s %s%n", row.getKey(), row.getValue());
		}

		out.printf("%n보류·오분류 페이지%n");
		for (GroundTruthPage answer : groundTruth.pages()) {
			DocumentType predicted = predictions.get(answer.page());
			if (predicted != answer.label()) {
				out.printf("  p%-3d 정답 %-14s 판정 %-14s 근거 %s%n",
						answer.page(), answer.label(), predicted, evidences.get(answer.page()));
			}
		}
	}
}
