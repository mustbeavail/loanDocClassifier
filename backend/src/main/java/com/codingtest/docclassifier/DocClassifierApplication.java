package com.codingtest.docclassifier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 애플리케이션 시작점(entry point).
 *
 * <p>@SpringBootApplication 하나에 아래 세 가지가 묶여 있다.
 * <ul>
 *   <li>@Configuration - 이 클래스를 설정 파일로 인식</li>
 *   <li>@EnableAutoConfiguration - 클래스패스에 있는 라이브러리를 보고 필요한 설정을 자동 구성</li>
 *   <li>@ComponentScan - 이 클래스가 있는 패키지 하위의 @Component/@Service/@RestController를 자동 등록</li>
 * </ul>
 * 그래서 새로 만드는 클래스는 반드시 {@code com.codingtest.docclassifier} 패키지 아래에 둬야 인식된다.
 */
@SpringBootApplication
public class DocClassifierApplication {

	/**
	 * 내장 톰캣을 띄우고 스프링 컨테이너를 기동한다.
	 *
	 * @param args 실행 시 전달되는 커맨드라인 인자
	 */
	public static void main(String[] args) {
		SpringApplication.run(DocClassifierApplication.class, args);
	}

}
