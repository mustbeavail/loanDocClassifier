package com.codingtest.docclassifier.pdf;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * PDF를 페이지 단위로 읽어 {@link PdfPage} 목록으로 바꾼다.
 *
 * <p>파이프라인의 첫 단계다. 제공된 자료는 대부분 텍스트 레이어를 갖고 있어
 * 여기서 얻은 텍스트만으로 분류가 되고, 텍스트가 비어 있는 소수의 스캔 페이지만
 * 뒤에서 OCR로 보충한다. 전 페이지를 이미지로 OCR하지 않는 이유가 이것이다.
 */
@Component
public class PdfPageReader {

	/**
	 * PDF의 모든 페이지를 읽는다. 분류 파이프라인이 쓰는 기본 방식이다.
	 *
	 * @param pdfPath 읽을 PDF 경로
	 * @return 1페이지부터 순서대로 담긴 페이지 목록
	 * @throws IOException PDF를 열지 못하거나 읽는 중 실패한 경우
	 */
	public List<PdfPage> read(Path pdfPath) throws IOException {
		return read(pdfPath, true);
	}

	/**
	 * 페이지에 그려진 글자를 하나도 빠뜨리지 않고 읽는다. 원본과 셔플본을 글자 단위로 대조할 때만 쓴다.
	 *
	 * <p>제공 자료에는 굵은 글씨를 흉내내려고 같은 글자를 살짝 어긋나게 두 번 그린 곳이 있다.
	 * PDFBox는 이렇게 겹친 글자를 기본적으로 한 번만 내보내는데, 그 판정이 원본과 셔플본에서
	 * 서로 다르게 나와(어긋난 정도가 달라져) 같은 내용인데도 텍스트가 달라졌다.
	 * 실제로 이 억제를 끄면 39페이지가 모두 정확히 일치하고, 켜 두면 2페이지가 어긋난다.
	 *
	 * <p>반대로 분류에 쓰는 {@link #read(Path)}는 억제를 켜 둔다.
	 * 겹쳐 그린 글자가 그대로 나오면 {@code CCoommmmiittmmeenntt} 처럼 읽혀 키워드 검색이 오히려 어려워지기 때문이다.
	 *
	 * @param pdfPath 읽을 PDF 경로
	 * @return 1페이지부터 순서대로 담긴 페이지 목록
	 * @throws IOException PDF를 열지 못하거나 읽는 중 실패한 경우
	 */
	public List<PdfPage> readForExactComparison(Path pdfPath) throws IOException {
		return read(pdfPath, false);
	}

	/**
	 * PDF를 페이지 단위로 읽는 실제 구현.
	 *
	 * @param pdfPath                 읽을 PDF 경로
	 * @param suppressDuplicatedGlyph 겹쳐 그린 글자를 한 번만 읽을지 여부
	 * @return 1페이지부터 순서대로 담긴 페이지 목록
	 * @throws IOException PDF를 열지 못하거나 읽는 중 실패한 경우
	 */
	private List<PdfPage> read(Path pdfPath, boolean suppressDuplicatedGlyph) throws IOException {
		try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
			// PDFTextStripper는 한 번에 한 페이지 구간만 뽑으므로, 페이지마다 구간을 옮겨 가며 호출한다
			PDFTextStripper textStripper = new PDFTextStripper();
			// 글자를 그린 순서가 아니라 화면상 위치 순서로 읽는다.
			// 제공 자료의 셔플본은 그린 순서가 뒤죽박죽이라, 이 설정 없이는 "U ni fo rm"처럼 낱글자가 흩어져 나온다
			textStripper.setSortByPosition(true);
			textStripper.setSuppressDuplicateOverlappingText(suppressDuplicatedGlyph);
			List<PdfPage> pages = new ArrayList<>();

			for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
				textStripper.setStartPage(pageNumber);
				textStripper.setEndPage(pageNumber);
				String text = textStripper.getText(document);

				PDPage page = document.getPage(pageNumber - 1);
				pages.add(new PdfPage(
						pageNumber,
						text,
						countImages(page),
						pageWidth(page),
						pageHeight(page)));
			}
			return pages;
		}
	}

	/**
	 * 텍스트의 공백을 한 칸으로 정리하고 앞뒤 공백을 없앤다.
	 *
	 * <p>같은 내용의 페이지라도 줄바꿈이나 들여쓰기가 다르게 추출될 수 있어,
	 * 페이지끼리 내용을 비교할 때는 반드시 이 형태로 맞춘 뒤 비교한다.
	 *
	 * @param text 원문 텍스트
	 * @return 공백이 정규화된 텍스트
	 */
	public static String normalizeWhitespace(String text) {
		return text.replaceAll("\\s+", " ").trim();
	}

	/**
	 * 페이지에 들어 있는 이미지 개수를 센다.
	 *
	 * <p>텍스트가 0자이면서 이미지가 있는 페이지가 곧 스캔 페이지다.
	 *
	 * @param page 대상 페이지
	 * @return 이미지 개수
	 */
	private int countImages(PDPage page) {
		PDResources resources = page.getResources();
		int imageCount = 0;
		for (COSName name : resources.getXObjectNames()) {
			if (resources.isImageXObject(name)) {
				imageCount++;
			}
		}
		return imageCount;
	}

	/**
	 * 페이지 가로 크기를 구한다.
	 *
	 * @param page 대상 페이지
	 * @return 가로 크기 (포인트)
	 */
	private float pageWidth(PDPage page) {
		return Math.abs(page.getMediaBox().getWidth());
	}

	/**
	 * 페이지 세로 크기를 구한다.
	 *
	 * <p>절댓값을 취하는 이유: 제공 자료 중 위아래가 뒤집힌 좌표
	 * (예: {@code [0, 946, 612, 0]})를 가진 페이지가 실제로 있어 높이가 음수로 나온다.
	 *
	 * @param page 대상 페이지
	 * @return 세로 크기 (포인트)
	 */
	private float pageHeight(PDPage page) {
		return Math.abs(page.getMediaBox().getHeight());
	}
}
