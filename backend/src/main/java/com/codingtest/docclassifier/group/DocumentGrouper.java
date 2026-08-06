package com.codingtest.docclassifier.group;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.codingtest.docclassifier.classify.DocumentType;
import com.codingtest.docclassifier.llm.GeminiGrouper;
import com.codingtest.docclassifier.llm.GeminiGrouper.PageForGrouping;

/**
 * 분류가 끝난 페이지들을 원래 문서 단위로 다시 묶는다.
 *
 * <p>셔플은 문서가 아니라 <b>페이지 단위</b>로 되어 있어, 같은 문서의 페이지가 PDF 여기저기에 흩어져 있다.
 * 따라서 인접한 페이지를 잇는 방식으로는 풀리지 않고,
 * <b>라벨 + 페이지에 인쇄된 순번 표기</b>로 원래 문서를 재조립해야 한다.
 *
 * <p>순번 표기가 모든 페이지에 있고 겹치지도 않으면 그것만으로 문서가 완성된다.
 * 종이에 인쇄된 사실이라 해석의 여지가 없으므로 이 경우는 규칙으로 끝낸다.
 *
 * <p>그렇지 않은 경우는 두 가지이고, 둘 다 페이지 내용을 읽어야 풀린다. 그래서 LLM에 넘긴다.
 * <ul>
 * <li>순번이 겹칠 때 — 같은 유형의 문서가 여러 벌이라는 뜻인데, 어느 사본이 어느 벌의 것인지
 *     순번만으로는 알 수 없다.
 * <li>순번 표기가 없는 페이지가 있을 때 — 어느 문서의 몇 번째 장인지 표기가 말해 주지 않는다.
 * </ul>
 */
@Component
public class DocumentGrouper {

	private static final Logger log = LoggerFactory.getLogger(DocumentGrouper.class);

	private final GeminiGrouper llmGrouper;

	/**
	 * @param llmGrouper 순번 표기만으로 가를 수 없을 때 물어볼 LLM 그룹퍼
	 */
	public DocumentGrouper(GeminiGrouper llmGrouper) {
		this.llmGrouper = llmGrouper;
	}

	/**
	 * 페이지들을 문서 단위로 묶는다.
	 *
	 * @param pages 분류가 끝난 페이지 목록
	 * @return 복원한 문서들과, 묶지 못한 페이지 번호들
	 */
	public GroupingResult group(List<ClassifiedPage> pages) {
		List<DocumentGroup> documents = new ArrayList<>();
		List<Integer> ungrouped = new ArrayList<>();

		for (Map.Entry<DocumentType, List<ClassifiedPage>> entry : groupByLabel(pages).entrySet()) {
			groupOneLabel(entry.getKey(), entry.getValue(), documents, ungrouped);
		}

		ungrouped.sort(null);
		return new GroupingResult(documents, ungrouped);
	}

	/**
	 * 페이지를 라벨별로 나눈다. 라벨이 정해지지 않은 페이지는 묶을 수 없으므로 제외한다.
	 *
	 * @param pages 전체 페이지
	 * @return 라벨 → 그 라벨의 페이지 목록
	 */
	private Map<DocumentType, List<ClassifiedPage>> groupByLabel(List<ClassifiedPage> pages) {
		Map<DocumentType, List<ClassifiedPage>> byLabel = new LinkedHashMap<>();
		for (ClassifiedPage page : pages) {
			if (page.label() == DocumentType.UNDECIDED) {
				continue;
			}
			byLabel.computeIfAbsent(page.label(), key -> new ArrayList<>()).add(page);
		}
		return byLabel;
	}

	/**
	 * 라벨 하나에 속한 페이지들을 문서로 묶는다.
	 *
	 * @param label     대상 라벨
	 * @param pages     그 라벨의 페이지 목록
	 * @param documents 만들어진 문서를 담을 곳
	 * @param ungrouped 묶지 못한 페이지 번호를 담을 곳
	 */
	private void groupOneLabel(DocumentType label, List<ClassifiedPage> allPages,
			List<DocumentGroup> documents, List<Integer> ungrouped) {

		List<ClassifiedPage> pages = dropUnusablePageNumbers(allPages);

		if (pages.size() == 1) {
			// 한 장뿐이면 그 자체가 문서다. 물어볼 것이 없다
			documents.add(new DocumentGroup(label, totalOf(pages.get(0)), List.of(pages.get(0).pageNumber())));
			return;
		}

		if (canGroupByMarkersAlone(pages)) {
			documents.add(groupByMarkers(label, pages));
			return;
		}

		groupWithLlm(label, pages, documents, ungrouped);
	}

