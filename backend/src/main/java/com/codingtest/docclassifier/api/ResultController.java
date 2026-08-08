package com.codingtest.docclassifier.api;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.codingtest.docclassifier.pipeline.ClassificationResult;

import tools.jackson.databind.json.JsonMapper;

/**
 * 저장소에 커밋된 분류·그룹핑 결과를 돌려주는 API.
 *
 * <p>제공 자료 PDF도 Gemini 키도 없는 사람이 결과를 보려면 미리 산출해 둔 파일이 있어야 한다.
 * 산출은 테스트가 하고({@code Package01ResultTest}, {@code Package02ResultTest})
 * 이 API는 그 파일을 읽기만 한다. 그래서 두 결과가 언제나 실제 실행에서 나온 값이다.
 *
 * <p>정확도는 {@code GET /api/accuracy}가 따로 돌려준다.
 * 그쪽은 정답지와의 대조표까지 담고 있어 오답 분석용이고, 이쪽은 분류·그룹핑 결과다.
 */
@RestController
public class ResultController {

	/** 결과 파일들이 놓인 클래스패스 경로 */
	private static final String RESULT_DIRECTORY = "/results/";

	private final JsonMapper json = JsonMapper.builder().build();

	/**
	 * 저장된 분류·그룹핑 결과를 돌려준다.
	 *
	 * @param packageId 패키지 이름 (예: package_01)
	 * @return 분류·그룹핑 결과. 이름이 올바르지 않으면 400, 그런 결과가 없으면 404
	 * @throws IOException 결과 파일을 읽지 못한 경우
	 */
	@GetMapping("/api/results/{packageId}")
	public ResponseEntity<Object> result(@PathVariable String packageId) throws IOException {
		// 이름을 그대로 클래스패스 경로에 넣으므로 영문·숫자·밑줄·붙임표만 허용한다
		if (!packageId.matches("[A-Za-z0-9_-]+")) {
			return ResponseEntity.badRequest()
					.body(new ErrorResponse("결과 이름에 쓸 수 없는 문자가 있습니다."));
		}

		try (InputStream stored = getClass().getResourceAsStream(RESULT_DIRECTORY + packageId + ".json")) {
			if (stored == null) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(new ErrorResponse("저장된 결과가 없습니다: " + packageId));
			}
			return ResponseEntity.ok(json.readValue(stored, ClassificationResult.class));
		}
	}
}
