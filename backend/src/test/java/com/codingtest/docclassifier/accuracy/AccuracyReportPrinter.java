package com.codingtest.docclassifier.accuracy;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.codingtest.docclassifier.accuracy.AccuracyReport.LabelMetrics;
import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.groundtruth.GroundTruth;
import com.codingtest.docclassifier.groundtruth.GroundTruth.GroundTruthPage;

/**
 * 정확도 측정 결과를 사람이 읽을 수 있는 표로 찍는다.
 *
 * <p>측정 테스트가 규칙만 돌린 것과 규칙+LLM을 돌린 것 두 벌이라, 같은 표를 두 번 쓰지 않으려고 뺐다.
 * 여기서 찍은 표를 README의 정확도 항목에 그대로 옮긴다.
 */
final class AccuracyReportPrinter {

	/** 한글이 깨지지 않게 UTF-8로 출력한다 */
	private static final PrintStream OUT = new PrintStream(System.out, true, StandardCharsets.UTF_8);

	private AccuracyReportPrinter() {
	}

	/**
	 * 측정 결과 한 벌을 출력한다.
	 *
	 * @param title       표 제목. 어떤 조건으로 잰 수치인지 적는다
	 * @param report      측정 결과
	 * @param groundTruth 정답지
	 * @param predictions 페이지별 판정 라벨
	 * @param evidences   페이지별 판정 근거. 보류·오분류 페이지를 훑을 때 쓴다
	 */
	static void print(String title, AccuracyReport report, GroundTruth groundTruth,
			Map<Integer, DocumentType> predictions, Map<Integer, String> evidences) {

		OUT.printf("%n=== %s ===%n", title);
		OUT.printf("정확도 %d/%d = %.1f%%   보류 %d장%n",
				report.correctPages(), report.totalPages(), report.accuracy() * 100, report.undecidedPages());

		OUT.printf("%n%-15s %8s %10s %8s %10s %8s %8s%n",
				"라벨", "정답수", "판정수", "맞은수", "정밀도", "재현율", "F1");
		for (LabelMetrics metrics : report.labelMetrics()) {
			OUT.printf("%-15s %8d %10d %8d %10.3f %8.3f %8.3f%n",
					metrics.label(), metrics.support(), metrics.predicted(), metrics.correct(),
					metrics.precision(), metrics.recall(), metrics.f1());
		}

		OUT.printf("%n혼동행렬 (행=정답, 열=판정)%n");
		for (Map.Entry<DocumentType, Map<DocumentType, Integer>> row : report.confusionMatrix().entrySet()) {
			OUT.printf("  %-15s %s%n", row.getKey(), row.getValue());
		}

		// 오분류와 미판정을 나눠 찍는다. 둘은 성격이 다르다.
		// 오분류는 분류기가 틀린 것이고, 미판정은 아예 답을 못 낸 것이라 원인부터 다르다
		OUT.printf("%n오분류 페이지 (틀린 라벨을 붙인 페이지)%n");
		for (GroundTruthPage answer : groundTruth.pages()) {
			DocumentType predicted = predictions.get(answer.page());
			if (predicted != answer.label() && predicted != DocumentType.UNDECIDED) {
				OUT.printf("  p%-3d 정답 %-14s 판정 %-14s 근거 %s%n",
						answer.page(), answer.label(), predicted, evidences.get(answer.page()));
			}
		}

		OUT.printf("%n미판정 페이지 (답을 내지 못한 페이지와 그 사유)%n");
		for (GroundTruthPage answer : groundTruth.pages()) {
			if (predictions.get(answer.page()) == DocumentType.UNDECIDED) {
				OUT.printf("  p%-3d 정답 %-14s 사유 %s%n",
						answer.page(), answer.label(), evidences.get(answer.page()));
			}
		}
	}
}
