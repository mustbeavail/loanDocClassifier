package com.codingtest.docclassifier.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.codingtest.docclassifier.TestMaterials;
import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.classify.RuleClassifier;
import com.codingtest.docclassifier.pdf.PdfPage;
import com.codingtest.docclassifier.pdf.PdfPageReader;

/**
 * {@link OcrFallback}이 스캔 페이지를 실제로 읽어내는지 확인한다.
 *
 * <p>대상은 package_02의 11·26·40페이지다. 셋 다 텍스트 레이어가 없고 회전되어 있어,
 * 방향을 잡지 못하면 글자가 깨져 나온다. 읽은 결과가 규칙 분류까지 통과하는지도 함께 본다.
 */
class OcrFallbackTest {

	private final OcrFallback ocrFallback = new OcrFallback();
	private final PdfPageReader pageReader = new PdfPageReader();
	private final RuleClassifier classifier = new RuleClassifier();

	private final PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);

	/** 텍스트 레이어가 없는 스캔 페이지들 */
	private static final int[] SCANNED_PAGES = { 11, 26, 40 };

	@Test
	@DisplayName("회전된 스캔 페이지 3장에서 권원보고서 문구를 읽어낸다")
	void readsRotatedScannedPages() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");
		Path pdfPath = TestMaterials.package02Shuffled();

		long startedAt = System.currentTimeMillis();
		for (int pageNumber : SCANNED_PAGES) {
			String text = ocrFallback.readText(pdfPath, pageNumber);

			assertThat(text.toUpperCase())
					.as("%d페이지 OCR 결과", pageNumber)
					.contains("COMMITMENT CONDITIONS");
		}
		out.printf("스캔 3장 OCR 소요 %.1f초%n", (System.currentTimeMillis() - startedAt) / 1000.0);
	}

	@Test
	@DisplayName("OCR 결과를 규칙 분류에 넣으면 권원보고서로 판정된다")
	void ocrTextPassesRuleClassification() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");

		String text = ocrFallback.readText(TestMaterials.package02Shuffled(), 11);
		PdfPage recoveredPage = new PdfPage(11, text, 1, 612, 792);

		assertThat(classifier.classify(recoveredPage).label()).isEqualTo(DocumentType.TITLE_REPORT);
	}

	@Test
	@DisplayName("OCR 대상 페이지는 텍스트 레이어가 비어 있어 OCR 없이는 판정할 수 없다")
	void scannedPagesHaveNoTextLayer() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");

		var pages = pageReader.read(TestMaterials.package02Shuffled());
		for (int pageNumber : SCANNED_PAGES) {
			PdfPage page = pages.get(pageNumber - 1);

			assertThat(page.text()).as("%d페이지 텍스트 레이어", pageNumber).isBlank();
			assertThat(classifier.classify(page).label()).isEqualTo(DocumentType.UNDECIDED);
		}
	}

	@Test
	@DisplayName("읽을 수 없는 페이지는 예외 대신 빈 문자열을 돌려준다")
	void returnsEmptyTextWhenPageCannotBeRead() throws Exception {
		Assumptions.assumeTrue(TestMaterials.isAvailable(), "제공 자료 폴더가 없어 건너뜁니다");

		String text = ocrFallback.readText(TestMaterials.package02Shuffled(), 999);

		assertThat(text).isEmpty();
	}

	@Test
	@DisplayName("PDF 파일이 없어도 예외 없이 빈 문자열을 돌려준다")
	void returnsEmptyTextWhenFileIsMissing() {
		String text = ocrFallback.readText(Path.of("없는파일.pdf"), 1);

		assertThat(text).isEmpty();
	}
}
