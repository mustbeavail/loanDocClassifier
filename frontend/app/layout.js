import "./globals.css";

/**
 * 브라우저 탭 제목과 설명. Next.js가 이 값을 읽어 <head>에 자동으로 넣어 준다.
 */
export const metadata = {
  title: "대출 서류 분류기",
  description: "섞여 있는 대출 서류 PDF를 페이지 단위로 분류합니다",
};

/**
 * 모든 페이지를 감싸는 최상위 레이아웃.
 * App Router에서는 이 파일이 <html>, <body> 태그를 직접 만들어야 한다.
 *
 * @param {object} props - Next.js가 넘겨주는 값
 * @param {React.ReactNode} props.children - 현재 주소에 해당하는 페이지 내용
 * @returns 전체 페이지를 감싼 HTML 구조
 */
export default function RootLayout({ children }) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
