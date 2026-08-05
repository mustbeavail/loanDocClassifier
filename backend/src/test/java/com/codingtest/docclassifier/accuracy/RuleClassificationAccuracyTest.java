package com.codingtest.docclassifier.accuracy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.codingtest.docclassifier.TestMaterials;
import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.classify.RuleClassifier;
import com.codingtest.docclassifier.classify.RuleDecision;
import com.codingtest.docclassifier.groundtruth.GroundTruth;
import com.codingtest.docclassifier.pdf.PdfPage;
import com.codingtest.docclassifier.pdf.PdfPageReader;

import tools.jackson.databind.json.JsonMapper;

/**
 * package_01에 규칙 분류기만 돌려 실제 정확도를 잰다. STEP 2의 첫 측정이다.
 *
 * <p>여기서 나온 보류(UNDECIDED) 건수가 다음 단계에서 LLM에 넘길 물량이 된다.
 * 규칙만으로 얼마나 되는지를 따로 남겨 둬야 LLM을 붙여서 얼마나 좋아졌는지 말할 수 있다.
 */
class RuleClassificationAccuracyTest {

	private final PdfPageReader pageReader = new PdfPageReader();
	private final RuleClassifier classifier = new RuleClassifier();
	private final AccuracyEvaluator evaluator = new AccuracyEvaluator();

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
		AccuracyReportPrinter.print("package_01 규칙 분류 결과", report, groundTruth, predictions, evidences);

		// 규칙은 "틀리느니 보류한다"는 원칙으로 만들었다. 판정한 페이지에 오답이 있으면 규칙이 잘못된 것이다
		int wrongPages = report.totalPages() - report.correctPages() - report.undecidedPages();
		assertThat(wrongPages).as("규칙이 판정했는데 틀린 페이지 수").isZero();
	}
}
