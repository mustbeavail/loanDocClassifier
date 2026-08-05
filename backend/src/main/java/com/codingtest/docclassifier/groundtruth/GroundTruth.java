package com.codingtest.docclassifier.groundtruth;

import java.util.List;

import com.codingtest.docclassifier.classify.DocumentType;

/**
 * 셔플된 PDF 한 개에 대한 정답지.
 *
 * <p>분류기 성능을 재려면 "몇 페이지가 어떤 문서였는가"라는 정답이 필요하다.
 * 이 정답지는 사람이 손으로 라벨링한 것이 아니라 원본 문서와 대조해 자동으로 만든 것이라
 * 언제든 같은 결과로 다시 만들 수 있다.
 *
 * @param packageId      대상 패키지 이름 (예: package_01)
 * @param totalPages     셔플 PDF의 전체 페이지 수
 * @param pages          원본과 매칭된 페이지들의 정답
 * @param unmatchedPages 원본에서 짝을 찾지 못한 페이지 번호. 정상이라면 비어 있어야 한다
 */
public record GroundTruth(
		String packageId,
		int totalPages,
		List<GroundTruthPage> pages,
		List<Integer> unmatchedPages) {

	/**
	 * 셔플 PDF 한 페이지의 정답.
	 *
	 * @param page       셔플 PDF에서의 페이지 번호
	 * @param label      이 페이지가 속한 문서 유형
	 * @param sourcePage 원본 문서에서의 페이지 번호. 문서 그룹핑 결과를 검증할 때 쓴다
	 */
	public record GroundTruthPage(int page, DocumentType label, int sourcePage) {
	}
}
