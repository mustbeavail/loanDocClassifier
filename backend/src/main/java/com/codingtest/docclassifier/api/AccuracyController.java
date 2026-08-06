package com.codingtest.docclassifier.api;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codingtest.docclassifier.accuracy.AccuracyResult;

import tools.jackson.databind.json.JsonMapper;

/**
 * package_01 정확도 측정 결과를 돌려주는 API.
 *
 * <p>요청받을 때마다 다시 측정하지 않고 저장소에 커밋된 결과 파일을 읽어 준다.
 * 다시 측정하려면 제공 자료 PDF와 Gemini API 키가 모두 있어야 하는데,
 * 저장소만 받은 사람에게는 둘 다 없어 정확도 화면이 통째로 비어 버린다.
 * 측정 자체는 {@code RuleAndLlmAccuracyTest}가 실제 자료로 수행하고, 그 산출물이 이 파일이다.
 *
 * <p>자료와 키가 있는 사람은 {@code POST /api/classify} 에 정답지를 지정해 그 자리에서 실측하면 된다.
 * 이 API는 그게 안 되는 사람 몫이다.
 */
@RestController
public class AccuracyController {

	/** 커밋된 측정 결과 파일의 클래스패스 경로 */
	private static final String RESULT_FILE = "/results/package_01-accuracy.json";

	private final JsonMapper json = JsonMapper.builder().build();

	/**
	 * 저장된 정확도 측정 결과를 돌려준다.
	 *
	 * @return 측정 결과. 아직 측정한 적이 없으면 404와 안내 메시지
	 * @throws IOException 결과 파일을 읽지 못한 경우
	 */
	@GetMapping("/api/accuracy")
	public ResponseEntity<Object> accuracy() throws IOException {
		try (InputStream stored = getClass().getResourceAsStream(RESULT_FILE)) {
			if (stored == null) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(new ErrorResponse("저장된 정확도 측정 결과가 없습니다."));
			}
			return ResponseEntity.ok(json.readValue(stored, AccuracyResult.class));
		}
	}
}
