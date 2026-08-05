package com.codingtest.docclassifier.group;

import com.codingtest.docclassifier.classify.DocumentType;

/**
 * 분류가 끝난 페이지 하나. 그룹핑의 입력이다.
 *
 * <p>텍스트를 함께 들고 다니는 이유는, 순번 표기만으로 문서를 가를 수 없을 때
 * 페이지 내용을 보고 판단해야 하기 때문이다.
 *
 * @param pageNumber 셔플된 PDF에서의 페이지 번호 (1부터 셈)
 * @param label      분류 결과 라벨
 * @param marker     페이지에 인쇄된 순번 표기. 표기가 없는 페이지는 null
 * @param text       페이지 텍스트
 */
public record ClassifiedPage(int pageNumber, DocumentType label, PageMarker marker, String text) {
}
