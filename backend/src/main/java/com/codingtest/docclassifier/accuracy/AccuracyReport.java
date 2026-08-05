package com.codingtest.docclassifier.accuracy;

import java.util.List;
import java.util.Map;

import com.codingtest.docclassifier.classify.DocumentType;

/**
 * 정확도 측정 결과.
 *
 * <p>전체 정확도 하나만 보고하지 않는 이유는 라벨 분포가 크게 치우쳐 있기 때문이다.
 * package_01은 39장 중 신용보고서가 18장, 소득서류가 1장이라
 * 소득서류를 통째로 틀려도 전체 정확도는 97%로 나온다.
 * 그래서 라벨별 지표와 혼동행렬을 함께 낸다.
 *
 * @param totalPages      측정 대상 페이지 수
 * @param correctPages    정답과 일치한 페이지 수
 * @param accuracy        전체 정확도 (0~1)
 * @param undecidedPages  판정을 보류한 페이지 수. 다음 단계(LLM)로 넘어갈 물량이다
 * @param labelMetrics    라벨별 정밀도·재현율·F1
 * @param confusionMatrix 실제 라벨 → 예측 라벨 → 페이지 수
 */
public record AccuracyReport(
		int totalPages,
		int correctPages,
		double accuracy,
		int undecidedPages,
		List<LabelMetrics> labelMetrics,
		Map<DocumentType, Map<DocumentType, Integer>> confusionMatrix) {

	/**
	 * 라벨 하나에 대한 지표.
	 *
	 * @param label     대상 라벨
	 * @param support   정답지에 이 라벨이 몇 장 있는지
	 * @param predicted 분류기가 이 라벨로 몇 장을 판정했는지
	 * @param correct   그중 실제로 맞은 장수
	 * @param precision 이 라벨로 판정한 것 중 맞은 비율
	 * @param recall    이 라벨의 실제 페이지 중 찾아낸 비율
	 * @param f1        정밀도와 재현율의 조화평균
	 */
	public record LabelMetrics(
			DocumentType label,
			int support,
			int predicted,
			int correct,
			double precision,
			double recall,
			double f1) {
	}
}
