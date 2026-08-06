package com.codingtest.docclassifier.api;

/**
 * 요청이 실패했을 때 돌려주는 응답 본문.
 *
 * <p>메시지는 화면에 그대로 띄울 수 있게 한글로 적는다.
 * 예외의 원문 메시지를 그대로 내보내지 않는 이유는, 영어 스택 정보가 사용자에게 도움이 되지 않고
 * 서버 내부 사정이 그대로 노출되기 때문이다. 원문은 서버 로그에만 남긴다.
 *
 * @param message 사용자에게 보여 줄 실패 사유
 */
public record ErrorResponse(String message) {
}
