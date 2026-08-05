package com.codingtest.docclassifier.groundtruth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.groundtruth.GroundTruth.GroundTruthPage;
import com.codingtest.docclassifier.pdf.PdfPage;
import com.codingtest.docclassifier.pdf.PdfPageReader;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 원본 문서와 셔플 PDF를 대조해 정답지를 만든다.
 *
 * <p>셔플 PDF는 원본 문서들의 페이지를 그대로 섞어 놓은 것이다.
 * 그래서 원본 페이지의 텍스트와 셔플 페이지의 텍스트를 맞춰 보면
 * "셔플 3페이지 = 신용보고서 18페이지"처럼 정답을 기계적으로 복원할 수 있다.
 * 사람이 39장을 눈으로 라벨링하는 것보다 빠르고, 실수가 없으며, 재현된다.
 *
 * <p>비교 키로 해시 대신 <b>공백을 정규화한 텍스트 자체</b>를 쓴다.
 * 페이지가 40장 남짓이라 메모리 부담이 없고, 해시 충돌을 걱정할 필요도
 * 해시 알고리즘 예외를 처리할 필요도 없어 코드가 단순해지기 때문이다.
 *
 * <p>텍스트는 {@link PdfPageReader#readForExactComparison(Path)}으로 읽는다.
 * 겹쳐 그린 글자까지 그대로 읽어야 원본과 셔플본의 텍스트가 정확히 같아진다.
 */
public class GroundTruthGenerator {

	private final PdfPageReader pageReader = new PdfPageReader();

	/**
	 * 셔플 PDF의 각 페이지가 어느 원본 문서의 몇 페이지였는지 찾아 정답지를 만든다.
	 *
	 * @param packageId    정답지에 기록할 패키지 이름 (예: package_01)
	 * @param shuffledPdf  페이지가 섞인 PDF 경로
	 * @param originalPdfs 섞기 전 원본 문서 PDF 경로들
	 * @return 페이지별 정답과, 짝을 찾지 못한 페이지 목록
	 * @throws IOException PDF를 읽지 못한 경우
	 */
	public GroundTruth generate(String packageId, Path shuffledPdf, List<Path> originalPdfs) throws IOException {
		Map<String, List<SourcePage>> originalPagesByText = indexOriginalPages(originalPdfs);

		List<PdfPage> shuffledPages = pageReader.readForExactComparison(shuffledPdf);
		List<GroundTruthPage> matched = new ArrayList<>();
		List<Integer> unmatched = new ArrayList<>();

		for (PdfPage shuffledPage : shuffledPages) {
			String key = PdfPageReader.normalizeWhitespace(shuffledPage.text());
			List<SourcePage> sources = originalPagesByText.getOrDefault(key, List.of());

			// 후보가 정확히 하나일 때만 정답으로 삼는다.
			// 0개면 원본에 없는 페이지이고, 2개 이상이면 내용이 똑같은 원본 페이지가 여럿이라 어느 쪽인지 특정할 수 없다
			if (sources.size() == 1) {
				SourcePage source = sources.get(0);
				matched.add(new GroundTruthPage(shuffledPage.pageNumber(), source.label(), source.pageNumber()));
			} else {
				unmatched.add(shuffledPage.pageNumber());
			}
		}

		return new GroundTruth(packageId, shuffledPages.size(), matched, unmatched);
	}

	/**
	 * 정답지를 JSON 파일로 저장한다.
	 *
	 * <p>원본 파일명에는 대출 번호가 들어 있어 저장 대상에서 제외했다.
	 * 정확도 측정에 필요한 것은 라벨과 페이지 번호뿐이다.
	 *
	 * @param groundTruth 저장할 정답지
	 * @param outputPath  저장 경로. 상위 폴더가 없으면 만든다
	 * @throws IOException 파일을 쓰지 못한 경우
	 */
	public void save(GroundTruth groundTruth, Path outputPath) throws IOException {
		Files.createDirectories(outputPath.getParent());
		JsonMapper jsonMapper = JsonMapper.builder()
				.enable(SerializationFeature.INDENT_OUTPUT)
				.build();
		jsonMapper.writeValue(outputPath.toFile(), groundTruth);
	}

	/**
	 * 원본 문서들의 모든 페이지를 "정규화된 텍스트 → 출처 목록" 형태로 모은다.
	 *
	 * <p>값을 목록으로 두는 이유는 내용이 똑같은 페이지가 여러 장 있을 수 있기 때문이다.
	 * 그런 텍스트는 목록 길이가 2 이상이 되어 매칭 단계에서 걸러진다.
	 *
	 * @param originalPdfs 원본 문서 PDF 경로들
	 * @return 텍스트를 키로 하는 출처 표
	 * @throws IOException PDF를 읽지 못한 경우
	 */
	private Map<String, List<SourcePage>> indexOriginalPages(List<Path> originalPdfs) throws IOException {
		Map<String, List<SourcePage>> index = new HashMap<>();
		for (Path originalPdf : originalPdfs) {
			DocumentType label = documentTypeOf(originalPdf);
			for (PdfPage page : pageReader.readForExactComparison(originalPdf)) {
				String key = PdfPageReader.normalizeWhitespace(page.text());
				index.computeIfAbsent(key, unused -> new ArrayList<>())
						.add(new SourcePage(label, page.pageNumber()));
			}
		}
		return index;
	}

	/**
	 * 원본 파일명을 보고 문서 유형을 정한다.
	 *
	 * <p>제공 자료의 원본 파일명이 유형을 그대로 담고 있어(예: {@code Credit Report_...pdf})
	 * 이름의 키워드만으로 충분하다. 정답지 생성에만 쓰는 규칙이고,
	 * 실제 분류기는 파일명이 아니라 페이지 내용으로 판정한다.
	 *
	 * @param originalPdf 원본 문서 경로
	 * @return 해당 문서의 유형
	 */
	private DocumentType documentTypeOf(Path originalPdf) {
		String fileName = originalPdf.getFileName().toString().toUpperCase();

		if (fileName.contains("URLA") || fileName.contains("1003")) {
			return DocumentType.URLA_1003;
		}
		if (fileName.contains("CREDIT")) {
			return DocumentType.CREDIT_REPORT;
		}
		if (fileName.contains("TITLE")) {
			return DocumentType.TITLE_REPORT;
		}
		if (fileName.contains("INCOME")) {
			return DocumentType.INCOME_DOC;
		}
		throw new IllegalArgumentException("문서 유형을 알 수 없는 원본 파일: " + originalPdf.getFileName());
	}

	/**
	 * 원본 페이지의 출처.
	 *
	 * @param label      원본 문서의 유형
	 * @param pageNumber 원본 문서에서의 페이지 번호
	 */
	private record SourcePage(DocumentType label, int pageNumber) {
	}
}
