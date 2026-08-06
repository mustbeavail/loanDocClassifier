package com.codingtest.docclassifier.group;

/**
 * 페이지 안에 인쇄된 순번 표기. "3 of 10", "Page 5 of 9", "Page 4" 같은 문자열에서 뽑아낸다.
 *
 * <p>셔플된 PDF에서 원래 문서를 되살리는 단서다.
 * 페이지 순서는 무작위로 섞여 있지만 종이에 인쇄된 순번은 그대로 남아 있기 때문이다.
 *
 * <p>총장수를 함께 찍지 않는 양식이 있다. package_01의 권원보고서(CLTA)가 "Page 4"까지만 적는다.
 * 그때는 {@code total}이 0이 되고, 문서가 완결됐는지는 판단할 수 없어 순서를 세우는 데만 쓴다.
 *
 * @param position 이 페이지가 문서에서 몇 번째인지
 * @param total    그 문서가 몇 장짜리인지. 총장수가 함께 인쇄되지 않은 양식이면 0
 */
public record PageMarker(int position, int total) {

	/**
	 * 사람이 읽는 형태로 만든다. 화면과 LLM 프롬프트에 같은 문자열을 쓴다.
	 *
	 * @return "3 of 11". 총장수를 모르면 "Page 3"
	 */
	public String text() {
		return total == 0 ? "Page " + position : position + " of " + total;
	}
}