	/**
	 * 같은 라벨 안에서 총장수 없는 쪽번호가 진짜 쪽번호인지 보고, 아니면 버린다.
	 *
	 * <p>"Book 577, Page 401" 같은 등기 참조도 쪽번호와 모양이 똑같다.
	 * 페이지 한 장만 봐서는 가릴 수 없고, 같은 라벨을 모아 놓고 봐야 갈린다.
	 * 진짜 쪽번호라면 서로 겹치지 않고 그 문서의 장수를 넘지 않는다.
	 *
	 * @param pages 한 라벨의 페이지들
	 * @return 쓸 수 없는 쪽번호를 지운 페이지들. 쓸 만하면 받은 그대로
	 */
	private List<ClassifiedPage> dropUnusablePageNumbers(List<ClassifiedPage> pages) {
		List<Integer> pageNumbers = new ArrayList<>();
		for (ClassifiedPage page : pages) {
			if (page.marker() != null && page.marker().total() == 0) {
				pageNumbers.add(page.marker().position());
			}
		}
		if (pageNumbers.isEmpty()) {
			return pages;
		}

		boolean usable = true;
		for (int pageNumber : pageNumbers) {
			boolean duplicated = pageNumbers.indexOf(pageNumber) != pageNumbers.lastIndexOf(pageNumber);
			if (duplicated || pageNumber > pages.size()) {
				usable = false;
			}
		}
		if (usable) {
			return pages;
		}

		log.warn("{}의 쪽번호 {}는 본문 숫자로 보여 쓰지 않습니다", pages.get(0).label(), pageNumbers);
		List<ClassifiedPage> cleaned = new ArrayList<>();
		for (ClassifiedPage page : pages) {
			boolean drop = page.marker() != null && page.marker().total() == 0;
			cleaned.add(drop
					? new ClassifiedPage(page.pageNumber(), page.label(), null, page.text())
					: page);
		}
		return cleaned;
	}

	/**
	 * 순번 표기만으로 문서를 완성할 수 있는지 본다.
	 *
	 * <p>모든 페이지에 표기가 있고, 전체 장수가 하나로 같고, 순번이 겹치지 않아야 한다.
	 * 셋 중 하나라도 어긋나면 표기가 말해 주지 않는 부분이 남는다는 뜻이다.
	 *
	 * <p>총장수를 모르는 표기(쪽번호만 인쇄된 양식)가 섞여 있으면 여기서 끝내지 않는다.
	 * 몇 장짜리 문서인지 모르면 지금 모인 페이지가 한 부를 이루는지 판단할 수 없기 때문이다.
	 * 그 쪽번호는 문서를 가르는 데는 못 쓰고, 뒤에서 장 순서를 바로잡는 데만 쓴다.
	 *
	 * @param pages 한 라벨의 페이지들
	 * @return 표기만으로 묶을 수 있으면 true
	 */
	private boolean canGroupByMarkersAlone(List<ClassifiedPage> pages) {
		List<Integer> seenPositions = new ArrayList<>();
		int total = -1;

		for (ClassifiedPage page : pages) {
			if (page.marker() == null || page.marker().total() == 0) {
				return false;
			}
			if (total == -1) {
				total = page.marker().total();
			} else if (total != page.marker().total()) {
				return false;
			}
			if (seenPositions.contains(page.marker().position())) {
				return false;
			}
			seenPositions.add(page.marker().position());
		}
		return true;
	}

	/**
	 * 순번 표기 순서대로 페이지를 세워 문서 하나를 만든다.
	 *
	 * @param label 문서 유형
	 * @param pages 한 라벨의 페이지들 (모두 표기가 있고 겹치지 않는 상태)
	 * @return 완성된 문서
	 */
	private DocumentGroup groupByMarkers(DocumentType label, List<ClassifiedPage> pages) {
		List<ClassifiedPage> sorted = new ArrayList<>(pages);
		sorted.sort((left, right) -> Integer.compare(left.marker().position(), right.marker().position()));

		List<Integer> pageNumbers = new ArrayList<>();
		for (ClassifiedPage page : sorted) {
			pageNumbers.add(page.pageNumber());
		}
		return new DocumentGroup(label, sorted.get(0).marker().total(), pageNumbers);
	}

