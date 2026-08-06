package com.codingtest.docclassifier.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link AccuracyController}가 커밋된 측정 결과를 그대로 돌려주는지 확인한다.
 *
 * <p>제공 자료도 API 키도 없이 통과해야 한다. 이 API의 존재 이유가
 * "자료와 키가 없는 사람도 정확도 화면을 볼 수 있게 한다"이므로,
 * 테스트가 자료를 필요로 한다면 그 목적이 지켜지지 않았다는 뜻이다.
 */
@WebMvcTest(AccuracyController.class)
class AccuracyControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("저장된 package_01 측정 결과를 정확도·혼동행렬과 함께 돌려준다")
	void returnsStoredAccuracyResult() throws Exception {
		mockMvc.perform(get("/api/accuracy"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.packageId").value("package_01"))
				.andExpect(jsonPath("$.pipeline").value("규칙 → LLM"))
				.andExpect(jsonPath("$.report.totalPages").value(39))
				.andExpect(jsonPath("$.report.accuracy").exists())
				.andExpect(jsonPath("$.report.labelMetrics.length()").value(5))
				// 혼동행렬이 있어야 어느 라벨을 어느 라벨로 잘못 봤는지 화면에 표로 그릴 수 있다
				.andExpect(jsonPath("$.report.confusionMatrix.URLA_1003.URLA_1003").exists())
				.andExpect(jsonPath("$.pages.length()").value(39))
				.andExpect(jsonPath("$.pages[0].actual").exists())
				.andExpect(jsonPath("$.pages[0].predicted").exists());
	}
}
