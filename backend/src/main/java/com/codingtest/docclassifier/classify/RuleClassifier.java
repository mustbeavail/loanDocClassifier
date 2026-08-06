package com.codingtest.docclassifier.classify;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.codingtest.docclassifier.pdf.PdfPage;
import com.codingtest.docclassifier.pdf.PdfPageReader;

/**
 * 페이지 텍스트에 들어 있는 키워드로 문서 유형을 1차 판정한다.
 *
 * <p>키워드는 <b>문서 유형 고유 문자열</b>로만 고른다. 발행사 이름이나 주소처럼
 * 이 서류 묶음에만 있는 문자열은 쓰지 않는다. 발행사가 바뀌었을 때
 * 규칙이 틀린 답을 내는 것보다, 아무 답도 내지 않고 2차 판정(LLM)으로 넘기는 편이 안전하기 때문이다.
 *
 * <p>확신이 없으면 {@link DocumentType#UNDECIDED}를 낸다.
 * 애매한 페이지에 억지로 라벨을 붙이면 정확도 수치가 실제보다 좋아 보인다.
 */
@Component
public class RuleClassifier {

	/**
	 * 유형별 판정 키워드. 하나라도 들어 있으면 그 유형으로 본다.
	 * 소문자로 적어 두고 비교할 때도 소문자로 맞춘다.
	 */
	private static final Map<DocumentType, List<String>> KEYWORDS = new LinkedHashMap<>();

	static {
		KEYWORDS.put(DocumentType.URLA_1003, List.of(
				"freddie mac form 65",
				"fannie mae form 1003",
				"uniform residential loan application"));
		KEYWORDS.put(DocumentType.CREDIT_REPORT, List.of(
				"repositories: tuc/exp/eqx",
				"credit score disclosure"));
		// 권원보고서는 지역·발행사에 따라 양식 이름이 다르다.
		// 제공 자료만 봐도 package_01은 캘리포니아식 Preliminary Report, package_02는 ALTA Commitment다
		KEYWORDS.put(DocumentType.TITLE_REPORT, List.of(
				"preliminary report",
				"commitment for title insurance",
				"commitment conditions"));
		KEYWORDS.put(DocumentType.INCOME_DOC, List.of(
				"wage and income transcript",
				"form w-2",
				"sensitive taxpayer data"));
	}

	/**
	 * 페이지 하나를 판정한다.
	 *
	 * @param page 판정할 페이지
	 * @return 판정 결과. 키워드가 하나도 없거나 두 유형 이상이 동시에 걸리면 미판정
	 */
	public RuleDecision classify(PdfPage page) {
		return classify(page.text());
	}

	/**
	 * 텍스트만 받아 판정한다.
	 *
	 * <p>스캔 페이지는 텍스트 레이어가 비어 있어 OCR로 읽은 글자를 대신 넣어야 한다.
	 * 그 글자는 {@link PdfPage}에 들어 있지 않으므로 텍스트를 직접 받는 입구를 따로 둔다.
	 *
	 * @param pageText 판정에 쓸 페이지 텍스트
	 * @return 판정 결과. 키워드가 하나도 없거나 두 유형 이상이 동시에 걸리면 미판정
	 */
	public RuleDecision classify(String pageText) {
		String text = PdfPageReader.normalizeWhitespace(pageText).toLowerCase();

		List<RuleDecision> matches = new ArrayList<>();
		for (Map.Entry<DocumentType, List<String>> entry : KEYWORDS.entrySet()) {
			String matchedKeyword = findKeyword(text, entry.getValue());
			if (matchedKeyword != null) {
				matches.add(new RuleDecision(entry.getKey(), matchedKeyword));
			}
		}

		// 두 유형이 동시에 걸리면 어느 쪽인지 규칙으로는 가릴 수 없다
		if (matches.size() != 1) {
			return RuleDecision.undecided();
		}
		return matches.get(0);
	}

	/**
	 * 키워드 목록 중 텍스트에 들어 있는 첫 번째를 찾는다.
	 *
	 * @param text     소문자로 맞춘 페이지 텍스트
	 * @param keywords 한 유형의 키워드 목록
	 * @return 찾은 키워드. 없으면 null
	 */
	private String findKeyword(String text, List<String> keywords) {
		for (String keyword : keywords) {
			if (text.contains(keyword)) {
				return keyword;
			}
		}
		return null;
	}
}
