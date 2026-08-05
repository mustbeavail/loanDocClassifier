package com.codingtest.docclassifier.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.codingtest.docclassifier.classify.DocumentType;

/**
 * Gemini 2차 판정기 테스트.
 *
 * <p>키가 필요 없는 항목(키 없을 때의 동작)과 키가 있어야 도는 항목(실제 판정·재현성)을 나눠 담았다.
 * 뒤쪽은 키가 없으면 건너뛴다. 자료나 키 없이 저장소만 받은 사람도 앞쪽 테스트는 그대로 돌아가야 하기 때문이다.
 */
class GeminiClassifierTest {

	/** 2차 판정에 쓰는 모델 */
	private static final String MODEL = "gemini-3.6-flash";

	/** 실제 서류 한 페이지의 앞부분 (package_02 23페이지, URLA 신청서 7/10장) */
	private static final String URLA_PAGE_TEXT = """
			Borrower Name: Uniform Residential Loan Application
			Freddie Mac Form 65 - Fannie Mae Form 1003 Effective 1/2021
			7 of 10 Section 9: Loan Originator Information.
			To be completed by your Loan Originator.
			""";

	/** 키를 적어 두는 로컬 설정 파일. .gitignore 대상이라 저장소에는 올라가지 않는다 */
	private static final Path LOCAL_SETTINGS = Path.of("src/main/resources/application-local.properties");

	/**
	 * API 키를 찾는다. 실행 환경 두 가지를 모두 본다.
	 *
	 * <p>환경변수를 먼저 보고, 없으면 로컬 설정 파일을 읽는다.
	 * 애플리케이션이 키를 받는 경로가 그 둘이라, 테스트도 같은 경로로 맞춰야
	 * "README대로 키를 넣었는데 테스트만 건너뛴다"는 상황이 생기지 않는다.
	 *
	 * @return 키. 어느 쪽에도 없으면 빈 문자열
	 */
	private String findApiKey() {
		String fromEnvironment = System.getenv("GOOGLE_API_KEY");
		if (fromEnvironment != null && !fromEnvironment.isBlank()) {
			return fromEnvironment;
		}

		if (!Files.exists(LOCAL_SETTINGS)) {
			return "";
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(LOCAL_SETTINGS)) {
			properties.load(reader);
		} catch (IOException exception) {
			return "";
		}
		return properties.getProperty("gemini.api-key", "");
	}

	@Test
	@DisplayName("키가 없으면 예외 없이 미판정을 돌려준다")
	void withoutApiKey_returnsUndecided() {
		GeminiClassifier classifier = new GeminiClassifier("", MODEL);

		LlmDecision decision = classifier.classify(URLA_PAGE_TEXT);

		assertThat(decision.label()).isEqualTo(DocumentType.UNDECIDED);
		assertThat(decision.confidence()).isZero();
		assertThat(decision.reason()).isEqualTo("API 키 없음");
	}

	@Test
	@DisplayName("키가 없으면 빈 텍스트를 넣어도 예외가 나지 않는다")
	void withoutApiKey_emptyTextDoesNotThrow() {
		GeminiClassifier classifier = new GeminiClassifier("", MODEL);

		assertThatCode(() -> classifier.classify("")).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("호출이 실패하면 미판정 사유에 실패 사실과 원인이 한 줄로 남는다")
	void failedCall_recordsShortReason() {
		// 일부러 못 쓰는 키를 넣어 실패 경로를 태운다.
		// 재시도를 넣지 않기로 했으므로, 실패했을 때 사유가 남는지가 이 경로의 유일한 안전장치다
		LlmDecision decision = new GeminiClassifier("이-키는-쓸-수-없다", MODEL).classify(URLA_PAGE_TEXT);

		assertThat(decision.label()).isEqualTo(DocumentType.UNDECIDED);
		assertThat(decision.reason()).startsWith("호출 실패: ");
		// 오류 응답 본문이 통째로 딸려 오면 리포트 표가 망가진다. 한 줄로 잘렸는지 확인한다
		assertThat(decision.reason()).hasSizeLessThan(120);
		assertThat(decision.reason()).doesNotContain("\n");
	}

	@Test
	@DisplayName("URLA 신청서 페이지를 URLA_1003으로 판정한다")
	void classifiesUrlaPage() {
		String apiKey = findApiKey();
		Assumptions.assumeTrue(!apiKey.isBlank(), "GOOGLE_API_KEY가 없어 건너뜁니다");

		LlmDecision decision = new GeminiClassifier(apiKey, MODEL).classify(URLA_PAGE_TEXT);

		assertThat(decision.label()).isEqualTo(DocumentType.URLA_1003);
		assertThat(decision.confidence()).isBetween(0.0, 1.0);
		assertThat(decision.reason()).isNotBlank();
	}

	@Test
	@DisplayName("대출 서류와 무관한 텍스트는 OTHER로 판정한다")
	void classifiesUnrelatedTextAsOther() {
		String apiKey = findApiKey();
		Assumptions.assumeTrue(!apiKey.isBlank(), "GOOGLE_API_KEY가 없어 건너뜁니다");

		String unrelated = "오늘 점심 메뉴는 김치찌개와 계란말이입니다. 영업시간은 오전 11시부터입니다.";

		LlmDecision decision = new GeminiClassifier(apiKey, MODEL).classify(unrelated);

		assertThat(decision.label()).isEqualTo(DocumentType.OTHER);
	}

	@Test
	@DisplayName("같은 페이지를 세 번 물어도 같은 라벨이 나온다")
	void repeatedCallsReturnSameLabel() {
		String apiKey = findApiKey();
		Assumptions.assumeTrue(!apiKey.isBlank(), "GOOGLE_API_KEY가 없어 건너뜁니다");

		// 응답 캐시를 두지 않기로 했으므로, 재현성은 매번 실제로 호출해서 확인해야 한다.
		// 온도 0과 라벨 enum 제한이 실제로 효과가 있는지 보는 항목이다
		GeminiClassifier classifier = new GeminiClassifier(apiKey, MODEL);
		List<DocumentType> labels = new ArrayList<>();
		for (int attempt = 0; attempt < 3; attempt++) {
			labels.add(classifier.classify(URLA_PAGE_TEXT).label());
		}

		// 호출이 다 실패해도 UNDECIDED가 세 번 나와 "일치"로 보인다.
		// 판정이 실제로 이뤄졌는지 먼저 확인해야 이 테스트가 재현성을 재는 의미를 갖는다
		assertThat(labels.get(0)).as("판정 결과. 미판정이면 호출 자체가 실패한 것이다")
				.isNotEqualTo(DocumentType.UNDECIDED);
		assertThat(labels).containsOnly(labels.get(0));
	}
}
