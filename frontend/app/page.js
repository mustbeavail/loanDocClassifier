'use client';

import { useState } from 'react';

import AccuracyPanel from './AccuracyPanel';
import ClassifyPanel from './ClassifyPanel';
import styles from './styles.module.css';

/**
 * 첫 화면. 두 개의 탭을 오간다.
 *
 * - 분류하기: PDF를 올려 그 자리에서 분류하고, 원하면 정답지와 대조해 정확도까지 잰다
 * - 저장된 정확도: 제공 자료로 미리 재어 저장소에 커밋해 둔 수치를 본다
 *
 * 방금 잰 값과 저장된 값을 한 화면에 섞지 않으려고 탭으로 나눴다. 어느 쪽 수치인지 헷갈리면 안 된다.
 *
 * @returns {JSX.Element} 머리말 + 탭 + 선택된 탭의 내용
 */
export default function Home() {
  const [tab, setTab] = useState('classify');

  return (
    <main className={styles.main}>
      <header className={styles.header}>
        <h1 className={styles.title}>대출 서류 PDF 페이지 분류기</h1>
        <p className={styles.description}>
          여러 종류의 대출 서류가 섞인 PDF에서 페이지마다 문서 유형을 판별하고, 같은 문서끼리 다시 묶습니다.
        </p>
      </header>

      <nav className={styles.tabs}>
        <button
          type="button"
          className={tab === 'classify' ? styles.tabActive : styles.tab}
          onClick={() => setTab('classify')}
        >
          분류하기
        </button>
        <button
          type="button"
          className={tab === 'accuracy' ? styles.tabActive : styles.tab}
          onClick={() => setTab('accuracy')}
        >
          저장된 정확도
        </button>
      </nav>

      {tab === 'classify' ? <ClassifyPanel /> : <AccuracyPanel />}
    </main>
  );
}
