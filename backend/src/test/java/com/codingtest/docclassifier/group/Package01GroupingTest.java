package com.codingtest.docclassifier.group;

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
import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.groundtruth.GroundTruth;
import com.codingtest.docclassifier.groundtruth.GroundTruth.GroundTruthPage;
import com.codingtest.docclassifier.llm.GeminiGrouper;
import com.codingtest.docclassifier.pdf.PdfPage;
import com.codingtest.docclassifier.pdf.PdfPageReader;

import tools.jackson.databind.json.JsonMapper;

/**
 * package_01을 실제로 묶어 원본 4개 문서가 복원되는지 확인한다. STEP 5의 성과 측정이다.
 *
 * <p>라벨은 정답지 값을 쓰고 순번 표기만 실제 PDF에서 뽑는다.
 * 분류 정확도는 이미 따로 재고 있으므로, 여기서는 <b>그룹핑 규칙만</b> 시험한다.
 * 분류 결과를 섞으면 결과가 틀렸을 때 분류 탓인지 그룹핑 탓인지 가릴 수 없다.
 */
class Package01GroupingTest {

	/** 2차 판정에 쓰는 모델 */
	private static final String MODEL = "gemini-3.6-flash";

	/** 키를 적어 두는 로컬 설정 파일. .gitignore 대상이라 저장소에는 올라가지 않는다 */
	private static final Path LOCAL_SETTINGS = Path.of("src/main/resources/application-local.properties");

	private final PdfPageReader pageReader = new PdfPageReader();
	private final PageMarkerParser markerParser = new PageMarkerParser();

