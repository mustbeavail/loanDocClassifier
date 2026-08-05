package com.codingtest.docclassifier.accuracy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.codingtest.docclassifier.accuracy.AccuracyReport.LabelMetrics;
import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.groundtruth.GroundTruth;
import com.codingtest.docclassifier.groundtruth.GroundTruth.GroundTruthPage;

/**
 * {@link AccuracyEvaluator}의 계산이 맞는지 확인한다.
 *
 * <p>손으로 세어 답을 알 수 있는 작은 정답지를 만들어 넣는다.
 * 실제 자료로 측정한 수치가 이상해 보일 때, 분류기가 문제인지 계산이 문제인지 가르는 기준이 된다.
 */
class AccuracyEvaluatorTest {

	private final AccuracyEvaluator evaluator = new AccuracyEvaluator();

	/**
	 * 5페이지짜리 정답지를 만든다. URLA 3장, 신용보고서 2장.
	 *
	 * @return 시험용 정답지
	 */
	private GroundTruth sampleGroundTruth() {
		return new GroundTruth("sample", 5, List.of(
				new GroundTruthPage(1, DocumentType.URLA_1003, 1),
				new GroundTruthPage(2, DocumentType.URLA_1003, 2),
				new GroundTruthPage(3, DocumentType.URLA_1003, 3),
				new GroundTruthPage(4, DocumentType.CREDIT_REPORT, 1),
				new GroundTruthPage(5, DocumentType.CREDIT_REPORT, 2)),
				List.of());
	}

	/**
	 * 라벨별 지표에서 한 라벨을 꺼낸다.
	 *
	 * @param report 측정 결과
	 * @param label  찾을 라벨
	 * @return 해당 라벨의 지표
	 */
	private LabelMetrics metricsOf(AccuracyReport report, DocumentType label) {
		return report.labelMetrics().stream()
				.filter(metrics -> metrics.label() == label)
				.findFirst()
				.orElseThrow();
	}

	@Test
	@DisplayName("전부 맞히면 정확도 100%가 나온다")
	void countsPerfectResult() {
		Map<Integer, DocumentType> predictions = new HashMap<>();
		predictions.put(1, DocumentType.URLA_1003);
		predictions.put(2, DocumentType.URLA_1003);
		predictions.put(3, DocumentType.URLA_1003);
		predictions.put(4, DocumentType.CREDIT_REPORT);
		predictions.put(5, DocumentType.CREDIT_REPORT);

		AccuracyReport report = evaluator.evaluate(sampleGroundTruth(), predictions);

		assertThat(report.correctPages()).isEqualTo(5);
		assertThat(report.accuracy()).isEqualTo(1.0);
		assertThat(report.undecidedPages()).isZero();
	}

	@Test
	@DisplayName("틀린 페이지와 보류한 페이지를 나눠서 센다")
	void countsWrongAndUndecidedSeparately() {
		Map<Integer, DocumentType> predictions = new HashMap<>();
		predictions.put(1, DocumentType.URLA_1003);
		predictions.put(2, DocumentType.CREDIT_REPORT);   // 틀림
		predictions.put(3, DocumentType.UNDECIDED);       // 보류
		predictions.put(4, DocumentType.CREDIT_REPORT);
		predictions.put(5, DocumentType.CREDIT_REPORT);

		AccuracyReport report = evaluator.evaluate(sampleGroundTruth(), predictions);

		assertThat(report.totalPages()).isEqualTo(5);
		assertThat(report.correctPages()).isEqualTo(3);
		assertThat(report.accuracy()).isCloseTo(0.6, within(0.001));
		assertThat(report.undecidedPages()).isEqualTo(1);
	}

	@Test
	@DisplayName("예측이 아예 없는 페이지는 보류로 센다")
	void treatsMissingPredictionAsUndecided() {
		AccuracyReport report = evaluator.evaluate(sampleGroundTruth(), Map.of());

		assertThat(report.undecidedPages()).isEqualTo(5);
		assertThat(report.correctPages()).isZero();
	}

	@Test
	@DisplayName("라벨별 정밀도·재현율·F1을 계산한다")
	void calculatesLabelMetrics() {
		Map<Integer, DocumentType> predictions = new HashMap<>();
		predictions.put(1, DocumentType.URLA_1003);
		predictions.put(2, DocumentType.URLA_1003);
		predictions.put(3, DocumentType.CREDIT_REPORT);   // URLA를 신용보고서로 오분류
		predictions.put(4, DocumentType.CREDIT_REPORT);
		predictions.put(5, DocumentType.CREDIT_REPORT);

		AccuracyReport report = evaluator.evaluate(sampleGroundTruth(), predictions);

		LabelMetrics urla = metricsOf(report, DocumentType.URLA_1003);
		assertThat(urla.support()).isEqualTo(3);
		assertThat(urla.predicted()).isEqualTo(2);
		assertThat(urla.precision()).isEqualTo(1.0);                    // 2장 판정 중 2장 정답
		assertThat(urla.recall()).isCloseTo(0.667, within(0.001));      // 실제 3장 중 2장 발견

		LabelMetrics credit = metricsOf(report, DocumentType.CREDIT_REPORT);
		assertThat(credit.predicted()).isEqualTo(3);
		assertThat(credit.precision()).isCloseTo(0.667, within(0.001)); // 3장 판정 중 2장 정답
		assertThat(credit.recall()).isEqualTo(1.0);
		assertThat(credit.f1()).isCloseTo(0.8, within(0.001));
	}

	@Test
	@DisplayName("정답이 한 장도 없는 라벨은 지표가 0이고 계산이 깨지지 않는다")
	void handlesLabelWithoutAnyPage() {
		AccuracyReport report = evaluator.evaluate(sampleGroundTruth(), Map.of());

		LabelMetrics other = metricsOf(report, DocumentType.OTHER);
		assertThat(other.support()).isZero();
		assertThat(other.precision()).isZero();
		assertThat(other.recall()).isZero();
		assertThat(other.f1()).isZero();
	}

	@Test
	@DisplayName("혼동행렬에 실제 라벨과 예측 라벨의 조합이 기록된다")
	void fillsConfusionMatrix() {
		Map<Integer, DocumentType> predictions = new HashMap<>();
		predictions.put(1, DocumentType.URLA_1003);
		predictions.put(2, DocumentType.CREDIT_REPORT);
		predictions.put(3, DocumentType.UNDECIDED);
		predictions.put(4, DocumentType.CREDIT_REPORT);
		predictions.put(5, DocumentType.CREDIT_REPORT);

		AccuracyReport report = evaluator.evaluate(sampleGroundTruth(), predictions);
		Map<DocumentType, Integer> urlaRow = report.confusionMatrix().get(DocumentType.URLA_1003);

		assertThat(urlaRow.get(DocumentType.URLA_1003)).isEqualTo(1);
		assertThat(urlaRow.get(DocumentType.CREDIT_REPORT)).isEqualTo(1);
		assertThat(urlaRow.get(DocumentType.UNDECIDED)).isEqualTo(1);
	}
}
