package com.codingtest.docclassifier.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.codingtest.docclassifier.TestMaterials;

/**
 * {@link PdfPageReader}가 제공 자료를 실제로 제대로 읽는지 확인한다.
 *
 * <p>단순히 예외 없이 돌아가는지가 아니라, 사전 조사에서 파악해 둔 자료의 성질
 * (페이지 수, 텍스트 레이어 유무, 스캔 페이지 위치)과 일치하는지를 본다.
 * 이 성질이 뒤 단계 설계의 전제이므로, 어긋나면 여기서 먼저 드러나야 한다.
 */
class PdfPageReaderTest {

	private final PdfPageReader pageReader = new PdfPageReader();

	/**
	 * 두 패키지의 페이지 수가 알려진 값과 같은지 확인한다.
	 * 페이지를 하나라도 빠뜨리거나 겹쳐 읽으면 뒤의 정확도 수치가 전부 어긋난다.
	 *
	 * @throws Exception PDF를 읽지 못한 경우
	 */
	@Test
	@DisplayName("셔플 PDF의 페이지 수를 그대로 읽어낸다 (39p / 44p)")
	void readsAllPages() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");

		List<PdfPage> package01 = pageReader.read(TestMaterials.package01Shuffled());
		List<PdfPage> package02 = pageReader.read(TestMaterials.package02Shuffled());

		assertThat(package01).hasSize(39);
		assertThat(package02).hasSize(44);
	}

	/**
	 * 페이지 번호가 1부터 순서대로 붙는지 확인한다.
	 * 정답지와 분류 결과를 페이지 번호로 맞추므로 순서가 곧 신뢰의 기준이 된다.
	 *
	 * @throws Exception PDF를 읽지 못한 경우
	 */
	@Test
	@DisplayName("페이지 번호를 1부터 순서대로 매긴다")
	void numbersPagesFromOne() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");

		List<PdfPage> pages = pageReader.read(TestMaterials.package01Shuffled());

		for (int index = 0; index < pages.size(); index++) {
			assertThat(pages.get(index).pageNumber()).isEqualTo(index + 1);
		}
	}

	/**
	 * package_01은 모든 페이지에 텍스트 레이어가 있는지 확인한다.
	 * 이 패키지에 OCR이 필요 없다는 전제를 검증하는 것이다.
	 *
	 * @throws Exception PDF를 읽지 못한 경우
	 */
	@Test
	@DisplayName("package_01은 모든 페이지에서 텍스트가 나온다")
	void package01HasTextOnEveryPage() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");

		List<PdfPage> pages = pageReader.read(TestMaterials.package01Shuffled());

		for (PdfPage page : pages) {
			assertThat(page.text()).as("%d페이지 텍스트", page.pageNumber()).isNotBlank();
		}
	}

	/**
	 * package_02에서 텍스트가 비어 있는 페이지가 11·26·40 세 장뿐인지 확인한다.
	 * 이 세 장이 곧 OCR 대상이며, 목록이 달라지면 OCR 폴백 설계를 다시 봐야 한다.
	 *
	 * @throws Exception PDF를 읽지 못한 경우
	 */
	@Test
	@DisplayName("package_02의 스캔 페이지 3장만 텍스트가 비어 있고 이미지를 갖고 있다")
	void package02HasThreeScannedPages() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");

		List<PdfPage> pages = pageReader.read(TestMaterials.package02Shuffled());

		List<Integer> pagesWithoutText = new ArrayList<>();
		for (PdfPage page : pages) {
			if (page.text().isBlank()) {
				pagesWithoutText.add(page.pageNumber());
				// 텍스트도 이미지도 없다면 스캔이 아니라 빈 페이지라는 뜻이므로 구분해서 확인한다
				assertThat(page.imageCount()).as("%d페이지 이미지 수", page.pageNumber()).isPositive();
			}
		}

		assertThat(pagesWithoutText).containsExactly(11, 26, 40);
	}

	/**
	 * 페이지 크기를 읽어 오는지 확인한다.
	 * 좌표가 뒤집힌 페이지가 실제로 있어 높이가 음수로 나올 수 있는데, 절댓값 처리가 되어 있어야 한다.
	 *
	 * @throws Exception PDF를 읽지 못한 경우
	 */
	@Test
	@DisplayName("좌표가 뒤집힌 페이지가 있어도 페이지 크기는 양수로 읽는다")
	void readsPageSizeAsPositiveValue() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");

		List<PdfPage> pages = pageReader.read(TestMaterials.package01Shuffled());

		for (PdfPage page : pages) {
			assertThat(page.width()).as("%d페이지 가로", page.pageNumber()).isPositive();
			assertThat(page.height()).as("%d페이지 세로", page.pageNumber()).isPositive();
		}
	}

	/**
	 * 공백 정규화가 줄바꿈·탭·연속 공백·앞뒤 공백을 모두 한 칸으로 정리하는지 확인한다.
	 * 정답지 매칭이 이 정규화 결과의 일치 여부로 이뤄지므로 동작이 정확해야 한다.
	 */
	@Test
	@DisplayName("공백 정규화는 줄바꿈·탭·연속 공백을 한 칸으로 만든다")
	void normalizesWhitespace() {
		assertThat(PdfPageReader.normalizeWhitespace("  Form   1003\n\n  Page 1 \tof 9  "))
				.isEqualTo("Form 1003 Page 1 of 9");
		assertThat(PdfPageReader.normalizeWhitespace("")).isEmpty();
		assertThat(PdfPageReader.normalizeWhitespace(" \n\t ")).isEmpty();
	}

	/**
	 * 줄바꿈 위치만 다른 같은 내용이 정규화 후 같은 문자열이 되는지 확인한다.
	 * 정답지 생성이 성립하는 근거가 바로 이 성질이다.
	 */
	@Test
	@DisplayName("줄바꿈만 다른 같은 내용은 정규화하면 같아진다")
	void normalizedTextIgnoresLineBreaks() {
		String withLineBreak = "ALTA Commitment\nfor Title Insurance";
		String withSpace = "ALTA Commitment for Title Insurance";

		assertThat(PdfPageReader.normalizeWhitespace(withLineBreak))
				.isEqualTo(PdfPageReader.normalizeWhitespace(withSpace));
	}
}
