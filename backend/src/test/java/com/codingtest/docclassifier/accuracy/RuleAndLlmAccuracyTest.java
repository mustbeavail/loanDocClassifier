package com.codingtest.docclassifier.accuracy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.codingtest.docclassifier.TestMaterials;
import com.codingtest.docclassifier.accuracy.AccuracyResult.PageComparison;
import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.classify.RuleClassifier;
import com.codingtest.docclassifier.classify.RuleDecision;
import com.codingtest.docclassifier.groundtruth.GroundTruth;
import com.codingtest.docclassifier.groundtruth.GroundTruth.GroundTruthPage;
import com.codingtest.docclassifier.llm.GeminiClassifier;
import com.codingtest.docclassifier.llm.LlmDecision;
import com.codingtest.docclassifier.pdf.PdfPage;
import com.codingtest.docclassifier.pdf.PdfPageReader;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * package_01에 규칙 → LLM 2단계를 모두 적용한 정확도를 잰다. STEP 4의 성과 측정이다.
 *
 * <p>규칙만 돌린 수치({@link RuleClassificationAccuracyTest})와 비교해야 2차 판정이
 * 실제로 무엇을 해결했는지 말할 수 있다. 두 수치를 나란히 README에 싣는다.
 *
 * <p>실제 API를 호출하므로 키가 없으면 건너뛴다. 응답을 캐시로 남기지 않기로 했기 때문에
 * 이 테스트는 돌릴 때마다 보류 페이지 수만큼 호출이 나간다.
 */
class RuleAndLlmAccuracyTest {

	/** 2차 판정에 쓰는 모델 */
	private static final String MODEL = "gemini-3.6-flash";

	/** 키를 적어 두는 로컬 설정 파일. .gitignore 대상이라 저장소에는 올라가지 않는다 */
	private static final Path LOCAL_SETTINGS = Path.of("src/main/resources/application-local.properties");

	/**
	 * 측정 결과를 저장할 위치. 이 파일을 저장소에 커밋해 두면 자료와 API 키가 없는 사람도
	 * {@code GET /api/accuracy}로 정확도 화면을 볼 수 있다.
	 */
	private static final Path OUTPUT_PATH = Path.of("src/main/resources/results/package_01-accuracy.json");

	private final PdfPageReader pageReader = new PdfPageReader();
	private final RuleClassifier ruleClassifier = new RuleClassifier();
	private final AccuracyEvaluator evaluator = new AccuracyEvaluator();

	/**
	 * API 키를 찾는다. 환경변수를 먼저 보고, 없으면 로컬 설정 파일을 읽는다.
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

	/**
	 * 저장소에 커밋된 package_01 정답지를 읽는다.
	 *
	 * @return 정답지
	 */
	private GroundTruth loadGroundTruth() throws Exception {
		try (InputStream json = getClass().getResourceAsStream("/ground-truth/package_01.json")) {
			return JsonMapper.builder().build().readValue(json, GroundTruth.class);
		}
	}

	@Test
	@DisplayName("package_01에 규칙+LLM을 적용한 정확도를 측정하고 규칙 단독과 비교한다")
	void measuresRuleAndLlmAccuracy() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");
		String apiKey = findApiKey();
		Assumptions.assumeTrue(!apiKey.isBlank(), "GOOGLE_API_KEY가 없어 건너뜁니다");

		GroundTruth groundTruth = loadGroundTruth();
		GeminiClassifier llmClassifier = new GeminiClassifier(apiKey, MODEL);

		Map<Integer, DocumentType> ruleOnly = new LinkedHashMap<>();
		Map<Integer, DocumentType> combined = new LinkedHashMap<>();
		Map<Integer, String> evidences = new LinkedHashMap<>();

		for (PdfPage page : pageReader.read(TestMaterials.package01Shuffled())) {
			RuleDecision ruleDecision = ruleClassifier.classify(page);
			ruleOnly.put(page.pageNumber(), ruleDecision.label());

			// 규칙이 판정한 페이지는 그대로 쓴다. LLM에 다시 물으면 비용만 들고 답이 흔들릴 수 있다
			if (ruleDecision.label() != DocumentType.UNDECIDED) {
				combined.put(page.pageNumber(), ruleDecision.label());
				evidences.put(page.pageNumber(), "규칙: " + ruleDecision.evidence());
				continue;
			}

			LlmDecision llmDecision = llmClassifier.classify(page.text());
			combined.put(page.pageNumber(), llmDecision.label());
			evidences.put(page.pageNumber(),
					"LLM(%.2f): %s".formatted(llmDecision.confidence(), llmDecision.reason()));
		}

