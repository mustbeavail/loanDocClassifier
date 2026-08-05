package com.codingtest.docclassifier.llm;

import com.codingtest.docclassifier.classify.DocumentType;

/**
 * LLM이 페이지 하나를 판정한 결과.
 *
 * <p>규칙 분류 결과({@code RuleDecision})와 달리 확신도와 근거 문장을 함께 담는다.
 * 규칙이 못 잡아 넘어온 애매한 페이지들이라, 사람이 결과를 검토할 때
 * "왜 이 라벨인지"를 페이지마다 확인할 수 있어야 하기 때문이다.
 *
 * @param label      판정한 문서 유형. 판정하지 못했으면 {@link DocumentType#UNDECIDED}
 * @param confidence 0~1 사이의 확신도. 판정하지 못했으면 0
 * @param reason     판정 근거 한 문장
 */
public record LlmDecision(DocumentType label, double confidence, String reason) {

	/**
	 * 판정하지 못한 결과를 만든다.
	 *
	 * <p>API 키가 없을 때나 호출이 실패했을 때 쓴다.
	 * 예외를 던지지 않는 이유는 한 페이지 때문에 나머지 페이지 처리를 멈출 이유가 없어서다.
	 *
	 * @param reason 판정하지 못한 이유
	 * @return 미판정 결과
	 */
	public static LlmDecision undecided(String reason) {
		return new LlmDecision(DocumentType.UNDECIDED, 0, reason);
	}
}
