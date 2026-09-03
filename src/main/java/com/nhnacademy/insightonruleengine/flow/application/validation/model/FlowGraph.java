package com.nhnacademy.insightonruleengine.flow.application.validation.model;

import com.nhnacademy.insightonruleengine.flow.domain.NodeType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 구조 검증이 사용하는 중립 그래프 모델이다.
 *
 * <p>저장 전 요청(FlowNodeRequest/FlowLinkRequest)과 저장된 정의(FlowDefinition)는 식별자와 필드 경로만
 * 다를 뿐 검증해야 할 그래프 규칙은 같다. 두 입력을 이 모델로 옮긴 뒤 한 검증기로 확인해, 저장된 정의를
 * 요청 DTO로 되돌리는 변환 없이 같은 규칙을 재사용한다.
 *
 * <p>{@code nodes}는 이미 key로 색인 가능한 상태여야 한다. 즉 key가 없거나 중복인 Node는 이 모델을
 * 만들기 전에 걸러진다. 반면 {@link Node#nodeType()}은 null일 수 있는데, 요청 경로에서 nodeType이
 * 누락돼도 Link 참조 오류({@code MISSING_SOURCE_NODE} 등)는 계속 알려줘야 하기 때문이다.
 */
public record FlowGraph(
        List<Node> nodes,
        List<Link> links
) {

    public FlowGraph {
        nodes = nodes != null ? List.copyOf(nodes) : List.of();
        links = links != null ? List.copyOf(links) : List.of();
    }

    /**
     * 요청 순서를 유지한 key 색인. 같은 key가 여러 번 오면 첫 Node를 남긴다.
     *
     * <p>record라 파생 상태를 담아둘 수 없어 호출할 때마다 새로 만든다. 반복문 안에서 부르지 말고 한 번
     * 받아 재사용한다.
     */
    public Map<String, Node> nodesByKey() {
        Map<String, Node> nodesByKey = new LinkedHashMap<>();
        for (Node node : nodes) {
            nodesByKey.putIfAbsent(node.key(), node);
        }
        return Collections.unmodifiableMap(nodesByKey);
    }

    /**
     * @param key      Link가 참조하는 Node 식별자. 요청은 clientNodeKey, 저장된 정의는 nodeId 문자열이다.
     * @param nodeType 요청 경로에서 누락될 수 있어 null을 허용한다.
     */
    public record Node(String key, NodeType nodeType) {

        public Node {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Node key는 필수입니다.");
            }
        }
    }

    /**
     * @param fieldPath 이 Link의 오류 필드 경로 접두어(예: {@code links[0]}). 세부 필드는 검증기가 덧붙인다.
     */
    public record Link(
            String fieldPath,
            String sourceKey,
            String sourcePort,
            String targetKey,
            String targetPort
    ) {

        private static final String LINKS_FIELD = "links";

        public Link {
            if (fieldPath == null || fieldPath.isBlank()) {
                throw new IllegalArgumentException("Link fieldPath는 필수입니다.");
            }
        }

        /**
         * 목록에서의 위치로 오류 필드 경로를 만들어 Link를 생성한다.
         * 요청과 저장된 정의가 같은 경로 형식을 쓰도록 조립을 한 곳에 둔다.
         */
        public static Link at(
                int index,
                String sourceKey,
                String sourcePort,
                String targetKey,
                String targetPort
        ) {
            return new Link(fieldPathAt(index), sourceKey, sourcePort, targetKey, targetPort);
        }

        /** Link를 만들 수 없는 오류(예: null 요소)도 같은 경로 형식을 쓰도록 노출한다. */
        public static String fieldPathAt(int index) {
            return LINKS_FIELD + "[" + index + "]";
        }
    }
}
