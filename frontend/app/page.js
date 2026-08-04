"use client";

import { useState } from "react";
import styles from "./page.module.css";

/** 백엔드 주소. 배포 환경이 생기면 환경변수로 빼면 된다 */
const API_BASE_URL = "http://localhost:8080";

/**
 * 첫 화면.
 *
 * 지금은 보일러플레이트 단계라 백엔드 연결 확인 버튼만 있다.
 * 분류 기능이 붙으면 이 자리에 PDF 업로드 → 결과표/시각화가 들어간다.
 *
 * 파일 맨 위 "use client"는 이 컴포넌트를 브라우저에서 실행하겠다는 표시다.
 * useState 같은 상태나 onClick 같은 이벤트를 쓰려면 반드시 붙여야 한다.
 *
 * @returns 화면에 그려질 JSX
 */
export default function Home() {
  // 헬스체크 결과 문구. 아직 눌러 보지 않았으면 null
  const [healthMessage, setHealthMessage] = useState(null);

  /**
   * 백엔드 /api/health를 호출해 연결 상태를 확인한다.
   * 서버가 꺼져 있으면 fetch가 예외를 던지므로 그 경우도 화면에 알려 준다.
   */
  async function checkBackendHealth() {
    try {
      const response = await fetch(`${API_BASE_URL}/api/health`);
      const data = await response.json();
      setHealthMessage(`백엔드 연결 성공 (status: ${data.status})`);
    } catch (error) {
      setHealthMessage(`백엔드 연결 실패: ${error.message}`);
    }
  }

  return (
    <main className={styles.main}>
      <h1 className={styles.title}>대출 서류 분류기</h1>
      <p className={styles.description}>
        섞여 있는 대출 서류 PDF를 페이지 단위로 분류합니다.
      </p>

      <button className={styles.button} onClick={checkBackendHealth}>
        백엔드 연결 확인
      </button>

      {/* healthMessage가 있을 때만 결과 문구를 보여 준다 */}
      {healthMessage && <p className={styles.result}>{healthMessage}</p>}
    </main>
  );
}
