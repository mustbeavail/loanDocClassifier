package com.codingtest.docclassifier.group;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.codingtest.docclassifier.pdf.PdfPageReader;

/**
 * 페이지 텍스트에서 순번 표기("3 of 10")를 찾아낸다.
 *
 * <p>제공 자료에서 확인된 표기는 세 가지 모양이다.
 * "7 of 10"(URLA), "Page 5 of 9"(신용보고서), "PAGE 1 OF 2"(세무 기록).
 * 앞에 붙는 "Page"는 있을 수도 없을 수도 있고 대소문자도 섞여 있어 한 패턴으로 묶었다.
 */
@Component
public class PageMarkerParser {

	/**
	 * 순번 표기 패턴. "Page"는 선택이고 대소문자를 가리지 않는다.
	 *
	 * <p>숫자를 {@code \d(?:\s?\d){0,2}} 로 잡는 이유는 자릿수 사이에 공백이 끼어 추출되는 페이지가 있어서다.
	 * package_01의 신용보고서는 "Page 4 of 11"이 "Page 4 of 1 1"로 읽힌다.
	 * 이걸 그냥 두면 11장짜리 문서가 1장짜리로 잡혀 그룹핑이 통째로 어긋난다.
	 *
	 * <p>반복을 세 자리로 묶어 둔 이유는, 공백을 허용한 채 열어 두면 뒤따라오는 다른 숫자까지 한 숫자로 삼키기 때문이다.
	 * "Page 8 of 9 990367284"가 통째로 걸리면 전체 장수가 정수 범위를 넘어 파싱 단계에서 예외가 난다.
	 * 서류 한 부가 999장을 넘는 일은 없으므로 세 자리에서 끊는다.
	 */
	private static final Pattern MARKER =
			Pattern.compile("(?i)\\b(?:page\\s+)?(\\d(?:\\s?\\d){0,2})\\s+of\\s+(\\d(?:\\s?\\d){0,2})\\b");

	/**
	 * 총장수 없이 쪽번호만 찍는 양식을 위한 패턴.
	 * package_01의 권원보고서(CLTA)가 바닥글에 "Page 4"까지만 적고 전체 장수를 적지 않는다.
	 */
	private static final Pattern PAGE_NUMBER_ONLY = Pattern.compile("(?i)\\bpage\\s+(\\d{1,3})\\b");

	/**
	 * 페이지 텍스트에서 순번 표기를 찾는다.
	 *
	 * <p>총장수가 함께 찍힌 "N of M"을 먼저 본다. 그 모양이 텍스트에 하나도 없을 때만
	 * 쪽번호만 있는 "Page N"으로 물러난다. "Page 12 of 5"처럼 총장수가 있는데 값이 이상한 경우까지
	 * 쪽번호로 되살리면 버리기로 한 표기가 되살아나기 때문이다.
	 *
	 * <p>여러 개가 잡히면 <b>마지막 것</b>을 쓴다. 순번은 대개 바닥글에 찍히는데
	 * 본문에도 "1 of 3 borrowers" 같은 표현이 나올 수 있어, 뒤쪽에 있는 것이 바닥글일 가능성이 높다.
	 *
	 * @param pageText 페이지 텍스트
	 * @return 찾은 순번 표기. 없으면 null
	 */
	public PageMarker parse(String pageText) {
		String text = PdfPageReader.normalizeWhitespace(pageText);
		Matcher matcher = MARKER.matcher(text);

		boolean sawTotalForm = false;
		PageMarker found = null;
		while (matcher.find()) {
			sawTotalForm = true;
			int position = toNumber(matcher.group(1));
			int total = toNumber(matcher.group(2));

			// 순번이 전체 장수보다 클 수는 없다. 이런 매치는 본문 숫자가 우연히 걸린 것이다
			if (position >= 1 && position <= total) {
				found = new PageMarker(position, total);
			}
		}

		return sawTotalForm ? found : parsePageNumberOnly(text);
	}

	/**
	 * 총장수 없이 쪽번호만 찍힌 표기를 읽는다.
	 *
	 * <p>"Book 577, Page 401" 같은 등기 참조도 모양이 똑같다. 페이지 한 장만 봐서는 가릴 수 없어
	 * 여기서는 후보로만 내놓고, 같은 라벨의 페이지를 모아 볼 수 있는 {@code DocumentGrouper}가 걸러낸다.
	 *
	 * @param text 공백이 정규화된 페이지 텍스트
	 * @return 총장수를 0으로 둔 순번 표기. 없으면 null
	 */
	private PageMarker parsePageNumberOnly(String text) {
		Matcher matcher = PAGE_NUMBER_ONLY.matcher(text);

		PageMarker found = null;
		while (matcher.find()) {
			found = new PageMarker(Integer.parseInt(matcher.group(1)), 0);
		}
		return found;
	}

	/**
	 * 자릿수 사이에 낀 공백을 지우고 숫자로 바꾼다. "1 1"은 11이 된다.
	 *
	 * @param digits 정규식이 잡은 숫자 문자열
	 * @return 정수 값
	 */
	private int toNumber(String digits) {
		return Integer.parseInt(digits.replace(" ", ""));
	}
}
