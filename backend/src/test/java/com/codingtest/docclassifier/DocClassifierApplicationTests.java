package com.codingtest.docclassifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 애플리케이션 기동 테스트.
 *
 * <p>@SpringBootTest는 실제 서버를 띄우듯 스프링 컨테이너를 통째로 올린다.
 * 빈(Bean) 설정이 잘못됐거나 의존성이 빠지면 여기서 바로 실패하므로,
 * 설정을 건드린 뒤에는 이 테스트부터 돌려 보면 된다.
 */
@SpringBootTest
class DocClassifierApplicationTests {

	/**
	 * 스프링 컨테이너가 예외 없이 뜨는지만 확인한다.
	 * 별도 검증 코드가 없어도, 기동에 실패하면 테스트가 실패한다.
	 */
	@Test
	@DisplayName("스프링 컨텍스트가 정상적으로 로딩된다")
	void contextLoads() {
	}

}
