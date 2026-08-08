'use client';

import { useState } from 'react';

import AccuracyPanel from './AccuracyPanel';
import ClassifyPanel from './ClassifyPanel';
import SavedResultPanel from './SavedResultPanel';
import styles from './styles.module.css';

/** 탭 정의. 값은 상태로 쓰고 이름은 버튼에 그대로 쓴다 */
const TABS = [
  { id: 'classify', name: '분류하기' },
  { id: 'saved', name: '저장된 결과' },
  { id: 'accuracy', name: '저장된 정확도' },
];

/**
 * 첫 화면. 세 개의 탭을 오간다.
 *
 * - 분류하기: PDF를 올려 그 자리에서 분류하고, 원하면 정답지와 대조해 정확도까지 잰다
 * - 저장된 결과: 두 패키지의 분류·그룹핑 결과를 미리 산출해 커밋해 둔 파일에서 읽는다
 * - 저장된 정확도: package_01 정답지와의 대조표를 읽는다
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
        {TABS.map((item) => (
          <button
            key={item.id}
            type="button"
            className={tab === item.id ? styles.tabActive : styles.tab}
            onClick={() => setTab(item.id)}
          >
            {item.name}
          </button>
        ))}
      </nav>

      {tab === 'classify' && <ClassifyPanel />}
      {tab === 'saved' && <SavedResultPanel />}
      {tab === 'accuracy' && <AccuracyPanel />}
    </main>
  );
}
