package com.codingtest.docclassifier.pdf;

/**
 * PDF 한 페이지에서 뽑아낸 정보.
 *
 * <p>뒤 단계(규칙 분류 · OCR 폴백 · 그룹핑)가 공통으로 쓰는 최소 단위라서
 * 페이지를 다룰 때는 항상 이 타입으로 주고받는다.
 *
 * @param pageNumber 1부터 세는 페이지 번호
 * @param text       페이지의 텍스트 레이어에서 추출한 원문. 스캔 페이지는 빈 문자열이 된다
 * @param imageCount 페이지에 들어 있는 이미지 개수. 텍스트가 없고 이미지만 있으면 스캔 페이지로 본다
 * @param width      페이지 가로 크기 (포인트 단위)
 * @param height     페이지 세로 크기 (포인트 단위)
 */
public record PdfPage(int pageNumber, String text, int imageCount, float width, float height) {
}
