package com.codingtest.docclassifier;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import net.sourceforge.tess4j.util.LoadLibs;

/**
 * STEP 0 사전 검증용 임시 테스트.
 *
 * <p>확인하려는 것.
 * <ul>
 *   <li>Tesseract를 따로 설치하지 않고 Maven 의존성만으로 OCR이 되는가</li>
 *   <li>회전된 스캔 페이지를 어떻게 바로 세울 것인가</li>
 * </ul>
 * 1차 실행에서 180도 뒤집힌 페이지가 그대로 읽혀 글자가 깨지는 것을 확인했다.
 * 그래서 0/90/180/270도로 돌려 가며 OCR한 뒤 인식 신뢰도가 가장 높은 방향을 고르는 방식을 검증한다.
 *
 * <p>검증이 끝나면 이 클래스는 삭제하고 정식 OCR 클래스로 대체한다.
 */
class Tess4jVerificationTest {

	/**
	 * 검증에 쓸 스캔 PDF.
	 * 제공 자료는 외부 공유 금지라 저장소에 넣지 않으므로, 경로를 실행 옵션으로 받는다.
	 * 예) mvn test -DtestPdfDir="C:/자료가/있는/폴더"
	 */
	private static final Path SCANNED_PDF =
			Path.of(System.getProperty("testPdfDir", "")).resolve("02.990367284_shuffled.pdf");

	/** 텍스트 레이어가 없는 스캔 페이지들. 11p는 180도, 26p·40p는 90도 회전 상태 */
	private static final int[] SCANNED_PAGES = {11, 26, 40};

	/** 시도할 회전 각도 */
	private static final int[] ROTATIONS = {0, 90, 180, 270};

	/**
	 * 스캔 페이지 3장을 4방향으로 돌려 가며 OCR하고, 방향별 신뢰도를 출력한다.
	 * 신뢰도가 가장 높은 방향의 글자가 제대로 읽히면 검증 성공이다.
	 *
	 * @throws Exception PDF 로딩·렌더링·OCR 과정에서 발생할 수 있는 예외
	 */
	@Test
	@DisplayName("회전된 스캔 페이지를 4방향 시도로 바로 세워 읽어낸다")
	void ocr_findsCorrectRotation() throws Exception {
		// 제공 자료가 없는 환경에서는 건너뛴다
		Assumptions.assumeTrue(Files.exists(SCANNED_PDF), "검증용 PDF를 찾을 수 없어 건너뜁니다");

		ITesseract tesseract = createTesseract();

		for (int pageNumber : SCANNED_PAGES) {
			BufferedImage original = renderPage(SCANNED_PDF, pageNumber);

			int bestRotation = 0;
			double bestConfidence = -1;
			String bestText = "";

			for (int rotation : ROTATIONS) {
				BufferedImage rotated = rotate(original, rotation);
				String text = tesseract.doOCR(rotated);
				double confidence = averageConfidence(tesseract, rotated);

				System.out.printf("  p%d / %3d도 → 신뢰도 %.1f, %d자%n",
						pageNumber, rotation, confidence, text.length());

				if (confidence > bestConfidence) {
					bestConfidence = confidence;
					bestRotation = rotation;
					bestText = text;
				}
			}

			System.out.printf("== p%d 최적: %d도 (신뢰도 %.1f) ==%n", pageNumber, bestRotation, bestConfidence);
			System.out.println(bestText.substring(0, Math.min(300, bestText.length())).replaceAll("\\s+", " "));
			System.out.println();
		}
	}

	/**
	 * 영어 인식용 Tesseract 인스턴스를 만든다.
	 * tessdata(학습 데이터)는 Tess4J가 jar 안에 담아 배포하는 것을 임시 폴더로 꺼내 쓴다.
	 *
	 * @return 설정이 끝난 Tesseract 인스턴스
	 */
	private ITesseract createTesseract() {
		File tessDataFolder = LoadLibs.extractTessResources("tessdata");
		ITesseract tesseract = new Tesseract();
		tesseract.setDatapath(tessDataFolder.getPath());
		tesseract.setLanguage("eng");
		return tesseract;
	}

	/**
	 * 인식된 단어들의 평균 신뢰도를 구한다. 방향이 맞으면 높고 뒤집혀 있으면 낮아진다.
	 *
	 * @param tesseract OCR 인스턴스
	 * @param image     대상 이미지
	 * @return 0~100 사이의 평균 신뢰도. 인식된 단어가 없으면 0
	 */
	private double averageConfidence(ITesseract tesseract, BufferedImage image) {
		List<Word> words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
		if (words.isEmpty()) {
			return 0;
		}
		return words.stream().mapToDouble(Word::getConfidence).average().orElse(0);
	}

	/**
	 * 이미지를 시계 방향으로 회전한다.
	 *
	 * @param source  원본 이미지
	 * @param degrees 0, 90, 180, 270 중 하나
	 * @return 회전된 이미지 (0도면 원본 그대로)
	 */
	private BufferedImage rotate(BufferedImage source, int degrees) {
		if (degrees == 0) {
			return source;
		}

		int width = source.getWidth();
		int height = source.getHeight();
		// 90도나 270도로 돌리면 가로·세로가 바뀐다
		boolean swapped = (degrees == 90 || degrees == 270);
		BufferedImage target = new BufferedImage(
				swapped ? height : width,
				swapped ? width : height,
				source.getType());

		AffineTransform transform = new AffineTransform();
		transform.translate(target.getWidth() / 2.0, target.getHeight() / 2.0);
		transform.rotate(Math.toRadians(degrees));
		transform.translate(-width / 2.0, -height / 2.0);

		new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR).filter(source, target);
		return target;
	}

	/**
	 * PDF의 특정 페이지를 이미지로 렌더링한다.
	 *
	 * @param pdfPath    대상 PDF 경로
	 * @param pageNumber 렌더링할 페이지 번호 (1부터 셈)
	 * @return 렌더링된 이미지 (OCR 정확도를 위해 300 DPI 사용)
	 * @throws Exception PDF를 열거나 렌더링하지 못한 경우
	 */
	private BufferedImage renderPage(Path pdfPath, int pageNumber) throws Exception {
		try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
			PDFRenderer renderer = new PDFRenderer(document);
			return renderer.renderImageWithDPI(pageNumber - 1, 300);
		}
	}
}