	/** 한글이 깨지지 않게 UTF-8로 출력한다 */
	private final PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);

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
	@DisplayName("package_01을 묶으면 원본 4개 문서가 그대로 복원된다")
	void restoresFourOriginalDocuments() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");
		String apiKey = findApiKey();
		Assumptions.assumeTrue(!apiKey.isBlank(), "GOOGLE_API_KEY가 없어 건너뜁니다");
		DocumentGrouper grouper = new DocumentGrouper(new GeminiGrouper(apiKey, MODEL));

		GroundTruth groundTruth = loadGroundTruth();
		Map<Integer, DocumentType> answerLabels = new LinkedHashMap<>();
		Map<Integer, Integer> answerSourcePages = new LinkedHashMap<>();
		for (GroundTruthPage answer : groundTruth.pages()) {
			answerLabels.put(answer.page(), answer.label());
			answerSourcePages.put(answer.page(), answer.sourcePage());
		}

		List<ClassifiedPage> pages = new ArrayList<>();
		for (PdfPage page : pageReader.read(TestMaterials.package01Shuffled())) {
			pages.add(new ClassifiedPage(
					page.pageNumber(),
					answerLabels.get(page.pageNumber()),
					markerParser.parse(page.text()),
					page.text()));
		}

		GroupingResult result = grouper.group(pages);
		print(result, answerSourcePages);

		// 원본은 URLA·CREDIT·TITLE·INCOME 각 1개씩 4개 문서다
		assertThat(result.documents()).hasSize(4);
		assertThat(result.ungroupedPages()).as("어느 문서에도 넣지 못한 페이지").isEmpty();

		assertThat(pagesOf(result, DocumentType.URLA_1003))
				.containsExactlyInAnyOrderElementsOf(pagesWithLabel(groundTruth, DocumentType.URLA_1003));
		assertThat(pagesOf(result, DocumentType.CREDIT_REPORT))
				.containsExactlyInAnyOrderElementsOf(pagesWithLabel(groundTruth, DocumentType.CREDIT_REPORT));
		assertThat(pagesOf(result, DocumentType.TITLE_REPORT))
				.containsExactlyInAnyOrderElementsOf(pagesWithLabel(groundTruth, DocumentType.TITLE_REPORT));
		assertThat(pagesOf(result, DocumentType.INCOME_DOC))
				.containsExactlyInAnyOrderElementsOf(pagesWithLabel(groundTruth, DocumentType.INCOME_DOC));

		// 문서 경계뿐 아니라 장 순서까지 원본과 같아야 한다.
		// 과제가 요구하는 것이 "시작·끝 페이지 식별"이므로 순서가 틀리면 그룹핑이 제 역할을 못 한 것이다.
		// URLA는 순번 표기로, 나머지는 LLM이 내용을 읽어 세운 결과다
		for (DocumentGroup document : result.documents()) {
			assertThat(sourceOrderOf(document, answerSourcePages))
					.as("%s 문서의 장 순서", document.label())
					.isEqualTo(countUpTo(document.pages().size()));
		}
	}

	/**
	 * 문서에 담긴 페이지들을 원본 장 번호로 바꾼다.
	 *
	 * @param document          복원한 문서
	 * @param answerSourcePages 셔플 페이지 → 원본 페이지 번호
	 * @return 담긴 순서대로의 원본 장 번호들
	 */
	private List<Integer> sourceOrderOf(DocumentGroup document, Map<Integer, Integer> answerSourcePages) {
		List<Integer> sourceOrder = new ArrayList<>();
		for (int pageNumber : document.pages()) {
			sourceOrder.add(answerSourcePages.get(pageNumber));
		}
		return sourceOrder;
	}

	/**
	 * 1부터 세는 기대 순서를 만든다.
	 *
	 * @param size 장수
	 * @return 1, 2, ... size
	 */
	private List<Integer> countUpTo(int size) {
		List<Integer> expected = new ArrayList<>();
		for (int number = 1; number <= size; number++) {
			expected.add(number);
		}
		return expected;
	}

	/**
	 * 결과에서 특정 라벨 문서의 페이지 목록을 꺼낸다.
	 *
	 * @param result 그룹핑 결과
	 * @param label  찾을 라벨
	 * @return 그 문서의 페이지 번호들
	 */
	private List<Integer> pagesOf(GroupingResult result, DocumentType label) {
		for (DocumentGroup document : result.documents()) {
			if (document.label() == label) {
				return document.pages();
			}
		}
		return List.of();
	}

	/**
	 * 정답지에서 특정 라벨인 페이지 번호를 모은다.
	 *
	 * @param groundTruth 정답지
	 * @param label       찾을 라벨
	 * @return 페이지 번호들
	 */
	private List<Integer> pagesWithLabel(GroundTruth groundTruth, DocumentType label) {
		List<Integer> pageNumbers = new ArrayList<>();
		for (GroundTruthPage answer : groundTruth.pages()) {
			if (answer.label() == label) {
				pageNumbers.add(answer.page());
			}
		}
		return pageNumbers;
	}

	/**
	 * 복원한 문서들을 사람이 읽을 수 있게 출력한다.
	 *
	 * @param result            그룹핑 결과
	 * @param answerSourcePages 셔플 페이지 → 원본 페이지 번호
	 */
	private void print(GroupingResult result, Map<Integer, Integer> answerSourcePages) {
		out.printf("%n=== package_01 문서 그룹핑 결과 ===%n");
		for (DocumentGroup document : result.documents()) {
			out.printf("%n%-14s %d장 (순번 표기 %s)%n",
					document.label(), document.pages().size(),
					document.totalPages() == 0 ? "없음" : "of " + document.totalPages());
			out.printf("  시작 p%d → 끝 p%d%n", document.startPage(), document.endPage());
			out.printf("  페이지 %s%n", document.pages());

			List<Integer> sourceOrder = new ArrayList<>();
			for (int pageNumber : document.pages()) {
				sourceOrder.add(answerSourcePages.get(pageNumber));
			}
			out.printf("  원본 장 %s%n", sourceOrder);
		}
		out.printf("%n묶지 못한 페이지 %s%n", result.ungroupedPages());
	}
}
