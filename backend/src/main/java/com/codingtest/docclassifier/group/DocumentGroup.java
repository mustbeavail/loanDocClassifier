package com.codingtest.docclassifier.group;

import java.util.List;

import com.codingtest.docclassifier.classify.DocumentType;

/**
 * 다시 묶어 낸 문서 하나.
 *
 * <p>페이지 목록은 <b>문서 안에서의 순서</b>로 담는다. 셔플된 PDF의 페이지 번호 순이 아니다.
 * 순번 표기가 있으면 그 순서, 없으면 셔플 PDF에 나온 순서를 쓴다.
 *
 * @param label      문서 유형
 * @param totalPages 순번 표기가 알려 주는 전체 장수. 표기가 없어 알 수 없으면 0
 * @param pages      이 문서에 속한 셔플 PDF의 페이지 번호들 (문서 안 순서)
 */
public record DocumentGroup(DocumentType label, int totalPages, List<Integer> pages) {

	/**
	 * 문서의 첫 장이 셔플 PDF의 몇 페이지인지 알려 준다.
	 *
	 * <p>과제가 요구하는 "시작 페이지"다. 셔플된 상태에서는 페이지 번호가 작은 쪽이 아니라
	 * 순번 1번이 찍힌 페이지가 문서의 시작이다.
	 *
	 * @return 시작 페이지 번호
	 */
	public int startPage() {
		return pages.get(0);
	}

	/**
	 * 문서의 마지막 장이 셔플 PDF의 몇 페이지인지 알려 준다.
	 *
	 * @return 끝 페이지 번호
	 */
	public int endPage() {
		return pages.get(pages.size() - 1);
	}
}
