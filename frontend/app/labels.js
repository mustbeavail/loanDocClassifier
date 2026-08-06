/**
 * 화면 전체가 함께 쓰는 라벨 표시 정보.
 *
 * 색과 한글 이름을 여기 한 곳에만 두는 이유는, 색 띠·결과표·문서 목록·혼동행렬이
 * 모두 같은 라벨을 그리기 때문이다. 흩어 두면 화면마다 색이 달라진다.
 */

/** 백엔드가 내려주는 라벨 값 → 화면 표시 이름과 색 */
export const LABELS = {
  URLA_1003: { name: '대출신청서', color: '#2563eb' },
  CREDIT_REPORT: { name: '신용보고서', color: '#16a34a' },
  TITLE_REPORT: { name: '권원보고서', color: '#ea580c' },
  INCOME_DOC: { name: '소득증빙', color: '#9333ea' },
  OTHER: { name: '기타', color: '#64748b' },
  UNDECIDED: { name: '미판정', color: '#d1d5db' },
};

/** 정답지 대조·혼동행렬에서 표를 그릴 때 쓰는 라벨 순서 */
export const LABEL_ORDER = [
  'URLA_1003',
  'CREDIT_REPORT',
  'TITLE_REPORT',
  'INCOME_DOC',
  'OTHER',
  'UNDECIDED',
];

/** 판정 경로 값 → 화면 표시 이름 */
export const DECIDED_BY = {
  RULE: '규칙',
  LLM: 'LLM',
  NONE: '판정 못함',
};

/**
 * 라벨의 표시 정보를 찾는다.
 *
 * @param {string} label 백엔드가 내려준 라벨 값
 * @returns {{name: string, color: string}} 표시 이름과 색. 모르는 값이면 값 자체를 이름으로 쓴다
 */
export function labelInfo(label) {
  return LABELS[label] ?? { name: label, color: '#d1d5db' };
}

/**
 * 0~1 사이 비율을 백분율 문자열로 바꾼다.
 *
 * @param {number} ratio 비율
 * @returns {string} 소수점 한 자리 백분율 (예: "82.1%")
 */
export function toPercent(ratio) {
  return `${(ratio * 100).toFixed(1)}%`;
}
