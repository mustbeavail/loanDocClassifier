package com.codingtest.docclassifier.ocr;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import net.sourceforge.tess4j.util.LoadLibs;

/**
 * 텍스트 레이어가 없는 스캔 페이지를 이미지로 렌더링해 글자로 바꾼다.
 *
 * <p>텍스트가 있는 페이지에는 쓰지 않는다. 제공 자료 83페이지 중 대상은 3장뿐이라
 * 전 페이지에 OCR을 돌리는 것은 시간 낭비다. 호출 여부는 부르는 쪽에서 판단한다.
 *
 * <p>스캔 페이지가 뒤집혀 있으면 글자가 깨져 읽히므로 방향부터 잡는다.
 * PDF에 적힌 회전값(/Rotate)은 쓰지 않는다. 제공 자료에서는 PDFBox가 그 값을 이미 적용한 뒤에도
 * 추가 회전이 필요했고, 실무에서는 종이를 뒤집어 스캔해 이미지 자체가 돌아가 있으면서
 * /Rotate는 0인 경우가 흔하다. 어느 쪽이든 이미지를 직접 보고 판단하는 편이 맞다.
 */
@Component
public class OcrFallback {

	private static final Logger log = LoggerFactory.getLogger(OcrFallback.class);

	/** 방향만 가릴 때 쓰는 해상도. 낮게 잡아도 방향은 충분히 구분된다 */
	private static final int ORIENTATION_DPI = 100;

	/** 글자를 실제로 읽을 때 쓰는 해상도 */
	private static final int TEXT_DPI = 300;

	/** 시도할 회전 각도. 실무에서 흔한 순서(정상 → 뒤집힘 → 옆으로)대로 본다 */
	private static final int[] ROTATIONS = { 0, 180, 90, 270 };

	/** 이 신뢰도를 넘으면 남은 각도는 보지 않는다. 실측상 맞는 방향은 90점대, 틀린 방향은 30~40점대다 */
	private static final double CONFIDENT_ENOUGH = 80;

	private final ITesseract tesseract = createTesseract();

	/**
	 * 스캔 페이지 한 장을 읽는다.
	 *
	 * @param pdfPath    대상 PDF 경로
	 * @param pageNumber 읽을 페이지 번호 (1부터 셈)
	 * @return 인식한 텍스트. 읽지 못하면 빈 문자열
	 */
	public String readText(Path pdfPath, int pageNumber) {
		try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
			PDFRenderer renderer = new PDFRenderer(document);

			int rotation = detectRotation(renderer, pageNumber);
			BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, TEXT_DPI);
			return tesseract.doOCR(rotate(image, rotation));

		} catch (Exception exception) {
			// 한 페이지를 못 읽었다고 전체 처리를 멈추지 않는다. 그 페이지는 미판정으로 흘러간다
			log.warn("{} {}페이지 OCR 실패: {}", pdfPath.getFileName(), pageNumber, exception.toString());
			return "";
		}
	}

	/**
	 * 페이지를 네 방향으로 돌려 보고 글자가 가장 잘 읽히는 방향을 고른다.
	 *
	 * <p>낮은 해상도로 방향만 먼저 가린다. 네 방향 모두 고해상도로 읽으면 3장에 85초가 걸렸는데,
	 * 방향 판별에는 그만한 해상도가 필요 없다.
	 *
	 * @param renderer   페이지를 이미지로 그리는 렌더러
	 * @param pageNumber 대상 페이지 번호 (1부터 셈)
	 * @return 채택한 회전 각도
	 * @throws Exception 렌더링에 실패한 경우
	 */
	private int detectRotation(PDFRenderer renderer, int pageNumber) throws Exception {
		BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, ORIENTATION_DPI);

		int bestRotation = 0;
		double bestConfidence = -1;

		for (int rotation : ROTATIONS) {
			double confidence = confidenceOf(rotate(image, rotation));
			if (confidence > bestConfidence) {
				bestConfidence = confidence;
				bestRotation = rotation;
			}
			// 확실히 잘 읽히는 방향을 찾았으면 나머지는 볼 필요가 없다
			if (bestConfidence >= CONFIDENT_ENOUGH) {
				break;
			}
		}

		log.info("{}페이지 회전 {}도 채택 (신뢰도 {})", pageNumber, bestRotation, Math.round(bestConfidence));
		return bestRotation;
	}

	/**
	 * 이미지에서 인식된 단어들의 평균 신뢰도를 구한다. 방향이 맞으면 높고 뒤집혀 있으면 낮다.
	 *
	 * @param image 대상 이미지
	 * @return 0~100 사이의 평균 신뢰도. 인식된 단어가 없으면 0
	 */
	private double confidenceOf(BufferedImage image) {
		List<Word> words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_WORD);
		if (words.isEmpty()) {
			return 0;
		}

		double total = 0;
		for (Word word : words) {
			total += word.getConfidence();
		}
		return total / words.size();
	}

	/**
	 * 이미지를 시계 방향으로 회전한다.
	 *
	 * @param source  원본 이미지
	 * @param degrees 0, 90, 180, 270 중 하나
	 * @return 회전된 이미지. 0도면 원본 그대로
	 */
	private BufferedImage rotate(BufferedImage source, int degrees) {
		if (degrees == 0) {
			return source;
		}

		int width = source.getWidth();
		int height = source.getHeight();
		// 90도나 270도로 돌리면 가로와 세로가 뒤바뀐다
		boolean sideways = (degrees == 90 || degrees == 270);
		BufferedImage target = new BufferedImage(
				sideways ? height : width,
				sideways ? width : height,
				source.getType());

		AffineTransform transform = new AffineTransform();
		transform.translate(target.getWidth() / 2.0, target.getHeight() / 2.0);
		transform.rotate(Math.toRadians(degrees));
		transform.translate(-width / 2.0, -height / 2.0);

		new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR).filter(source, target);
		return target;
	}

	/**
	 * 영어 인식용 Tesseract를 준비한다.
	 * 학습 데이터는 Tess4J가 jar 안에 담아 배포하는 것을 임시 폴더로 꺼내 쓰므로 별도 설치가 필요 없다.
	 *
	 * @return 설정이 끝난 Tesseract
	 */
	private ITesseract createTesseract() {
		File tessDataFolder = LoadLibs.extractTessResources("tessdata");
		ITesseract instance = new Tesseract();
		instance.setDatapath(tessDataFolder.getPath());
		instance.setLanguage("eng");
		return instance;
	}
}