	/**
	 * 페이지 내용을 읽어야 갈리는 경우를 LLM에 맡긴다.
	 *
	 * <p>LLM이 답하지 못하면(키가 없거나 호출 실패) 그 라벨의 페이지를 통째로 미분류로 남긴다.
	 * 근거 없이 임의로 묶으면 틀린 문서를 만들어 놓고 맞는 것처럼 보이게 되기 때문이다.
	 *
	 * @param label     대상 라벨
	 * @param pages     그 라벨의 페이지들
	 * @param documents 만들어진 문서를 담을 곳
	 * @param ungrouped 묶지 못한 페이지 번호를 담을 곳
	 */
	private void groupWithLlm(DocumentType label, List<ClassifiedPage> pages,
			List<DocumentGroup> documents, List<Integer> ungrouped) {

		List<PageForGrouping> request = new ArrayList<>();
		for (ClassifiedPage page : pages) {
			request.add(new PageForGrouping(page.pageNumber(), markerTextOf(page), page.text()));
		}

		List<List<Integer>> grouped = llmGrouper.group(label.name(), request);
		if (grouped.isEmpty()) {
			log.warn("{} {}장을 묶지 못했습니다", label, pages.size());
			for (ClassifiedPage page : pages) {
				ungrouped.add(page.pageNumber());
			}
			return;
		}

		List<Integer> placed = new ArrayList<>();
		for (List<Integer> pageNumbers : grouped) {
			List<Integer> corrected = enforceMarkerOrder(pageNumbers, pages);
			documents.add(new DocumentGroup(label, totalOfGroup(pages, corrected), corrected));
			placed.addAll(corrected);
		}

		// 모델이 빠뜨린 페이지는 지어내지 않고 미분류로 남긴다
		for (ClassifiedPage page : pages) {
			if (!placed.contains(page.pageNumber())) {
				ungrouped.add(page.pageNumber());
			}
		}
	}

	/**
	 * 순번 표기가 있는 페이지들이 표기 순서대로 놓이도록 바로잡는다.
	 *
	 * <p>순번은 종이에 인쇄된 사실이라 모델이 바꿀 수 있는 것이 아니다.
	 * 그런데 모델에게 문서 전체를 나열하게 하면 확정된 순서까지 다시 쓰게 되고, 그 과정에서 어긋날 수 있다.
	 * 그래서 표기가 있는 페이지들이 앉은 <b>자리</b>는 모델의 판단을 그대로 두되,
	 * 그 자리에 들어갈 페이지는 순번대로 다시 채운다.
	 *
	 * <p>표기가 없는 페이지의 위치는 손대지 않는다. 그건 내용을 읽어야 아는 것이라 모델의 몫이다.
	 *
	 * @param llmOrder 모델이 정한 페이지 순서
	 * @param pages    이 라벨의 페이지들 (순번 표기를 찾기 위해 필요)
	 * @return 순번 표기 순서가 지켜진 페이지 순서
	 */
	private List<Integer> enforceMarkerOrder(List<Integer> llmOrder, List<ClassifiedPage> pages) {
		List<Integer> markedPages = new ArrayList<>();
		for (int pageNumber : llmOrder) {
			if (markerOf(pageNumber, pages) != null) {
				markedPages.add(pageNumber);
			}
		}
		markedPages.sort((left, right) -> Integer.compare(
				markerOf(left, pages).position(), markerOf(right, pages).position()));

		List<Integer> corrected = new ArrayList<>();
		int nextMarked = 0;
		for (int pageNumber : llmOrder) {
			if (markerOf(pageNumber, pages) == null) {
				corrected.add(pageNumber);
			} else {
				corrected.add(markedPages.get(nextMarked));
				nextMarked++;
			}
		}
		return corrected;
	}

	/**
	 * 페이지 번호로 순번 표기를 찾는다.
	 *
	 * @param pageNumber 찾을 페이지 번호
	 * @param pages      이 라벨의 페이지들
	 * @return 순번 표기. 없으면 null
	 */
	private PageMarker markerOf(int pageNumber, List<ClassifiedPage> pages) {
		for (ClassifiedPage page : pages) {
			if (page.pageNumber() == pageNumber) {
				return page.marker();
			}
		}
		return null;
	}

	/**
	 * 문서에 적을 전체 장수를 고른다. 소속 페이지 중 순번 표기가 있는 첫 장의 값을 쓴다.
	 *
	 * @param pages       한 라벨의 페이지들
	 * @param pageNumbers 그중 이 문서에 속한 페이지 번호들
	 * @return 전체 장수. 표기가 하나도 없으면 0
	 */
	private int totalOfGroup(List<ClassifiedPage> pages, List<Integer> pageNumbers) {
		for (ClassifiedPage page : pages) {
			if (pageNumbers.contains(page.pageNumber()) && page.marker() != null) {
				return page.marker().total();
			}
		}
		return 0;
	}

	/**
	 * 페이지의 전체 장수를 꺼낸다.
	 *
	 * @param page 대상 페이지
	 * @return 전체 장수. 표기가 없으면 0
	 */
	private int totalOf(ClassifiedPage page) {
		return page.marker() == null ? 0 : page.marker().total();
	}

	/**
	 * 순번 표기를 프롬프트에 넣을 문자열로 만든다.
	 *
	 * @param page 대상 페이지
	 * @return "3 of 5" 또는 총장수를 모르면 "Page 3". 표기가 없으면 null
	 */
	private String markerTextOf(ClassifiedPage page) {
		return page.marker() == null ? null : page.marker().text();
	}
}
