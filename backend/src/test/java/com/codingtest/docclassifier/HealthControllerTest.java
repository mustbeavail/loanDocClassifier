package com.codingtest.docclassifier;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link HealthController} 테스트.
 *
 * <p>@WebMvcTest는 웹 계층(컨트롤러)만 올리는 가벼운 테스트다.
 * MockMvc는 실제 톰캣을 띄우지 않고 HTTP 요청을 흉내 내 준다.
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

	/** 가짜 HTTP 요청을 보내는 도구. 스프링이 주입해 준다 */
	@Autowired
	private MockMvc mockMvc;

	/**
	 * GET /api/health 호출 시 200 OK와 {"status":"ok"}가 오는지 확인한다.
	 *
	 * @throws Exception MockMvc 호출이 던질 수 있는 예외 (테스트에서는 그대로 실패 처리)
	 */
	@Test
	@DisplayName("헬스체크는 200 OK와 status=ok를 반환한다")
	void health_returnsOk() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"));
	}
}
