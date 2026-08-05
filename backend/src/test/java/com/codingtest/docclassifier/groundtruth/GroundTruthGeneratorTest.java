package com.codingtest.docclassifier.groundtruth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.codingtest.docclassifier.TestMaterials;
import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.groundtruth.GroundTruth.GroundTruthPage;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link GroundTruthGenerator}가 package_01의 정답지를 제대로 복원하는지 확인한다.
 *
 * <p>정답지는 이후 모든 정확도 수치의 기준이 된다.
 * 정답지가 틀리면 분류기가 아무리 잘 동작해도 수치를 믿을 수 없으므로,
 * 39장 전부가 원본과 짝지어지는지를 가장 엄격하게 본다.
 *
 * <p>{@link #savesGroundTruthAsJson()} 은 검증인 동시에 정답지 파일을 만들어 내는 단계다.
 * 즉 정답지는 손으로 만든 것이 아니라 아래 명령으로 언제든 다시 생성된다.
 * 예) {@code mvnw test -Dtest=GroundTruthGeneratorTest -DtestPdfDir="자료 폴더 경로"}
 */
class GroundTruthGeneratorTest {

	/** 생성된 정답지를 저장할 위치. 저장소에 커밋해 자료 없이도 정확도를 다시 계산할 수 있게 한다 */
	private static final Path OUTPUT_PATH = Path.of("src/main/resources/ground-truth/package_01.json");

	private final GroundTruthGenerator generator = new GroundTruthGenerator();

	/**
	 * package_01 정답지를 만들어 반환한다. 각 테스트가 공통으로 쓰는 준비 과정이다.
	 *
	 * @return 생성된 정답지
	 * @throws Exception PDF를 읽지 못한 경우
	 */
	private GroundTruth generatePackage01() throws Exception {
		Path shuffledPdf = TestMaterials.package01Shuffled();
		return generator.generate("package_01", shuffledPdf, TestMaterials.originalsOf(shuffledPdf));
	}

	/**
	 * 39페이지 전부가 원본과 짝지어지는지 확인한다.
	 * 한 장이라도 남으면 셔플본이 원본을 그대로 재배열한 것이라는 전제가 깨진 것이다.
	 *
	 * @throws Exception PDF를 읽지 못한 경우
	 */
	@Test
	@DisplayName("package_01 39페이지가 원본과 전부 매칭된다 (미매칭 0건)")
	void matchesEveryPage() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");

		GroundTruth groundTruth = generatePackage01();

