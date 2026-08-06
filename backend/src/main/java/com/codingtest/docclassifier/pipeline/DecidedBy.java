package com.codingtest.docclassifier.pipeline;

/**
 * 페이지 라벨을 누가 정했는지 나타낸다.
 *
 * <p>결과 화면에 이 값을 함께 보여 준다. 규칙과 LLM이 각각 몇 장을 맡았는지 보이지 않으면
 * "정확도 100%"라는 숫자만 남고, 그 숫자가 키워드 규칙 덕분인지 모델 덕분인지 알 수 없기 때문이다.
 */
public enum DecidedBy {

	/** 키워드 규칙이 판정했다 */
	RULE,

	/** 규칙이 보류해서 LLM이 판정했다 */
	LLM,

	/** 둘 다 판정하지 못했다. 이유는 근거 문자열에 남는다 */
	NONE
}
