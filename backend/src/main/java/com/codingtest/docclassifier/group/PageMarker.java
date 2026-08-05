package com.codingtest.docclassifier.group;

/**
 * 페이지 안에 인쇄된 순번 표기. "3 of 10", "Page 5 of 9" 같은 문자열에서 뽑아낸다.
 *
 * <p>셔플된 PDF에서 원래 문서를 되살리는 유일한 단서다.
 * 페이지 순서는 무작위로 섞여 있지만 종이에 인쇄된 순번은 그대로 남아 있기 때문이다.
 *
 * @param position 이 페이지가 문서에서 몇 번째인지 (of 앞의 숫자)
 * @param total    그 문서가 몇 장짜리인지 (of 뒤의 숫자)
 */
public record PageMarker(int position, int total) {
}
