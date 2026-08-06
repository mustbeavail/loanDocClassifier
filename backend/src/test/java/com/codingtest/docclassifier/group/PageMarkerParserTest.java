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
	@DisplayName("표기를 사람이 읽는 문자열로 만든다")
	void rendersMarkerText() {
		assertThat(new PageMarker(3, 11).text()).isEqualTo("3 of 11");
		// 총장수를 모르면 "3 of 0"처럼 없는 정보를 지어내지 않는다
		assertThat(new PageMarker(3, 0).text()).isEqualTo("Page 3");
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
	@DisplayName("총장수 없이 쪽번호만 찍힌 양식은 총장수를 0으로 읽는다")
	void readsPageNumberWithoutTotal() {
		// package_01 권원보고서(CLTA)의 바닥글이다. "of 9"가 없다
		PageMarker marker = parser.parse(
				"CLTA Preliminary Report Form (02/03/2023) Printed: 04.22.26 @ 12:26 PM Page 4 CA-FT-FLVE-01500");

		assertThat(marker).isEqualTo(new PageMarker(4, 0));
	}

	@Test
	@DisplayName("총장수가 함께 있으면 쪽번호만 있는 표기보다 그쪽을 쓴다")
	void prefersMarkerWithTotal() {
		// 등기 참조("Book 15502 at Page 2096")와 바닥글이 한 페이지에 같이 있는 경우다
		PageMarker marker = parser.parse(
				"recorded in Deed Book 15502 at Page 2096 ... Form 50167851 (8-25-22) Page 2 of 5");

		assertThat(marker).isEqualTo(new PageMarker(2, 5));
	}

	@Test
	@DisplayName("총장수가 있는데 값이 이상하면 쪽번호로 되살리지 않는다")
	void doesNotFallBackWhenTotalFormIsImplausible() {
		// "12 of 5"를 버린 뒤 "Page 12"로 되살리면 버리기로 한 표기가 돌아온다
		assertThat(parser.parse("paid Page 12 of 5 installments")).isNull();
	}

	@Test
	@DisplayName("표기가 여러 개면 마지막 것을 쓴다")
	void usesLastMarker() {
		// 순번은 대개 바닥글에 찍히므로 뒤에 있는 것이 진짜일 가능성이 높다
		PageMarker marker = parser.parse("1 of 3 borrowers listed. ... Page 5 of 9");

		assertThat(marker).isEqualTo(new PageMarker(5, 9));
	}
}
