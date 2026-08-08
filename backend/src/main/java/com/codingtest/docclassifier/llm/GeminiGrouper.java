package com.codingtest.docclassifier.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 순번 표기만으로는 가를 수 없는 페이지들을 Gemini에게 물어 문서 단위로 묶는다.
 *
 * <p>순번 표기가 모든 페이지에 있고 겹치지도 않으면 규칙만으로 문서가 완성된다. 그때는 부르지 않는다.
 * 부르는 경우는 두 가지다.
 * <ul>
 * <li>같은 유형의 문서가 여러 벌이라 순번이 겹칠 때. "3 of 5"가 두 장이면
 *     어느 쪽이 어느 문서의 3장인지 순번만으로는 알 수 없다.
 * <li>순번 표기가 없는 페이지가 섞여 있을 때. 어느 문서의 몇 번째 장인지 표기가 말해 주지 않는다.
 * </ul>
 *
 * <p>이 두 경우를 규칙으로 풀려면 문서번호·발행일 같은 식별자를 양식마다 정규식으로 뽑아야 한다.
 * 그러면 문서 유형이 늘어날 때마다 정규식을 덧붙여야 하므로, 내용을 읽고 판단하는 쪽에 맡겼다.
 */
@Component
public class GeminiGrouper {

	private static final Logger log = LoggerFactory.getLogger(GeminiGrouper.class);

	/**
	 * 페이지 하나당 프롬프트에 넣을 텍스트 길이.
	 * 같은 문서인지 가르는 단서(발행일·문서번호·물건 주소·이어지는 문장)는 앞부분에 몰려 있다.
	 */
	private static final int TEXT_PER_PAGE = 700;

	private final JsonMapper json = JsonMapper.builder().build();

	/** Gemini API 키. 없으면 빈 문자열이고, 그때는 묶지 않고 물러난다 */
	private final String apiKey;

	/** 판정에 쓸 모델 이름 */
	private final String model;

	/**
	 * @param apiKey Gemini API 키
	 * @param model  사용할 모델 이름
	 */
	public GeminiGrouper(@Value("${gemini.api-key:}") String apiKey, @Value("${gemini.model}") String model) {
		this.apiKey = apiKey;
		this.model = model;
	}

	/**
	 * 페이지들을 문서 단위로 묶고 각 문서 안에서 순서를 매긴다.
	 *
	 * @param label 이 페이지들의 문서 유형. 프롬프트에 문맥으로 넣는다
	 * @param pages 묶을 페이지들 (페이지 번호 · 순번 표기 · 텍스트)
	 * @return 문서별 페이지 번호 목록. 키가 없거나 호출이 실패하면 빈 목록
	 */
	public List<List<Integer>> group(String label, List<PageForGrouping> pages) {
		if (apiKey.isBlank()) {
			log.warn("Gemini API 키가 없어 {} 그룹핑을 건너뜁니다", label);
			return List.of();
		}

		try (Client client = Client.builder().apiKey(apiKey).build()) {
			GenerateContentResponse response =
					client.models.generateContent(model, buildPrompt(label, pages), responseFormat());
			return parse(response.text(), pages);

		} catch (Exception exception) {
			log.warn("Gemini 그룹핑 실패({}): {}", label, exception.toString());
			return List.of();
		}
	}

	/**
	 * 응답 형식을 JSON으로 고정한다.
	 *
	 * <p>문서 배열 하나만 받는다. 온도를 0으로 두는 이유는 같은 입력에 같은 묶음이 나와야
	 * 결과를 비교하고 검증할 수 있기 때문이다.
	 *
	 * @return 구조화 응답 설정
	 */
	private GenerateContentConfig responseFormat() {
		Schema pageList = Schema.builder()
				.type(Type.Known.ARRAY)
				.items(Schema.builder().type(Type.Known.INTEGER).build())
				.build();

		Schema schema = Schema.builder()
				.type(Type.Known.OBJECT)
				.properties(Map.of("documents", Schema.builder()
						.type(Type.Known.ARRAY)
						.items(pageList)
						.build()))
				.required("documents")
				.build();

		return GenerateContentConfig.builder()
				.responseMimeType("application/json")
				.responseSchema(schema)
				.temperature(0.0f)
				.build();
	}

