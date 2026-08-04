package com.codingtest.docclassifier;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

/**
 * STEP 0 사전 검증용 임시 테스트.
 *
 * <p>Gemini API 키가 유효하고 SDK 호출이 되는지만 확인한다.
 * 실제 분류 프롬프트는 STEP 4에서 만든다. 검증이 끝나면 이 클래스는 삭제한다.
 *
 * <p>키는 환경변수 GOOGLE_API_KEY로 주입한다. 코드나 설정 파일에 적지 않는다.
 */
class GeminiVerificationTest {

	/** 2차 분류에 쓸 후보 모델. 저비용·고속 계열 중 최신 버전 */
	private static final String MODEL = "gemini-3.6-flash";

	/**
	 * 실제 서류 한 페이지의 앞부분을 주고 문서 유형을 판정하게 해 본다.
	 * 응답이 정상적으로 오고 라벨을 맞히면 검증 성공이다.
	 */
	@Test
	@DisplayName("Gemini가 서류 텍스트를 받아 문서 유형을 판정한다")
	void gemini_classifiesDocumentType() {
		String apiKey = System.getenv("GOOGLE_API_KEY");
		Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "GOOGLE_API_KEY가 없어 건너뜁니다");

		// package_02 23페이지 앞부분 (URLA 신청서 7/10장)
		String pageText = """
				Borrower Name: Uniform Residential Loan Application
				Freddie Mac Form 65 - Fannie Mae Form 1003 Effective 1/2021
				7 of 10 Section 9: Loan Originator Information.
				To be completed by your Loan Originator.
				""";

		String prompt = """
				다음은 미국 모기지 대출 서류 패키지에서 뽑은 한 페이지의 텍스트다.
				아래 5개 유형 중 하나로만 답하라. 다른 말은 붙이지 마라.

				URLA_1003     : 대출 신청서 (Uniform Residential Loan Application, Form 1003)
				INCOME_DOC    : 소득 증빙 (급여명세서, W-2, 1040, 1099 등)
				CREDIT_REPORT : 신용 보고서 (Tri-merge Credit Report)
				TITLE_REPORT  : 권원 보고서 (Title Commitment 등)
				OTHER         : 위 어디에도 속하지 않는 문서

				---
				""" + pageText;

		try (Client client = Client.builder().apiKey(apiKey).build()) {
			GenerateContentResponse response = client.models.generateContent(MODEL, prompt, null);
			System.out.println("Gemini 응답: [" + response.text().trim() + "]");
		}
	}
}
