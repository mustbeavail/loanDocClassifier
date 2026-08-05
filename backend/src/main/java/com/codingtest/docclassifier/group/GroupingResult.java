package com.codingtest.docclassifier.group;

import java.util.List;

/**
 * 그룹핑 결과 한 벌.
 *
 * <p>묶인 문서와 묶지 못한 페이지를 함께 돌려준다.
 * 근거 없이 아무 문서에나 끼워 넣는 것보다, 못 묶었다고 밝히는 편이 결과를 검토하기 쉽기 때문이다.
 *
 * @param documents      복원한 문서들
 * @param ungroupedPages 어느 문서에 넣을지 정하지 못한 셔플 PDF의 페이지 번호들
 */
public record GroupingResult(List<DocumentGroup> documents, List<Integer> ungroupedPages) {
}
