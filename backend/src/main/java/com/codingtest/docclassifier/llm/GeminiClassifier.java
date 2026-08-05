package com.codingtest.docclassifier.llm;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.codingtest.docclassifier.classify.DocumentType;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 규칙으로 판정하지 못한 페이지를 Gemini에 물어 라벨을 정한다.
 *
 * <p>모든 페이지를 LLM에 보내지 않는다. 규칙이 확실히 판정한 페이지까지 물어보면
 * 비용과 시간만 늘고 정확도는 오히려 흔들린다. 호출 대상은 규칙이 보류한 페이지뿐이다.
 *
 * <p>키가 없거나 호출이 실패해도 예외를 던지지 않고 미판정으로 돌려준다.
 * 페이지 한 장 때문에 패키지 전체 분류가 멈추면 안 되기 때문이다.
 */
@Component
public class GeminiClassifier {

	private static final Logger log = LoggerFactory.getLogger(GeminiClassifier.class);

	/**
	 * 프롬프트에 넣을 페이지 텍스트의 최대 길이.
	 * 문서 유형은 페이지 앞부분의 제목·양식명으로 거의 갈리고,
	 * 뒤쪽 표 데이터까지 다 넣으면 토큰만 늘어난다.
	 */
	private static final int MAX_TEXT_LENGTH = 3000;

	/** 모델이 답할 수 있는 라벨. 보류(UNDECIDED)는 넣지 않는다. 모르겠으면 OTHER로 답하게 한다 */
	private static final List<String> ALLOWED_LABELS = List.of(
			DocumentType.URLA_1003.name(),
			DocumentType.CREDIT_REPORT.name(),
			DocumentType.TITLE_REPORT.name(),
			DocumentType.INCOME_DOC.name(),
			DocumentType.OTHER.name());

	private final JsonMapper json = JsonMapper.builder().build();

	/** Gemini API 키. 없으면 빈 문자열이고, 그때는 2차 판정을 건너뛴다 */
	private final String apiKey;

	/** 판정에 쓸 모델 이름 */
	private final String model;

	/**
	 * @param apiKey Gemini API 키. 환경변수 GOOGLE_API_KEY 또는 application-local.properties로 주입한다
	 * @param model  사용할 모델 이름
	 */
	public GeminiClassifier(@Value("${gemini.api-key:}") String apiKey, @Value("${gemini.model}") String model) {
		this.apiKey = apiKey;
		this.model = model;
	}

	/**
	 * 페이지 텍스트 하나를 판정한다.
	 *
	 * @param pageText 판정할 페이지의 텍스트
	 * @return 판정 결과. 키가 없거나 호출이 실패하면 예외 대신 미판정 결과가 나온다
	 */
	public LlmDecision classify(String pageText) {
		if (apiKey.isBlank()) {
			log.warn("Gemini API 키가 없어 2차 판정을 건너뜁니다");
			return LlmDecision.undecided("API 키 없음");
		}

		try (Client client = Client.builder().apiKey(apiKey).build()) {
			GenerateContentResponse response =
					client.models.generateContent(model, buildPrompt(pageText), responseFormat());
			return parse(response.text());

		} catch (Exception exception) {
			// 재시도는 하지 않는다. 대신 왜 판정하지 못했는지를 결과에 남겨,
			// 나중에 리포트를 볼 때 "모델이 못 고른 페이지"와 "호출이 실패한 페이지"를 구분할 수 있게 한다
			log.warn("Gemini 판정 실패: {}", exception.toString());
			return LlmDecision.undecided("호출 실패: " + summarize(exception));
		}
	}

	/**
	 * 예외를 한 줄로 줄인다.
	 *
	 * <p>API 오류는 응답 본문(JSON)이 통째로 메시지에 딸려 온다.
	 * 그대로 결과에 넣으면 리포트 표가 몇 줄씩 밀려 읽기 어려워진다.
	 *
	 * @param exception 발생한 예외
	 * @return 예외 메시지의 첫 줄. 비어 있으면 예외 이름
	 */
	private String summarize(Exception exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return exception.getClass().getSimpleName();
		}

		String firstLine = message.lines().findFirst().orElse(message);
		return firstLine.length() > 80 ? firstLine.substring(0, 80) : firstLine;
	}

	/**
	 * 응답 형식을 JSON으로 고정한다.
	 *
	 * <p>라벨 후보를 스키마에 못 박아 두면 모델이 "아마 CREDIT_REPORT인 것 같습니다" 같은
	 * 문장을 돌려주지 못해 파싱이 단순해진다.
	 * 온도를 0으로 두는 이유는 같은 페이지를 다시 물었을 때 같은 답이 나오게 하기 위해서다.
	 *
	 * @return 구조화 응답 설정
	 */
	private GenerateContentConfig responseFormat() {
		Schema schema = Schema.builder()
				.type(Type.Known.OBJECT)
				.properties(Map.of(
						"label", Schema.builder().type(Type.Known.STRING).enum_(ALLOWED_LABELS).build(),
						"confidence", Schema.builder().type(Type.Known.NUMBER).build(),
						"reason", Schema.builder().type(Type.Known.STRING).build()))
				.required("label", "confidence", "reason")
				.build();

		return GenerateContentConfig.builder()
				.responseMimeType("application/json")
				.responseSchema(schema)
				.temperature(0.0f)
				.build();
	}

	/**
	 * 판정 지시문을 만든다.
	 *
	 * <p>"확실하지 않으면 OTHER"를 명시하는 이유는, 규칙이 이미 보류한 애매한 페이지만 여기로 오기 때문이다.
	 * 모델이 억지로 네 유형 중 하나를 고르면 오답이 늘어난다.
	 *
	 * @param pageText 페이지 텍스트
	 * @return 프롬프트 전문
	 */
	private String buildPrompt(String pageText) {
		String trimmed = pageText.length() > MAX_TEXT_LENGTH
				? pageText.substring(0, MAX_TEXT_LENGTH)
				: pageText;

		return """
				다음은 미국 모기지 대출 서류 패키지에서 뽑은 한 페이지의 텍스트다.
				이 페이지가 어떤 문서의 일부인지 아래 5개 유형 중 하나로 판정하라.

				URLA_1003     : 대출 신청서 (Uniform Residential Loan Application, Form 1003 / Form 65)
				CREDIT_REPORT : 신용 보고서 (tri-merge 신용조회 결과, 신용점수 고지문 포함)
				TITLE_REPORT  : 권원 보고서 (Preliminary Report, Commitment for Title Insurance 등)
				INCOME_DOC    : 소득 증빙 (W-2, 세무 기록, 급여명세서, 손익계산서 등)
				OTHER         : 위 네 유형 어디에도 속하지 않는 별개 발행물

				판정 기준
				- 페이지 일부만 보이더라도 그 페이지가 속한 문서의 유형으로 답하라.
				  표지, 서명란, 부록도 본문과 같은 문서로 본다.
				- 어느 유형인지 확실하지 않으면 OTHER로 답하라. 억지로 맞히지 마라.
				- confidence는 0과 1 사이의 확신도, reason은 판단 근거를 한 문장으로 한글로 적어라.

				--- 페이지 텍스트 ---
				""" + trimmed;
	}

	/**
	 * 모델이 돌려준 JSON을 판정 결과로 바꾼다.
	 *
	 * @param responseText JSON 문자열
	 * @return 판정 결과
	 */
	private LlmDecision parse(String responseText) {
		JsonNode node = json.readTree(responseText);
		return new LlmDecision(
				DocumentType.valueOf(node.get("label").asString()),
				node.get("confidence").asDouble(),
				node.get("reason").asString());
	}
}
