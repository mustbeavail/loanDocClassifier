package com.codingtest.docclassifier;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * 테스트에서 쓰는 제공 자료 PDF의 위치를 찾아 준다.
 *
 * <p>제공 자료는 외부 공유가 금지되어 저장소에 넣을 수 없다.
 * 그래서 자료 폴더 경로를 실행 옵션으로 받고, 폴더 안에서 파일을 이름 규칙으로 찾는다.
 * 예) {@code mvnw test -DtestPdfDir="F:/.../테스트 제공자료"}
 *
 * <p>옵션이 없으면 관련 테스트는 실패가 아니라 건너뛰기로 처리한다.
 * 자료 없이 저장소만 받은 사람도 나머지 테스트는 그대로 돌릴 수 있어야 하기 때문이다.
 */
public final class TestMaterials {

	/** 제공 자료 폴더. 실행 옵션 -DtestPdfDir 로 받는다 */
	private static final Path MATERIALS_DIRECTORY = Path.of(System.getProperty("testPdfDir", ""));

	/** 페이지가 섞인 PDF의 파일명 끝 부분 */
	private static final String SHUFFLED_SUFFIX = "_shuffled.pdf";

	private TestMaterials() {
	}

	/**
	 * 제공 자료 폴더를 쓸 수 있는 상태인지 확인한다.
	 *
	 * @return 폴더 경로가 주어졌고 실제로 존재하면 true
	 */
	public static boolean isAvailable() {
		return !MATERIALS_DIRECTORY.toString().isBlank() && Files.isDirectory(MATERIALS_DIRECTORY);
	}

	/**
	 * 자료 폴더 안의 셔플 PDF를 파일명 순으로 모두 찾는다.
	 *
	 * <p>파일명이 01·02로 시작해 정렬하면 package_01, package_02 순이 된다.
	 * 파일명을 코드에 그대로 적지 않는 이유는 이름에 대출 번호가 들어 있어서다.
	 *
	 * @return 셔플 PDF 경로 목록 (파일명 오름차순)
	 * @throws IOException 폴더를 훑지 못한 경우
	 */
	public static List<Path> shuffledPdfs() throws IOException {
		List<Path> found = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(MATERIALS_DIRECTORY)) {
			for (Path path : paths.toList()) {
				if (path.getFileName().toString().endsWith(SHUFFLED_SUFFIX)) {
					found.add(path);
				}
			}
		}
		Collections.sort(found);
		return found;
	}

	/**
	 * package_01의 셔플 PDF를 찾는다.
	 *
	 * @return 39페이지짜리 셔플 PDF 경로
	 * @throws IOException 폴더를 훑지 못한 경우
	 */
	public static Path package01Shuffled() throws IOException {
		return shuffledPdfs().get(0);
	}

	/**
	 * package_02의 셔플 PDF를 찾는다.
	 *
	 * @return 44페이지짜리 셔플 PDF 경로
	 * @throws IOException 폴더를 훑지 못한 경우
	 */
	public static Path package02Shuffled() throws IOException {
		return shuffledPdfs().get(1);
	}

	/**
	 * 셔플 PDF와 같은 폴더에 있는 원본 문서 PDF들을 찾는다.
	 *
	 * @param shuffledPdf 기준이 되는 셔플 PDF
	 * @return 같은 폴더의 PDF 중 셔플본을 뺀 나머지 (파일명 오름차순)
	 * @throws IOException 폴더를 훑지 못한 경우
	 */
	public static List<Path> originalsOf(Path shuffledPdf) throws IOException {
		List<Path> originals = new ArrayList<>();
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(shuffledPdf.getParent(), "*.pdf")) {
			for (Path entry : entries) {
				if (!entry.equals(shuffledPdf)) {
					originals.add(entry);
				}
			}
		}
		Collections.sort(originals);
		return originals;
	}
}
