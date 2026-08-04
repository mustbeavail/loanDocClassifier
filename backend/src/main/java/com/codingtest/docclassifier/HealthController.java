package com.codingtest.docclassifier;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버가 살아 있는지 확인하는 용도의 임시 컨트롤러.
 *
 * <p>보일러플레이트 단계에서 백엔드-프론트엔드 연결이 되는지 확인하려고 만들었다.
 * 실제 분류 API가 붙으면 지워도 된다.
 */
@RestController
public class HealthController {

	/**
	 * 서버 상태를 반환한다.
	 *
	 * @return {@code {"status": "ok"}} 형태의 JSON
	 *         (Map을 리턴하면 스프링이 자동으로 JSON으로 바꿔 준다)
	 */
	@GetMapping("/api/health")
	public Map<String, String> health() {
		return Map.of("status", "ok");
	}
}
