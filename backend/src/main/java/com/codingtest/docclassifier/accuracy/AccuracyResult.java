package com.codingtest.docclassifier.accuracy;

import java.util.List;

import com.codingtest.docclassifier.classify.DocumentType;

/**
 * 저장소에 커밋해 두는 정확도 측정 결과. {@code GET /api/accuracy} 의 응답 본문이 된다.
 *
 * <p>정확도를 요청받을 때마다 다시 계산하지 않고 이 파일을 읽어 돌려준다.
 * 다시 계산하려면 제공 자료 PDF와 Gemini API 키가 둘 다 있어야 하는데,
 * 저장소만 받은 사람에게는 둘 다 없어 정확도 화면이 통째로 비어 버리기 때문이다.
 * 자료와 키가 있으면 {@code POST /api/classify} 에 정답지를 지정해 그 자리에서 실측할 수 있다.
 *
 * <p>대신 이 파일은 손으로 쓰지 않는다. {@code RuleAndLlmAccuracyTest}가 실제로 측정하면서 만들어 낸다.
 *
 * @param packageId 측정 대상 패키지 이름 (예: package_01)
 * @param pipeline  이 수치를 낸 처리 경로 (예: "규칙 → LLM"). 같은 자료라도 경로에 따라 수치가 달라진다
 * @param report    전체 정확도·라벨별 지표·혼동행렬
 * @param pages     페이지별 정답과 판정 결과. 어느 페이지를 왜 틀렸는지 확인하는 데 쓴다
 */
public record AccuracyResult(
		String packageId,
		String pipeline,
		AccuracyReport report,
		List<PageComparison> pages) {

	/**
	 * 페이지 한 장의 정답과 판정 결과 대조.
	 *
	 * @param page      셔플 PDF에서의 페이지 번호
	 * @param actual    정답지가 말하는 라벨
	 * @param predicted 분류기가 판정한 라벨
	 * @param evidence  판정 근거 (규칙 키워드 또는 LLM 근거 문장)
	 */
	public record PageComparison(
			int page,
			DocumentType actual,
			DocumentType predicted,
			String evidence) {
	}
}