		AccuracyReport ruleReport = evaluator.evaluate(groundTruth, ruleOnly);
		AccuracyReport combinedReport = evaluator.evaluate(groundTruth, combined);
		AccuracyReportPrinter.print("package_01 규칙+LLM 분류 결과", combinedReport, groundTruth, combined, evidences);

		// 호출이 실패한 페이지가 섞인 수치를 저장하면, 그 파일을 읽는 화면에도 잘못된 수치가 그대로 나간다
		if (warnIfCallsFailed(evidences) == 0) {
			save(combinedReport, groundTruth, combined, evidences);
		}

		// 2차 판정을 붙인 목적은 규칙이 남긴 보류를 없애는 것이다. 보류가 그대로면 붙인 의미가 없다
		assertThat(combinedReport.undecidedPages())
				.as("LLM 판정 후 남은 보류 페이지 수")
				.isLessThan(ruleReport.undecidedPages());

		// 보류를 채우면서 오답이 늘어 정확도가 규칙 단독보다 떨어지면 2차 판정이 해가 된 것이다
		assertThat(combinedReport.accuracy())
				.as("규칙+LLM 정확도")
				.isGreaterThan(ruleReport.accuracy());
	}

	/**
	 * 호출 실패로 미판정이 된 페이지가 있으면 눈에 띄게 알린다.
	 *
	 * <p>API가 죽어서 못 판정한 것과 모델이 판단을 못 한 것은 원인이 전혀 다른데,
	 * 정확도 숫자만 보면 둘 다 똑같이 "낮은 정확도"로 보인다.
	 * 수치를 README에 옮기기 전에 이 줄을 보고 걸러내라고 남기는 경고다.
	 *
	 * @param evidences 페이지별 판정 근거·사유
	 * @return 호출이 실패한 페이지 수
	 */
	private int warnIfCallsFailed(Map<Integer, String> evidences) {
		int failedPages = 0;
		for (String evidence : evidences.values()) {
			if (evidence.contains("호출 실패")) {
				failedPages++;
			}
		}

		if (failedPages > 0) {
			PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
			out.printf("%n[주의] %d장은 Gemini 호출이 실패해 미판정입니다."
					+ " 이번 수치는 분류 성능이 아니라 API 상태를 반영한 것이므로 그대로 쓰지 마세요.%n", failedPages);
		}
		return failedPages;
	}

	/**
	 * 측정 결과를 JSON 파일로 저장한다. {@code GET /api/accuracy}가 이 파일을 읽어 화면에 뿌린다.
	 *
	 * <p>정확도 수치를 손으로 옮겨 적지 않는다는 것이 요점이다.
	 * 측정한 그 자리에서 저장하므로 화면의 숫자와 실제 측정 결과가 어긋날 수 없다.
	 *
	 * @param report      측정 리포트
	 * @param groundTruth 정답지
	 * @param predictions 페이지 번호 → 판정한 라벨
	 * @param evidences   페이지 번호 → 판정 근거·사유
	 * @throws IOException 파일을 쓰지 못한 경우
	 */
	private void save(AccuracyReport report, GroundTruth groundTruth,
			Map<Integer, DocumentType> predictions, Map<Integer, String> evidences) throws IOException {

		List<PageComparison> comparisons = new ArrayList<>();
		for (GroundTruthPage answer : groundTruth.pages()) {
			comparisons.add(new PageComparison(
					answer.page(),
					answer.label(),
					predictions.get(answer.page()),
					evidences.get(answer.page())));
		}

		AccuracyResult result = new AccuracyResult(groundTruth.packageId(), "규칙 → LLM", report, comparisons);

		Files.createDirectories(OUTPUT_PATH.getParent());
		JsonMapper.builder()
				.enable(SerializationFeature.INDENT_OUTPUT)
				.build()
				.writeValue(OUTPUT_PATH.toFile(), result);
	}
}
