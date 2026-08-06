package com.codingtest.docclassifier.pipeline;

import java.util.List;

import com.codingtest.docclassifier.accuracy.AccuracyReport;
import com.codingtest.docclassifier.classify.DocumentType;

/**
 * PDF 한 개를 처리한 최종 결과. {@code POST /api/classify} 의 응답 본문이 된다.
 *
 * <p>페이지별 판정과 문서 그룹핑을 한 응답에 함께 담는다.
 * 과제가 요구하는 것이 "페이지 분류"와 "문서의 시작·끝 페이지 식별" 두 가지이고,
 * 둘은 같은 PDF를 한 번 훑어 나오는 결과라 나눠 호출할 이유가 없다.
 *
 * @param fileName       업로드된 파일 이름
 * @param totalPages     PDF의 전체 페이지 수
 * @param pages          페이지별 판정 결과 (PDF 페이지 번호 순)
 * @param documents      다시 묶어 낸 문서들
 * @param ungroupedPages 어느 문서에도 넣지 못한 페이지 번호들
 * @param accuracy       정답지를 지정하고 올렸을 때만 채워지는 정확도. 지정하지 않았으면 null.
 *                       저장된 수치가 아니라 <b>이번 업로드로 방금 측정한 값</b>이다
 */
public record ClassificationResult(
		String fileName,
		int totalPages,
		List<PageResult> pages,
		List<DocumentResult> documents,
		List<Integer> ungroupedPages,
		AccuracyReport accuracy) {

	/**
	 * 페이지 한 장의 판정 결과.
	 *
	 * <p>라벨만 주지 않고 판정 경로({@code decidedBy}, {@code ocrUsed})와 근거를 함께 주는 이유는,
	 * 결과를 보는 사람이 각 페이지가 <b>왜</b> 그 라벨을 받았는지 확인할 수 있어야 하기 때문이다.
	 *
	 * @param pageNumber PDF에서의 페이지 번호 (1부터 셈)
	 * @param label      판정한 문서 유형. 판정하지 못했으면 {@link DocumentType#UNDECIDED}
	 * @param decidedBy  이 라벨을 누가 정했는지
	 * @param ocrUsed    텍스트 레이어가 없어 OCR로 글자를 읽었는지 여부
	 * @param evidence   판정 근거. 규칙이면 매칭된 키워드, LLM이면 근거 문장,
	 *                   판정하지 못했으면 그 사유(예: "API 키 없음")
	 * @param marker     페이지에 인쇄된 순번 표기 (예: "3 of 11"). 표기가 없으면 null
	 */
	public record PageResult(
			int pageNumber,
			DocumentType label,
			DecidedBy decidedBy,
			boolean ocrUsed,
			String evidence,
			String marker) {
	}

	/**
	 * 다시 묶어 낸 문서 하나.
	 *
	 * <p>{@code startPage}와 {@code endPage}를 값으로 담아 두는 이유는 과제가 요구하는 항목이기 때문이다.
	 * 페이지가 섞여 있어 번호가 작은 쪽이 시작이 아니므로, 받는 쪽에서 계산하게 두면 틀리기 쉽다.
	 *
	 * @param label      문서 유형
	 * @param totalPages 순번 표기가 알려 주는 전체 장수. 표기가 없어 알 수 없으면 0
	 * @param startPage  문서의 첫 장이 PDF의 몇 페이지인지
	 * @param endPage    문서의 마지막 장이 PDF의 몇 페이지인지
	 * @param pages      이 문서에 속한 PDF 페이지 번호들 (문서 안에서의 장 순서)
	 */
	public record DocumentResult(
			DocumentType label,
			int totalPages,
			int startPage,
			int endPage,
			List<Integer> pages) {
	}
}
