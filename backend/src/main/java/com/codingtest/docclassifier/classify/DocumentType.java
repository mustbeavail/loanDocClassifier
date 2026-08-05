package com.codingtest.docclassifier.classify;

/**
 * 페이지에 붙일 수 있는 라벨.
 *
 * <p>과제가 정한 문서 유형 5종에 더해, 판정을 보류한 상태를 뜻하는 {@link #UNDECIDED}를 둔다.
 * 규칙 분류기가 확신 없이 아무 라벨이나 찍어 버리면 정확도 수치가 실제보다 좋아 보이므로,
 * "모르겠다"를 별도 값으로 남기고 다음 단계(LLM 판정)로 넘긴다.
 */
public enum DocumentType {

	/** 대출 신청서 (Uniform Residential Loan Application, Form 1003) */
	URLA_1003,

	/** 신용 보고서 */
	CREDIT_REPORT,

	/** 권원 보고서 (Title Commitment) */
	TITLE_REPORT,

	/** 소득 서류 (W-2, 세무 기록, 손익계산서 등) */
	INCOME_DOC,

	/** 위 4종에 속하지 않는 별개 발행물 */
	OTHER,

	/** 아직 판정하지 못한 상태. 라벨이 아니라 "보류" 표시다 */
	UNDECIDED
}
