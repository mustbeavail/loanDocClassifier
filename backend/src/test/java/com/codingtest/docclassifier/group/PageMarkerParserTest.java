package com.codingtest.docclassifier.group;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 순번 표기 파서 테스트.
 *
 * <p>제공 자료에서 실제로 나온 표기 모양을 모두 담았다.
 * 표기 하나를 놓치면 그 문서 전체가 그룹핑에서 빠지므로 모양별로 확인한다.
 */
class PageMarkerParserTest {

	private final PageMarkerParser parser = new PageMarkerParser();

	@Test
	@DisplayName("Page 없이 숫자만 있는 표기를 읽는다")
	void readsBareMarker() {
		PageMarker marker = parser.parse("Uniform Residential Loan Application 7 of 10 GURLA20S");

		assertThat(marker).isEqualTo(new PageMarker(7, 10));
	}

	@Test
	@DisplayName("Page가 앞에 붙은 표기를 읽는다")
	void readsMarkerWithPagePrefix() {
		PageMarker marker = parser.parse("End of Report Page 11 of 11");

		assertThat(marker).isEqualTo(new PageMarker(11, 11));
	}

	@Test
	@DisplayName("대문자 표기를 읽는다")
	void readsUpperCaseMarker() {
		PageMarker marker = parser.parse("This Product Contains Sensitive Taxpayer Data PAGE 2 OF 2");

		assertThat(marker).isEqualTo(new PageMarker(2, 2));
	}

	@Test
	@DisplayName("자릿수 사이에 공백이 끼어도 한 숫자로 읽는다")
	void readsDigitsSplitBySpace() {
		// package_01 신용보고서에서 실제로 "Page 4 of 11"이 이렇게 추출된다.
		// 그냥 두면 11장짜리 문서가 1장짜리로 잡혀 그룹핑이 어긋난다
		PageMarker marker = parser.parse("ID ACCOUNT/ZERO BALANCE Page 4 of 1 1");

		assertThat(marker).isEqualTo(new PageMarker(4, 11));
	}

	@Test
	@DisplayName("표기가 없으면 null을 돌려준다")
	void returnsNullWhenNoMarker() {
		PageMarker marker = parser.parse("Terms Reported On Manner of Payment Revolving Charge");

		assertThat(marker).isNull();
	}

	@Test
	@DisplayName("순번이 전체 장수보다 크면 표기로 보지 않는다")
	void ignoresImpossibleMarker() {
		// 본문 숫자가 우연히 "of"를 사이에 두고 붙은 경우다
		PageMarker marker = parser.parse("paid 12 of 5 installments");

		assertThat(marker).isNull();
	}

	@Test
	@DisplayName("표기가 여러 개면 마지막 것을 쓴다")
	void usesLastMarker() {
		// 순번은 대개 바닥글에 찍히므로 뒤에 있는 것이 진짜일 가능성이 높다
		PageMarker marker = parser.parse("1 of 3 borrowers listed. ... Page 5 of 9");

		assertThat(marker).isEqualTo(new PageMarker(5, 9));
	}
}