	/**
	 * 그룹핑 지시문을 만든다.
	 *
	 * <p>순번 표기를 함께 넣는 이유는, 표기가 있는 페이지는 그것을 그대로 따르게 하고
	 * 표기가 없는 페이지만 내용으로 판단하게 하기 위해서다.
	 * 모델이 표기를 무시하고 내용만으로 순서를 뒤집으면 확실한 정보를 버리는 셈이 된다.
	 *
	 * <p>지시문은 특정 양식이 아니라 <b>서류 일반의 구조</b>로만 적었다.
	 * "표지가 앞, 부속이 뒤"는 어느 서류에나 통하지만,
	 * "재발행 통지문은 고지문 뒤" 같은 규칙은 이 자료에서만 맞고 다른 발행사 서류에는 틀린다.
	 * 실제로 그런 규칙을 넣어 봤다가 정확도가 떨어져 걷어냈다.
	 * 문서 유형이 늘어날 때 프롬프트를 고치지 않아도 되게 하는 것이 규칙 대신 LLM을 쓴 이유이므로,
	 * 이 파일에 양식별 지식을 쌓기 시작하면 그 이점이 사라진다.
	 *
	 * @param label 문서 유형
	 * @param pages 묶을 페이지들
	 * @return 프롬프트 전문
	 */
	private String buildPrompt(String label, List<PageForGrouping> pages) {
		StringBuilder prompt = new StringBuilder();
		prompt.append("""
				여러 종류의 서류가 섞인 패키지에서 뽑은 페이지들이다. 페이지 순서는 무작위로 섞여 있다.
				아래 페이지들은 모두 같은 문서 유형(%s)으로 분류되었다.

				같은 문서에 속한 페이지끼리 묶고, 각 문서 안에서 원래 장 순서를 정확히 맞춰라.

				[같은 문서인지 판단하는 근거 — 위에서부터 차례로 적용한다]
				위 근거로 판단되면 아래 근거는 보지 않는다.
				1. 문서 식별자 — 보고서 번호·파일 번호·문서 번호가 같으면 같은 문서다. 가장 확실한 근거다.
				2. 발행 정보 — 발행사·발행일·수신인·대상 물건이 같으면 같은 문서로 본다.
				3. 순번 — 같은 총장수의 순번(3 of 11)이 붙어 있으면 같은 문서의 본문이다.

				1~3 중 어느 것도 적용할 수 없는 페이지만 documents에서 빼라.

				[장 순서]
				페이지에 인쇄된 순번이나 쪽번호를 그대로 따르고, 표기가 없는 페이지만 내용으로 자리를 정하라.

				출력: documents 배열. 각 원소는 한 문서의 페이지 번호를 장 순서대로 담은 배열이다.

				""".formatted(label));

		for (PageForGrouping page : pages) {
			prompt.append("--- 페이지 ").append(page.pageNumber()).append(" ---\n");
			prompt.append("순번: ").append(page.markerText() == null ? "없음" : page.markerText()).append("\n");
			prompt.append(excerpt(page.text())).append("\n\n");
		}
		return prompt.toString();
	}

	/**
	 * 페이지 텍스트를 프롬프트에 넣을 만큼만 자른다.
	 *
	 * @param text 페이지 텍스트
	 * @return 잘라 낸 텍스트
	 */
	private String excerpt(String text) {
		String flat = text.replaceAll("\\s+", " ").trim();
		return flat.length() > TEXT_PER_PAGE ? flat.substring(0, TEXT_PER_PAGE) : flat;
	}

	/**
	 * 모델이 돌려준 JSON을 문서별 페이지 목록으로 바꾼다.
	 *
	 * <p>모델이 페이지를 빠뜨리거나 없는 번호를 지어낼 수 있으므로,
	 * 입력에 있던 번호만 남기고 중복은 버린다. 빠진 페이지는 부르는 쪽에서 처리한다.
	 *
	 * @param responseText JSON 문자열
	 * @param pages        입력으로 준 페이지들
	 * @return 문서별 페이지 번호 목록
	 */
	private List<List<Integer>> parse(String responseText, List<PageForGrouping> pages) {
		List<Integer> allowed = new ArrayList<>();
		for (PageForGrouping page : pages) {
			allowed.add(page.pageNumber());
		}

		List<Integer> used = new ArrayList<>();
		List<List<Integer>> documents = new ArrayList<>();

		for (JsonNode documentNode : json.readTree(responseText).get("documents")) {
			List<Integer> pageNumbers = new ArrayList<>();
			for (JsonNode pageNode : documentNode) {
				int pageNumber = pageNode.asInt();
				if (allowed.contains(pageNumber) && !used.contains(pageNumber)) {
					pageNumbers.add(pageNumber);
					used.add(pageNumber);
				}
			}
			if (!pageNumbers.isEmpty()) {
				documents.add(pageNumbers);
			}
		}
		return documents;
	}

	/**
	 * 그룹핑에 넘길 페이지 한 장의 정보.
	 *
	 * @param pageNumber 셔플 PDF에서의 페이지 번호
	 * @param markerText 순번 표기를 사람이 읽는 형태로 적은 것 (예: "3 of 5"). 표기가 없으면 null
	 * @param text       페이지 텍스트
	 */
	public record PageForGrouping(int pageNumber, String markerText, String text) {
	}
}
