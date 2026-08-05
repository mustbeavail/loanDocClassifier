package com.codingtest.docclassifier.group;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.llm.GeminiGrouper;

/**
 * 문서 그룹핑 테스트.
 *
 * <p>순번 표기만으로 문서가 완성되는 경우와, 표기가 부족해 LLM에 넘어가는 경우를 나눠 확인한다.
 * LLM 자리에는 키가 없는 그룹퍼를 넣는다. 그러면 실제 호출 없이 "묶지 못한 상태"가 되므로
 * <b>어떤 입력이 LLM으로 넘어가는지</b>를 미분류 결과로 확인할 수 있다.
 */
class DocumentGrouperTest {

	/** 키가 없는 그룹퍼. 호출해도 빈 결과를 돌려주므로 LLM 경로가 그대로 드러난다 */
	private final GeminiGrouper offlineLlm = new GeminiGrouper("", "gemini-3.6-flash");

	private final DocumentGrouper grouper = new DocumentGrouper(offlineLlm);

	/**
	 * 테스트용 페이지를 만든다.
	 *
	 * @param pageNumber 셔플 PDF의 페이지 번호
	 * @param label      라벨
	 * @param position   순번. 0이면 표기 없는 페이지로 만든다
	 * @param total      전체 장수
	 * @return 분류된 페이지
	 */
	private ClassifiedPage page(int pageNumber, DocumentType label, int position, int total) {
		PageMarker marker = (position == 0) ? null : new PageMarker(position, total);
		return new ClassifiedPage(pageNumber, label, marker, "페이지 " + pageNumber + " 본문");
	}

	@Test
	@DisplayName("순번 표기가 다 있고 겹치지 않으면 표기 순서대로 한 문서를 만든다")
	void groupsScatteredPagesInOrder() {
		List<ClassifiedPage> pages = List.of(
				page(5, DocumentType.URLA_1003, 3, 3),
				page(1, DocumentType.URLA_1003, 2, 3),
				page(9, DocumentType.URLA_1003, 1, 3));

		GroupingResult result = grouper.group(pages);

		assertThat(result.documents()).hasSize(1);
		DocumentGroup document = result.documents().get(0);
		// 페이지 번호 순(1,5,9)이 아니라 순번 순(9,1,5)으로 담겨야 한다
		assertThat(document.pages()).containsExactly(9, 1, 5);
		assertThat(document.startPage()).isEqualTo(9);
		assertThat(document.endPage()).isEqualTo(5);
		assertThat(result.ungroupedPages()).isEmpty();
	}

	@Test
	@DisplayName("한 장짜리 문서는 물어보지 않고 그대로 문서가 된다")
	void singlePageBecomesDocument() {
		GroupingResult result = grouper.group(List.of(page(36, DocumentType.INCOME_DOC, 0, 0)));

		assertThat(result.documents()).hasSize(1);
		assertThat(result.documents().get(0).pages()).containsExactly(36);
		assertThat(result.ungroupedPages()).isEmpty();
	}

	@Test
	@DisplayName("순번이 겹치면 표기만으로 가르지 않고 LLM에 넘긴다")
	void sendsDuplicatePositionsToLlm() {
		// package_02의 권원보고서가 이렇다. "Page 1~5 of 5"가 두 벌 들어 있어
		// 어느 사본이 어느 벌의 것인지 순번만으로는 알 수 없다
		List<ClassifiedPage> pages = List.of(
				page(3, DocumentType.TITLE_REPORT, 1, 2),
				page(17, DocumentType.TITLE_REPORT, 1, 2),
				page(1, DocumentType.TITLE_REPORT, 2, 2),
				page(42, DocumentType.TITLE_REPORT, 2, 2));

		GroupingResult result = grouper.group(pages);

		// LLM을 못 쓰는 상태이므로 묶이지 않는다. 임의로 짝지어 문서를 만들면 안 된다
		assertThat(result.documents()).isEmpty();
		assertThat(result.ungroupedPages()).containsExactly(1, 3, 17, 42);
	}

	@Test
	@DisplayName("순번 표기가 없는 페이지가 섞여 있으면 LLM에 넘긴다")
	void sendsPagesWithoutMarkerToLlm() {
		// package_01의 신용보고서가 이렇다. 본체에만 순번이 있고 부속 페이지에는 없다
		List<ClassifiedPage> pages = List.of(
				page(1, DocumentType.CREDIT_REPORT, 1, 2),
				page(7, DocumentType.CREDIT_REPORT, 2, 2),
				page(13, DocumentType.CREDIT_REPORT, 0, 0));

		GroupingResult result = grouper.group(pages);

		assertThat(result.documents()).isEmpty();
		assertThat(result.ungroupedPages()).containsExactly(1, 7, 13);
	}

	@Test
	@DisplayName("전체 장수가 다르면 표기만으로 가르지 않고 LLM에 넘긴다")
	void sendsMixedTotalsToLlm() {
		List<ClassifiedPage> pages = List.of(
				page(1, DocumentType.INCOME_DOC, 1, 2),
				page(2, DocumentType.INCOME_DOC, 2, 2),
				page(3, DocumentType.INCOME_DOC, 1, 4));

		GroupingResult result = grouper.group(pages);

		assertThat(result.documents()).isEmpty();
		assertThat(result.ungroupedPages()).containsExactly(1, 2, 3);
	}

	@Test
	@DisplayName("라벨이 정해지지 않은 페이지는 묶지 않는다")
	void skipsUndecidedPages() {
		List<ClassifiedPage> pages = List.of(
				page(1, DocumentType.URLA_1003, 1, 1),
				page(2, DocumentType.UNDECIDED, 0, 0));

		GroupingResult result = grouper.group(pages);

		assertThat(result.documents()).hasSize(1);
		assertThat(result.documents().get(0).pages()).containsExactly(1);
		assertThat(result.ungroupedPages()).isEmpty();
	}

	@Test
	@DisplayName("라벨이 다르면 순번이 같아도 서로 다른 문서로 본다")
	void separatesByLabel() {
		// package_01은 URLA와 신용보고서 본체가 둘 다 "of 11"이다. 라벨이 갈라 준다
		List<ClassifiedPage> pages = List.of(
				page(6, DocumentType.URLA_1003, 1, 2),
				page(2, DocumentType.URLA_1003, 2, 2),
				page(9, DocumentType.CREDIT_REPORT, 1, 2),
				page(7, DocumentType.CREDIT_REPORT, 2, 2));

		GroupingResult result = grouper.group(pages);

		assertThat(result.documents()).hasSize(2);
		assertThat(result.ungroupedPages()).isEmpty();
	}

	@Test
	@DisplayName("페이지가 없으면 빈 결과를 돌려준다")
	void handlesEmptyInput() {
		GroupingResult result = grouper.group(new ArrayList<>());

		assertThat(result.documents()).isEmpty();
		assertThat(result.ungroupedPages()).isEmpty();
	}
}
