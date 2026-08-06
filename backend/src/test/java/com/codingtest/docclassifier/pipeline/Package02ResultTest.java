package com.codingtest.docclassifier.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.codingtest.docclassifier.TestMaterials;
import com.codingtest.docclassifier.accuracy.AccuracyEvaluator;
import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.classify.RuleClassifier;
import com.codingtest.docclassifier.group.DocumentGrouper;
import com.codingtest.docclassifier.group.PageMarkerParser;
import com.codingtest.docclassifier.llm.GeminiClassifier;
import com.codingtest.docclassifier.llm.GeminiGrouper;
import com.codingtest.docclassifier.ocr.OcrFallback;
import com.codingtest.docclassifier.pdf.PdfPageReader;
import com.codingtest.docclassifier.pipeline.ClassificationResult.DocumentResult;
import com.codingtest.docclassifier.pipeline.ClassificationResult.PageResult;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * package_02를 끝까지 돌려 분류·그룹핑 결과를 파일로 남긴다.
 *
 * <p>과제가 package_02의 분류 결과를 제출물에 포함하라고 요구하는데, package_02에는 정답지가 없다.
 * 그래서 채점은 하지 않고 결과만 저장한다. 저장한 파일을 커밋해 두면 제공 자료나 API 키가 없는 사람도
 * 이 패키지를 어떻게 분류했는지 확인할 수 있다.
 *
 * <p>정답지가 있는 package_01 쪽은 {@code RuleAndLlmAccuracyTest}가 정확도까지 재어 따로 저장한다.
 */
class Package02ResultTest {

	/** 키를 적어 두는 로컬 설정 파일. .gitignore 대상이라 저장소에는 올라가지 않는다 */
	private static final Path LOCAL_SETTINGS = Path.of("src/main/resources/application-local.properties");

	/** 결과를 저장할 위치 */
	private static final Path OUTPUT_PATH = Path.of("src/main/resources/results/package_02.json");

	/** 2차 판정과 그룹핑에 쓰는 모델 */
	private static final String MODEL = "gemini-3.6-flash";

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

	@Test
	@DisplayName("package_02를 분류·그룹핑해 결과 파일을 남긴다")
	void writesPackage02Result() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");
		String apiKey = findApiKey();
		Assumptions.assumeTrue(!apiKey.isBlank(), "GOOGLE_API_KEY가 없어 건너뜁니다");

		// 화면이 쓰는 것과 같은 파이프라인이다. 여기서만 다른 경로로 돌리면 결과가 화면과 어긋난다
		ClassificationService service = new ClassificationService(
				new PdfPageReader(),
				new OcrFallback(),
				new RuleClassifier(),
				new GeminiClassifier(apiKey, MODEL),
				new PageMarkerParser(),
				new DocumentGrouper(new GeminiGrouper(apiKey, MODEL)),
				new AccuracyEvaluator());

		Path pdf = TestMaterials.package02Shuffled();
		ClassificationResult result = service.classify(pdf, pdf.getFileName().toString(), null);

		print(result);

		// 호출이 실패한 페이지가 섞인 결과를 저장하면, 분류 성능이 아니라 API 상태를 남기게 된다
		if (countFailedCalls(result) == 0) {
			save(result);
		}

		assertThat(result.totalPages()).as("package_02 페이지 수").isEqualTo(44);
		assertThat(result.pages())
				.as("판정하지 못한 페이지")
				.noneMatch(page -> page.label() == DocumentType.UNDECIDED);
		assertThat(result.documents()).as("복원한 문서").isNotEmpty();
	}

	/**
	 * 결과 요약을 콘솔에 찍는다. 저장된 파일을 열어 보지 않아도 무엇이 나왔는지 바로 보이게 한다.
	 *
	 * @param result 분류 결과
	 */
	private void print(ClassificationResult result) {
		PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
		long ruleCount = result.pages().stream().filter(page -> page.decidedBy() == DecidedBy.RULE).count();
		long llmCount = result.pages().stream().filter(page -> page.decidedBy() == DecidedBy.LLM).count();
		long ocrCount = result.pages().stream().filter(PageResult::ocrUsed).count();

		out.printf("%n=== package_02 분류 결과 ===%n");
		out.printf("%d페이지 · 규칙 %d장 · LLM %d장 · OCR %d장 · 문서 %d개 · 미분류 %d장%n",
				result.totalPages(), ruleCount, llmCount, ocrCount,
				result.documents().size(), result.ungroupedPages().size());

		for (DocumentResult document : result.documents()) {
			out.printf("  %-14s %2d장  시작 %2d쪽  끝 %2d쪽  %s%n",
					document.label(), document.pages().size(),
					document.startPage(), document.endPage(), document.pages());
		}
	}

	/**
	 * Gemini 호출이 실패한 페이지 수를 센다.
	 *
	 * <p>API가 죽어서 못 판정한 것과 모델이 판단을 못 한 것은 원인이 다른데, 결과 파일만 보면 구분되지 않는다.
	 *
	 * @param result 분류 결과
	 * @return 호출이 실패한 페이지 수
	 */
	private int countFailedCalls(ClassificationResult result) {
		int failedPages = 0;
		for (PageResult page : result.pages()) {
			if (page.evidence() != null && page.evidence().contains("호출 실패")) {
				failedPages++;
			}
		}

		if (failedPages > 0) {
			PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
			out.printf("%n[주의] %d장은 Gemini 호출이 실패했습니다. 결과 파일을 저장하지 않습니다.%n", failedPages);
		}
		return failedPages;
	}

	/**
	 * 결과를 JSON 파일로 저장한다.
	 *
	 * @param result 분류 결과
	 * @throws IOException 파일을 쓰지 못한 경우
	 */
	private void save(ClassificationResult result) throws IOException {
		Files.createDirectories(OUTPUT_PATH.getParent());
		JsonMapper.builder()
				.enable(SerializationFeature.INDENT_OUTPUT)
				.build()
				.writeValue(OUTPUT_PATH.toFile(), result);
	}
}
