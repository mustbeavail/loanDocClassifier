package com.codingtest.docclassifier.classify;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.codingtest.docclassifier.pdf.PdfPage;

/**
 * {@link RuleClassifier}의 판정 규칙을 확인한다.
 *
 * <p>실제 PDF 없이 텍스트만 만들어 넣는다. 규칙 자체가 맞게 동작하는지를 보는 테스트이고,
 * 실제 자료에서 몇 %를 맞히는지는 {@code RuleClassificationAccuracyTest}에서 잰다.
 */
class RuleClassifierTest {

	private final RuleClassifier classifier = new RuleClassifier();

	/**
	 * 텍스트만 담은 페이지를 만든다.
	 *
	 * @param text 페이지 텍스트
	 * @return 판정에 넣을 페이지
	 */
	private PdfPage pageWith(String text) {
		return new PdfPage(1, text, 0, 612, 792);
	}

	@Test
	@DisplayName("유형별 고유 키워드가 있으면 해당 라벨로 판정한다")
	void classifiesByKeyword() {
		assertThat(classifier.classify(pageWith("... Freddie Mac Form 65 ...")).label())
				.isEqualTo(DocumentType.URLA_1003);
		assertThat(classifier.classify(pageWith("Repositories: TUC/EXP/EQX")).label())
				.isEqualTo(DocumentType.CREDIT_REPORT);
		assertThat(classifier.classify(pageWith("ALTA Commitment for Title Insurance")).label())
				.isEqualTo(DocumentType.TITLE_REPORT);
		assertThat(classifier.classify(pageWith("Wage and Income Transcript")).label())
				.isEqualTo(DocumentType.INCOME_DOC);
	}

	@Test
	@DisplayName("권원보고서는 양식이 달라도(Preliminary Report / ALTA Commitment) 같은 라벨로 판정한다")
	void classifiesBothTitleReportForms() {
		assertThat(classifier.classify(pageWith("PRELIMINARY REPORT Prelim Number: 1500")).label())
				.isEqualTo(DocumentType.TITLE_REPORT);
		assertThat(classifier.classify(pageWith("2021 ALTA Commitment for Title Insurance")).label())
				.isEqualTo(DocumentType.TITLE_REPORT);
	}

	@Test
	@DisplayName("판정 근거로 어떤 키워드가 맞았는지 함께 돌려준다")
	void reportsMatchedKeyword() {
		RuleDecision decision = classifier.classify(pageWith("Page 3 - Fannie Mae Form 1003 - Effective"));

		assertThat(decision.label()).isEqualTo(DocumentType.URLA_1003);
		assertThat(decision.evidence()).isEqualTo("fannie mae form 1003");
	}

	@Test
	@DisplayName("대소문자가 달라도 같은 키워드로 본다")
	void ignoresLetterCase() {
		assertThat(classifier.classify(pageWith("COMMITMENT CONDITIONS")).label())
				.isEqualTo(DocumentType.TITLE_REPORT);
	}

	@Test
	@DisplayName("키워드 중간에 줄바꿈이 끼어도 판정한다")
	void ignoresLineBreakInsideKeyword() {
		assertThat(classifier.classify(pageWith("Uniform Residential\nLoan Application")).label())
				.isEqualTo(DocumentType.URLA_1003);
	}

	@Test
	@DisplayName("키워드가 하나도 없으면 판정을 보류한다")
	void returnsUndecidedWhenNoKeyword() {
		RuleDecision decision = classifier.classify(pageWith("Page 2 of 5"));

		assertThat(decision.label()).isEqualTo(DocumentType.UNDECIDED);
		assertThat(decision.evidence()).isEmpty();
	}

	@Test
	@DisplayName("서로 다른 두 유형의 키워드가 함께 있으면 판정을 보류한다")
	void returnsUndecidedWhenTwoTypesMatch() {
		String mixed = "Freddie Mac Form 65 ... Repositories: TUC/EXP/EQX";

		assertThat(classifier.classify(pageWith(mixed)).label()).isEqualTo(DocumentType.UNDECIDED);
	}

	@Test
	@DisplayName("텍스트가 비어 있으면 판정을 보류한다")
	void returnsUndecidedForEmptyText() {
		assertThat(classifier.classify(pageWith("")).label()).isEqualTo(DocumentType.UNDECIDED);
	}
}