		assertThat(groundTruth.totalPages()).isEqualTo(39);
		assertThat(groundTruth.unmatchedPages()).isEmpty();
		assertThat(groundTruth.pages()).hasSize(39);
	}

	/**
	 * 라벨별 페이지 수가 원본 문서의 페이지 수와 맞는지 확인한다.
	 * 특히 INCOME_DOC이 단 1장이라 분포가 크게 치우쳐 있고,
	 * 이 때문에 뒤에서 정확도를 전체 평균만으로 보고하면 안 된다는 근거가 된다.
	 *
	 * @throws Exception PDF를 읽지 못한 경우
	 */
	@Test
	@DisplayName("라벨별 페이지 수가 원본 문서 구성과 일치한다")
	void labelCountsMatchOriginalDocuments() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");

		GroundTruth groundTruth = generatePackage01();

		Map<DocumentType, Integer> countByLabel = new HashMap<>();
		for (GroundTruthPage page : groundTruth.pages()) {
			countByLabel.merge(page.label(), 1, Integer::sum);
		}

		assertThat(countByLabel).containsOnly(
				entry(DocumentType.CREDIT_REPORT, 18),
				entry(DocumentType.URLA_1003, 11),
				entry(DocumentType.TITLE_REPORT, 9),
				entry(DocumentType.INCOME_DOC, 1));
	}

	/**
	 * 각 원본 문서의 페이지 번호가 1부터 빠짐없이 한 번씩 나오는지 확인한다.
	 * 원본 페이지가 중복되거나 빠지면 문서 그룹핑 검증의 기준이 무너진다.
	 *
	 * @throws Exception PDF를 읽지 못한 경우
	 */
	@Test
	@DisplayName("문서별 원본 페이지 번호가 1부터 빠짐없이 한 번씩 나온다")
	void sourcePagesAreCompleteForEachDocument() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");

		GroundTruth groundTruth = generatePackage01();

		Map<DocumentType, Integer> pageSumByLabel = new HashMap<>();
		Map<DocumentType, Integer> pageCountByLabel = new HashMap<>();
		for (GroundTruthPage page : groundTruth.pages()) {
			pageSumByLabel.merge(page.label(), page.sourcePage(), Integer::sum);
			pageCountByLabel.merge(page.label(), 1, Integer::sum);
		}

		// 1부터 n까지 한 번씩 나왔다면 합은 n(n+1)/2 이 된다
		for (DocumentType label : pageCountByLabel.keySet()) {
			int pageCount = pageCountByLabel.get(label);
			int expectedSum = pageCount * (pageCount + 1) / 2;
			assertThat(pageSumByLabel.get(label)).as("%s 원본 페이지 번호 합", label).isEqualTo(expectedSum);
		}
	}

	/**
	 * 사전 조사에서 확인해 둔 페이지 몇 개를 표본으로 대조한다.
	 * 전체 건수만 맞고 짝이 서로 뒤바뀌는 경우를 잡기 위한 확인이다.
	 *
	 * @throws Exception PDF를 읽지 못한 경우
	 */
	@Test
	@DisplayName("알려진 표본 페이지의 라벨과 원본 페이지가 일치한다")
	void matchesKnownSamplePages() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");

		GroundTruth groundTruth = generatePackage01();

		assertThat(pageOf(groundTruth, 1)).isEqualTo(new GroundTruthPage(1, DocumentType.CREDIT_REPORT, 8));
		assertThat(pageOf(groundTruth, 6)).isEqualTo(new GroundTruthPage(6, DocumentType.URLA_1003, 1));
		assertThat(pageOf(groundTruth, 32)).isEqualTo(new GroundTruthPage(32, DocumentType.TITLE_REPORT, 1));
		assertThat(pageOf(groundTruth, 36)).isEqualTo(new GroundTruthPage(36, DocumentType.INCOME_DOC, 1));
	}

	/**
	 * 정답지를 JSON으로 저장하고, 저장한 내용이 그대로 다시 읽히는지 확인한다.
	 * 이 테스트가 저장소에 커밋할 정답지 파일을 만들어 내는 역할도 한다.
	 *
	 * @throws Exception PDF를 읽지 못하거나 파일을 쓰지 못한 경우
	 */
	@Test
	@DisplayName("정답지를 JSON 파일로 저장하고 다시 읽어들일 수 있다")
	void savesGroundTruthAsJson() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");

		GroundTruth groundTruth = generatePackage01();
		generator.save(groundTruth, OUTPUT_PATH);

		assertThat(Files.exists(OUTPUT_PATH)).isTrue();

		GroundTruth reloaded = JsonMapper.builder().build().readValue(OUTPUT_PATH.toFile(), GroundTruth.class);
		assertThat(reloaded).isEqualTo(groundTruth);
	}

	/**
	 * 정답지에서 특정 페이지 항목을 찾는다.
	 *
	 * @param groundTruth 대상 정답지
	 * @param pageNumber  찾을 셔플 페이지 번호
	 * @return 해당 페이지 항목. 없으면 null
	 */
	private GroundTruthPage pageOf(GroundTruth groundTruth, int pageNumber) {
		for (GroundTruthPage page : groundTruth.pages()) {
			if (page.page() == pageNumber) {
				return page;
			}
		}
		return null;
	}
}
