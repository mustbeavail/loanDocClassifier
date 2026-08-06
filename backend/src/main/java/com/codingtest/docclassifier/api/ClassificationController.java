package com.codingtest.docclassifier.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.codingtest.docclassifier.pipeline.ClassificationResult;
import com.codingtest.docclassifier.pipeline.ClassificationService;

/**
 * PDF를 업로드받아 페이지 분류와 문서 그룹핑 결과를 돌려주는 API.
 */
@RestController
public class ClassificationController {

	private static final Logger log = LoggerFactory.getLogger(ClassificationController.class);

	private final ClassificationService classificationService;

	/**
	 * @param classificationService 업로드된 PDF를 끝까지 처리하는 파이프라인
	 */
	public ClassificationController(ClassificationService classificationService) {
		this.classificationService = classificationService;
	}

	/**
	 * 업로드된 PDF를 분류한다.
	 *
	 * <p>파일을 임시 파일로 내려 두고 처리한다. PDF 파싱과 OCR이 모두 파일 경로를 받는데,
	 * 업로드 내용을 메모리에 통째로 들고 있으면 큰 패키지에서 부담이 되기 때문이다.
	 * 처리가 끝나면 성공·실패와 상관없이 임시 파일을 지운다.
	 *
	 * <p>정답지가 있는 패키지를 올릴 때는 {@code groundTruth}에 그 이름을 적는다.
	 * 그러면 방금 낸 판정을 정답지와 대조한 정확도가 응답에 함께 담긴다.
	 * 저장된 수치를 읽는 {@code GET /api/accuracy}와 달리 이번 업로드로 측정한 값이다.
	 *
	 * @param file        업로드된 PDF (multipart 필드 이름은 {@code file})
	 * @param groundTruth 대조할 정답지 이름 (예: {@code package_01}). 생략하면 채점하지 않는다
	 * @return 페이지별 판정과 문서 그룹핑 결과. 정답지를 지정했다면 정확도까지
	 * @throws IOException 임시 파일을 다루지 못했거나 PDF를 읽지 못한 경우
	 */
	@PostMapping("/api/classify")
	public ClassificationResult classify(
			@RequestParam("file") MultipartFile file,
			@RequestParam(value = "groundTruth", required = false) String groundTruth) throws IOException {

		if (file.isEmpty()) {
			throw new IllegalArgumentException("업로드된 파일이 비어 있습니다.");
		}

		Path uploaded = Files.createTempFile("classify-", ".pdf");
		try {
			file.transferTo(uploaded);
			return classificationService.classify(uploaded, file.getOriginalFilename(), groundTruth);
		} finally {
			Files.deleteIfExists(uploaded);
		}
	}

	/**
	 * 요청 자체가 잘못된 경우를 400으로 돌려준다.
	 *
	 * @param exception 발생한 예외. 메시지를 이미 한글로 적어 둔 것만 여기로 온다
	 * @return 사용자에게 보여 줄 실패 사유
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleInvalidRequest(IllegalArgumentException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	/**
	 * 파일이 실려 오지 않은 요청을 400으로 돌려준다.
	 *
	 * <p>파일 선택 없이 제출하면 {@code file} 항목이 빠지고, 요청을 multipart로 보내지 않으면
	 * 업로드 자체가 성립하지 않는다. 둘 다 요청이 잘못된 것이므로 400으로 답한다.
	 * 손대지 않으면 후자는 500(서버 오류)으로 나가, 고칠 쪽이 서버인 것처럼 보인다.
	 *
	 * @param exception 발생한 예외
	 * @return 사용자에게 보여 줄 실패 사유
	 */
	@ExceptionHandler({ MultipartException.class, MissingServletRequestPartException.class })
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleMissingFile(Exception exception) {
		return new ErrorResponse("PDF 파일을 함께 보내 주세요.");
	}

	/**
	 * PDF를 읽지 못한 경우를 400으로 돌려준다.
	 *
	 * <p>PDF가 아닌 파일을 올리거나 파일이 깨져 있으면 PDFBox가 여기로 예외를 던진다.
	 * 원문 메시지는 영어라 화면에 띄우지 않고 로그에만 남긴다.
	 *
	 * @param exception 발생한 예외
	 * @return 사용자에게 보여 줄 실패 사유
	 */
	@ExceptionHandler(IOException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleUnreadablePdf(IOException exception) {
		log.warn("업로드된 PDF 처리 실패: {}", exception.toString());
		return new ErrorResponse("PDF를 읽지 못했습니다. 올바른 PDF 파일인지 확인해 주세요.");
	}
}
