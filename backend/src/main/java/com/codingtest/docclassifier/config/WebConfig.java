package com.codingtest.docclassifier.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 관련 설정.
 *
 * <p>프론트엔드(localhost:3000)와 백엔드(localhost:8080)는 포트가 달라
 * 브라우저 입장에서는 서로 다른 출처(origin)다. 이때 브라우저가 기본적으로
 * 요청을 막기 때문에(CORS 정책), 개발용으로 3000번 출처를 허용해 준다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	/**
	 * CORS 허용 규칙을 등록한다.
	 *
	 * @param registry 스프링이 넘겨주는 CORS 규칙 등록기
	 */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins("http://localhost:3000")
				.allowedMethods("GET", "POST");
	}
}
