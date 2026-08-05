package com.codingtest.docclassifier.classify;

/**
 * 규칙 분류기가 페이지 하나를 판정한 결과.
 *
 * @param label    판정한 문서 유형. 확정하지 못하면 {@link DocumentType#UNDECIDED}
 * @param evidence 판정 근거가 된 키워드. 판정하지 못했으면 빈 문자열
 */
public record RuleDecision(DocumentType label, String evidence) {

	/** 판정하지 못한 결과를 만들 때 쓴다 */
	public static RuleDecision undecided() {
		return new RuleDecision(DocumentType.UNDECIDED, "");
	}
}
