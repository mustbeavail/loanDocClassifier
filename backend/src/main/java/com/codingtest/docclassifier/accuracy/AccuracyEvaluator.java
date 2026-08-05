package com.codingtest.docclassifier.accuracy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.codingtest.docclassifier.accuracy.AccuracyReport.LabelMetrics;
import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.groundtruth.GroundTruth;
import com.codingtest.docclassifier.groundtruth.GroundTruth.GroundTruthPage;

/**
 * 분류 결과를 정답지와 대조해 정확도를 계산한다.
 *
 * <p>측정 대상은 정답지가 있는 package_01뿐이다.
 * 분류기를 고칠 때마다 이 결과가 좋아졌는지 나빠졌는지 보고 판단한다.
 */
@Component
public class AccuracyEvaluator {

	/** 지표를 낼 라벨. UNDECIDED는 정답이 될 수 없어 대상이 아니다 */
	private static final List<DocumentType> LABELS = List.of(
			DocumentType.URLA_1003,
			DocumentType.CREDIT_REPORT,
			DocumentType.TITLE_REPORT,
			DocumentType.INCOME_DOC,
			DocumentType.OTHER);

	/**
	 * 정답지와 분류 결과를 대조해 리포트를 만든다.
	 *
	 * @param groundTruth 정답지
	 * @param predictions 페이지 번호 → 분류기가 판정한 라벨
	 * @return 전체 정확도, 라벨별 지표, 혼동행렬이 담긴 리포트
	 */
	public AccuracyReport evaluate(GroundTruth groundTruth, Map<Integer, DocumentType> predictions) {
		Map<DocumentType, Map<DocumentType, Integer>> confusionMatrix = emptyConfusionMatrix();
		int correctPages = 0;
		int undecidedPages = 0;

		for (GroundTruthPage answer : groundTruth.pages()) {
			DocumentType predicted = predictions.getOrDefault(answer.page(), DocumentType.UNDECIDED);

			confusionMatrix.get(answer.label()).merge(predicted, 1, Integer::sum);
			if (predicted == answer.label()) {
				correctPages++;
			}
			if (predicted == DocumentType.UNDECIDED) {
				undecidedPages++;
			}
		}

		int totalPages = groundTruth.pages().size();
		return new AccuracyReport(
				totalPages,
				correctPages,
				divide(correctPages, totalPages),
				undecidedPages,
				labelMetrics(confusionMatrix),
				confusionMatrix);
	}

	/**
	 * 라벨별 정밀도·재현율·F1을 구한다.
	 *
	 * @param confusionMatrix 실제 라벨 → 예측 라벨 → 페이지 수
	 * @return 라벨별 지표 목록
	 */
	private List<LabelMetrics> labelMetrics(Map<DocumentType, Map<DocumentType, Integer>> confusionMatrix) {
		List<LabelMetrics> metrics = new ArrayList<>();

		for (DocumentType label : LABELS) {
			int support = sum(confusionMatrix.get(label).values());
			int correct = confusionMatrix.get(label).getOrDefault(label, 0);

			// 이 라벨로 판정한 총 장수는 혼동행렬의 세로 한 줄을 더한 값이다
			int predicted = 0;
			for (DocumentType actualLabel : LABELS) {
				predicted += confusionMatrix.get(actualLabel).getOrDefault(label, 0);
			}

			double precision = divide(correct, predicted);
			double recall = divide(correct, support);
			double f1 = (precision + recall == 0) ? 0 : 2 * precision * recall / (precision + recall);

			metrics.add(new LabelMetrics(label, support, predicted, correct, precision, recall, f1));
		}
		return metrics;
	}

	/**
	 * 값이 모두 0인 혼동행렬을 만든다. 라벨 순서를 고정해 두면 표로 출력할 때 순서가 흔들리지 않는다.
	 *
	 * @return 실제 라벨 → 예측 라벨 → 0 으로 채워진 표
	 */
	private Map<DocumentType, Map<DocumentType, Integer>> emptyConfusionMatrix() {
		Map<DocumentType, Map<DocumentType, Integer>> matrix = new LinkedHashMap<>();
		for (DocumentType actualLabel : LABELS) {
			Map<DocumentType, Integer> row = new LinkedHashMap<>();
			for (DocumentType predictedLabel : LABELS) {
				row.put(predictedLabel, 0);
			}
			row.put(DocumentType.UNDECIDED, 0);
			matrix.put(actualLabel, row);
		}
		return matrix;
	}

	/**
	 * 0으로 나누는 경우를 0으로 처리하는 나눗셈.
	 *
	 * @param numerator   분자
	 * @param denominator 분모
	 * @return 나눈 값. 분모가 0이면 0
	 */
	private double divide(int numerator, int denominator) {
		if (denominator == 0) {
			return 0;
		}
		return (double) numerator / denominator;
	}

	/**
	 * 정수 모음을 더한다.
	 *
	 * @param values 더할 값들
	 * @return 합계
	 */
	private int sum(Iterable<Integer> values) {
		int total = 0;
		for (int value : values) {
			total += value;
		}
		return total;
	}
}
