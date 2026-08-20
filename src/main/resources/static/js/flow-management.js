(() => {
    "use strict";

    const API_BASE = "/api/v1/flows";
    const STORAGE_KEY = "insighton.flow.connection";
    const NODE_TYPES = [
        "SENSOR",
        "LOCATION",
        "SCHEDULE",
        "THRESHOLD",
        "TIME_WINDOW",
        "TIMER",
        "ACTUATOR_CONTROL",
        "ALERT",
        "EXTERNAL_NOTIFICATION"
    ];
    const ACTION_TYPES = new Set(["ACTUATOR_CONTROL", "ALERT", "EXTERNAL_NOTIFICATION"]);
    const TRIGGER_TYPES = new Set(["SENSOR", "LOCATION", "SCHEDULE"]);

    const state = {
        groupId: null,
        userId: null,
        locationId: null,
        flows: [],
        archivedFlows: [],
        archivedLoaded: false,
        filter: "ALL",
        detail: null,
        draftNodes: [],
        draftLinks: [],
        pendingRequests: 0
    };

    const elements = {};

    document.addEventListener("DOMContentLoaded", initialize);

    function initialize() {
        collectElements();
        bindEvents();
        restoreConnection();
        updateConnectionDisplay();

        if (hasConnection()) {
            loadFlows();
        } else {
            elements.connectionPanel.hidden = false;
            renderFlowList();
        }
    }

    function collectElements() {
        [
            "connectionToggle",
            "connectionDot",
            "connectionLabel",
            "connectionPanel",
            "connectionForm",
            "groupIdInput",
            "userIdInput",
            "locationIdInput",
            "totalCount",
            "activeCount",
            "inactiveCount",
            "archivedCount",
            "listTitle",
            "flowList",
            "filterRow",
            "openCreateButton",
            "emptyCreateButton",
            "emptyState",
            "detailContent",
            "detailStatus",
            "detailId",
            "detailName",
            "detailDescription",
            "detailActions",
            "detailGroup",
            "detailLocation",
            "detailCreated",
            "detailComposition",
            "nodeList",
            "linkList",
            "addNodeButton",
            "addLinkButton",
            "updateForm",
            "updateName",
            "updateDescription",
            "createDialog",
            "createForm",
            "closeCreateButton",
            "cancelCreateButton",
            "createLocationId",
            "createName",
            "createDescription",
            "loadingLayer",
            "toastRegion"
        ].forEach((id) => {
            elements[id] = document.getElementById(id);
        });
    }

    function bindEvents() {
        elements.connectionToggle.addEventListener("click", () => {
            elements.connectionPanel.hidden = !elements.connectionPanel.hidden;
        });
        elements.connectionForm.addEventListener("submit", saveConnection);
        elements.filterRow.addEventListener("click", handleFilterClick);
        elements.flowList.addEventListener("click", handleFlowSelection);
        elements.openCreateButton.addEventListener("click", openCreateDialog);
        elements.emptyCreateButton.addEventListener("click", openCreateDialog);
        elements.closeCreateButton.addEventListener("click", closeCreateDialog);
        elements.cancelCreateButton.addEventListener("click", closeCreateDialog);
        elements.createForm.addEventListener("submit", createFlow);
        elements.addNodeButton.addEventListener("click", addNode);
        elements.addLinkButton.addEventListener("click", addLink);
        elements.nodeList.addEventListener("input", updateNodeDraft);
        elements.nodeList.addEventListener("change", updateNodeDraft);
        elements.nodeList.addEventListener("click", removeNode);
        elements.linkList.addEventListener("input", updateLinkDraft);
        elements.linkList.addEventListener("change", updateLinkDraft);
        elements.linkList.addEventListener("click", removeLink);
        elements.updateForm.addEventListener("submit", updateFlow);
    }

    function restoreConnection() {
        const pageDefaults = document.querySelector(".flow-page")?.dataset ?? {};
        const query = new URLSearchParams(window.location.search);
        let stored = {};
        try {
            stored = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || "{}");
        } catch (error) {
            stored = {};
        }

        state.groupId = positiveNumber(query.get("groupId") || pageDefaults.groupId || stored.groupId);
        state.userId = positiveNumber(pageDefaults.userId || query.get("userId") || stored.userId);
        state.locationId = positiveNumber(query.get("locationId") || stored.locationId);

        elements.groupIdInput.value = state.groupId ?? "";
        elements.userIdInput.value = state.userId ?? "";
        elements.locationIdInput.value = state.locationId ?? "";
    }

    function saveConnection(event) {
        event.preventDefault();
        state.groupId = positiveNumber(elements.groupIdInput.value);
        state.userId = positiveNumber(elements.userIdInput.value);
        state.locationId = positiveNumber(elements.locationIdInput.value);

        if (!hasConnection()) {
            showToast("Group ID와 User ID를 확인해 주세요.", true);
            return;
        }

        sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
            groupId: state.groupId,
            userId: state.userId,
            locationId: state.locationId
        }));
        state.detail = null;
        state.archivedLoaded = false;
        elements.connectionPanel.hidden = true;
        updateConnectionDisplay();
        loadFlows();
    }

    function updateConnectionDisplay() {
        if (!hasConnection()) {
            elements.connectionDot.classList.add("muted");
            elements.connectionLabel.textContent = "연결 정보 설정";
            return;
        }

        elements.connectionDot.classList.remove("muted");
        elements.connectionLabel.textContent = `Group ${state.groupId} · User ${state.userId}`;
        elements.createLocationId.value = state.locationId ?? "";
    }

    async function loadFlows(selectFlowId = null) {
        if (!hasConnection()) {
            renderFlowList();
            return;
        }

        try {
            state.flows = await request("", {method: "GET"});
            updateSummary();
            renderFlowList();

            if (selectFlowId != null) {
                await selectFlow(selectFlowId);
            } else if (state.detail != null) {
                const stillVisible = state.flows.some((flow) => flow.flowId === state.detail.flowId);
                if (stillVisible) {
                    await selectFlow(state.detail.flowId);
                } else {
                    clearDetail();
                }
            }
        } catch (error) {
            showToast(error.message, true);
            renderFlowList(error.message);
        }
    }

    async function loadArchivedFlows() {
        try {
            state.archivedFlows = await request("", {
                method: "GET",
                query: {status: "ARCHIVED"}
            });
            state.archivedLoaded = true;
            elements.archivedCount.textContent = state.archivedFlows.length;
            renderFlowList();
        } catch (error) {
            showToast(error.message, true);
            renderFlowList(error.message);
        }
    }

    function updateSummary() {
        elements.totalCount.textContent = state.flows.length;
        elements.activeCount.textContent = state.flows.filter((flow) => flow.status === "ACTIVE").length;
        elements.inactiveCount.textContent = state.flows.filter((flow) => flow.status === "INACTIVE").length;
        elements.archivedCount.textContent = state.archivedLoaded ? state.archivedFlows.length : "—";
    }

    async function handleFilterClick(event) {
        const button = event.target.closest("[data-filter]");
        if (button == null) {
            return;
        }

        state.filter = button.dataset.filter;
        elements.filterRow.querySelectorAll("[data-filter]").forEach((chip) => {
            chip.classList.toggle("active", chip === button);
        });
        elements.listTitle.textContent = state.filter === "ARCHIVED" ? "휴지통" : "Flow 목록";
        clearDetail();

        if (state.filter === "ARCHIVED" && !state.archivedLoaded) {
            await loadArchivedFlows();
            return;
        }
        renderFlowList();
    }

    function currentFlows() {
        if (state.filter === "ARCHIVED") {
            return state.archivedFlows;
        }
        if (state.filter === "ALL") {
            return state.flows;
        }
        return state.flows.filter((flow) => flow.status === state.filter);
    }

    function renderFlowList(errorMessage = null) {
        if (errorMessage != null) {
            elements.flowList.innerHTML = `<div class="empty-list">${escapeHtml(errorMessage)}</div>`;
            return;
        }
        if (!hasConnection()) {
            elements.flowList.innerHTML = `
                <div class="empty-list">
                    상단의 연결 정보에서<br>Group ID와 User ID를 입력해 주세요.
                </div>`;
            return;
        }

        const flows = currentFlows();
        if (flows.length === 0) {
            const message = state.filter === "ARCHIVED"
                ? "휴지통이 비어 있습니다."
                : "조건에 맞는 Flow가 없습니다.<br>새 Flow를 만들어 보세요.";
            elements.flowList.innerHTML = `<div class="empty-list">${message}</div>`;
            return;
        }

        elements.flowList.innerHTML = flows.map((flow) => `
            <button class="flow-card ${state.detail?.flowId === flow.flowId ? "selected" : ""}"
                    type="button" data-flow-id="${flow.flowId}">
                <span class="flow-card-top">
                    <h3>${escapeHtml(flow.name)}</h3>
                    <span class="status-badge" data-status="${flow.status}">${flow.status}</span>
                </span>
                <p>${escapeHtml(flow.description || "설명이 없는 Flow입니다.")}</p>
                <span class="flow-card-meta">
                    <span>FLOW #${flow.flowId}</span>
                    <span>LOCATION ${flow.locationId}</span>
                </span>
            </button>
        `).join("");
    }

    function handleFlowSelection(event) {
        const card = event.target.closest("[data-flow-id]");
        if (card == null) {
            return;
        }
        selectFlow(Number(card.dataset.flowId));
    }

    async function selectFlow(flowId) {
        try {
            const detail = await request(`/${flowId}`, {method: "GET"});
            state.detail = detail;
            hydrateDraft(detail);
            renderFlowList();
            renderDetail();
        } catch (error) {
            showToast(error.message, true);
            if (error.status === 404) {
                clearDetail();
                await refreshCurrentFilter();
            }
        }
    }

    function hydrateDraft(detail) {
        const keyByNodeId = new Map();
        state.draftNodes = (detail.nodes || []).map((node, index) => {
            const clientNodeKey = `node-${node.nodeId ?? index + 1}`;
            keyByNodeId.set(node.nodeId, clientNodeKey);
            return {
                clientNodeKey,
                nodeType: node.nodeType,
                configuration: JSON.stringify(node.configuration ?? {}, null, 2)
            };
        });
        state.draftLinks = (detail.links || []).map((link) => ({
            sourceClientNodeKey: keyByNodeId.get(link.sourceNodeId) || "",
            targetClientNodeKey: keyByNodeId.get(link.targetNodeId) || "",
            sourcePort: link.sourcePort || "",
            targetPort: link.targetPort || "in"
        }));
    }

    function renderDetail() {
        const detail = state.detail;
        if (detail == null) {
            clearDetail();
            return;
        }

        elements.emptyState.hidden = true;
        elements.detailContent.hidden = false;
        elements.detailStatus.textContent = detail.status;
        elements.detailStatus.dataset.status = detail.status;
        elements.detailId.textContent = `FLOW #${detail.flowId}`;
        elements.detailName.textContent = detail.name;
        elements.detailDescription.textContent = detail.description || "설명이 없는 Flow입니다.";
        elements.detailGroup.textContent = detail.groupId;
        elements.detailLocation.textContent = detail.locationId;
        elements.detailCreated.textContent = formatDate(detail.createdAt);
        elements.detailComposition.textContent = `${state.draftNodes.length} Nodes · ${state.draftLinks.length} Links`;
        elements.updateName.value = suggestUpdatedName(detail.name);
        elements.updateDescription.value = detail.description || "";

        renderDetailActions();
        renderNodes();
        renderLinks();

        const readOnly = detail.status === "ARCHIVED";
        elements.addNodeButton.hidden = readOnly;
        elements.addLinkButton.hidden = readOnly;
        elements.updateForm.hidden = readOnly;
    }

    function renderDetailActions() {
        const detail = state.detail;
        if (detail.status === "ARCHIVED") {
            elements.detailActions.innerHTML = `
                <button class="button secondary" type="button" data-action="restore">복구</button>
                <button class="button danger" type="button" data-action="delete">영구 삭제</button>`;
        } else {
            const nextStatus = detail.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
            const label = nextStatus === "ACTIVE" ? "활성화" : "비활성화";
            elements.detailActions.innerHTML = `
                <button class="button ${nextStatus === "ACTIVE" ? "primary" : "secondary"}"
                        type="button" data-action="status" data-status="${nextStatus}">${label}</button>
                <button class="button danger" type="button" data-action="archive">휴지통으로 이동</button>`;
        }

        elements.detailActions.querySelector("[data-action='status']")
            ?.addEventListener("click", changeStatus);
        elements.detailActions.querySelector("[data-action='archive']")
            ?.addEventListener("click", archiveFlow);
        elements.detailActions.querySelector("[data-action='restore']")
            ?.addEventListener("click", restoreFlow);
        elements.detailActions.querySelector("[data-action='delete']")
            ?.addEventListener("click", deleteFlow);
    }

    function renderNodes() {
        const readOnly = state.detail?.status === "ARCHIVED";
        if (state.draftNodes.length === 0) {
            elements.nodeList.innerHTML = `
                <div class="builder-empty">
                    Node가 없습니다. Trigger와 Action Node부터 추가해 주세요.
                </div>`;
            return;
        }

        elements.nodeList.innerHTML = state.draftNodes.map((node, index) => `
            <article class="node-card" data-node-index="${index}">
                <span class="node-order">${String(index + 1).padStart(2, "0")}</span>
                <label>
                    Client Key
                    <input data-field="clientNodeKey" value="${escapeAttribute(node.clientNodeKey)}"
                           maxlength="100" required ${readOnly ? "disabled" : ""}>
                </label>
                <label>
                    Node Type
                    <select data-field="nodeType" ${readOnly ? "disabled" : ""}>
                        ${NODE_TYPES.map((type) => `
                            <option value="${type}" ${node.nodeType === type ? "selected" : ""}>${type}</option>
                        `).join("")}
                    </select>
                </label>
                <label>
                    Configuration JSON
                    <input class="configuration-input" data-field="configuration"
                           value="${escapeAttribute(compactJson(node.configuration))}"
                           ${readOnly ? "disabled" : ""}>
                </label>
                ${readOnly ? "" : `
                    <button class="remove-row" data-remove-node="${index}" type="button" aria-label="Node 삭제">×</button>
                `}
            </article>
        `).join("");
    }

    function renderLinks() {
        const readOnly = state.detail?.status === "ARCHIVED";
        if (state.draftLinks.length === 0) {
            elements.linkList.innerHTML = `
                <div class="builder-empty">
                    Link가 없습니다. Node를 추가한 뒤 실행 순서를 연결해 주세요.
                </div>`;
            return;
        }

        const sourceNodes = state.draftNodes.filter((node) => !ACTION_TYPES.has(node.nodeType));
        const targetNodes = state.draftNodes.filter((node) => !TRIGGER_TYPES.has(node.nodeType));
        elements.linkList.innerHTML = state.draftLinks.map((link, index) => `
            <article class="link-card" data-link-index="${index}">
                <label>
                    Source Node
                    <select data-field="sourceClientNodeKey" required ${readOnly ? "disabled" : ""}>
                        ${nodeOptions(sourceNodes, link.sourceClientNodeKey)}
                    </select>
                </label>
                <label>
                    Source Port
                    <input data-field="sourcePort" value="${escapeAttribute(link.sourcePort)}"
                           maxlength="50" required ${readOnly ? "disabled" : ""}>
                </label>
                <span class="link-arrow" aria-hidden="true">→</span>
                <label>
                    Target Node
                    <select data-field="targetClientNodeKey" required ${readOnly ? "disabled" : ""}>
                        ${nodeOptions(targetNodes, link.targetClientNodeKey)}
                    </select>
                </label>
                <label>
                    Target Port
                    <input data-field="targetPort" value="${escapeAttribute(link.targetPort)}"
                           maxlength="50" required ${readOnly ? "disabled" : ""}>
                </label>
                ${readOnly ? "" : `
                    <button class="remove-row" data-remove-link="${index}" type="button" aria-label="Link 삭제">×</button>
                `}
            </article>
        `).join("");
    }

    function nodeOptions(nodes, selectedKey) {
        if (nodes.length === 0) {
            return "<option value=\"\">선택 가능한 Node 없음</option>";
        }
        return nodes.map((node) => `
            <option value="${escapeAttribute(node.clientNodeKey)}"
                    ${node.clientNodeKey === selectedKey ? "selected" : ""}>
                ${escapeHtml(node.clientNodeKey)} · ${node.nodeType}
            </option>
        `).join("");
    }

    function updateNodeDraft(event) {
        const card = event.target.closest("[data-node-index]");
        if (card == null || event.target.dataset.field == null) {
            return;
        }
        const index = Number(card.dataset.nodeIndex);
        const previousKey = state.draftNodes[index].clientNodeKey;
        state.draftNodes[index][event.target.dataset.field] = event.target.value;

        if (event.target.dataset.field === "clientNodeKey") {
            state.draftLinks.forEach((link) => {
                if (link.sourceClientNodeKey === previousKey) {
                    link.sourceClientNodeKey = event.target.value;
                }
                if (link.targetClientNodeKey === previousKey) {
                    link.targetClientNodeKey = event.target.value;
                }
            });
            renderLinks();
        }
        if (event.target.dataset.field === "nodeType") {
            renderLinks();
        }
    }

    function updateLinkDraft(event) {
        const card = event.target.closest("[data-link-index]");
        if (card == null || event.target.dataset.field == null) {
            return;
        }
        const index = Number(card.dataset.linkIndex);
        state.draftLinks[index][event.target.dataset.field] = event.target.value;
    }

    function addNode() {
        const nodeType = state.draftNodes.length === 0
            ? "SENSOR"
            : state.draftNodes.length === 1 ? "ALERT" : "THRESHOLD";
        state.draftNodes.push({
            clientNodeKey: uniqueNodeKey(nodeType.toLowerCase()),
            nodeType,
            configuration: defaultConfiguration(nodeType)
        });
        renderNodes();
        renderLinks();
        updateComposition();
    }

    function removeNode(event) {
        const button = event.target.closest("[data-remove-node]");
        if (button == null) {
            return;
        }
        const [removed] = state.draftNodes.splice(Number(button.dataset.removeNode), 1);
        state.draftLinks = state.draftLinks.filter((link) =>
            link.sourceClientNodeKey !== removed.clientNodeKey
            && link.targetClientNodeKey !== removed.clientNodeKey);
        renderNodes();
        renderLinks();
        updateComposition();
    }

    function addLink() {
        const source = state.draftNodes.find((node) => !ACTION_TYPES.has(node.nodeType));
        const target = state.draftNodes.find((node) =>
            !TRIGGER_TYPES.has(node.nodeType) && node.clientNodeKey !== source?.clientNodeKey);
        if (source == null || target == null) {
            showToast("Link를 만들려면 연결 가능한 Source와 Target Node가 필요합니다.", true);
            return;
        }
        state.draftLinks.push({
            sourceClientNodeKey: source.clientNodeKey,
            targetClientNodeKey: target.clientNodeKey,
            sourcePort: defaultSourcePort(source.nodeType),
            targetPort: "in"
        });
        renderLinks();
        updateComposition();
    }

    function removeLink(event) {
        const button = event.target.closest("[data-remove-link]");
        if (button == null) {
            return;
        }
        state.draftLinks.splice(Number(button.dataset.removeLink), 1);
        renderLinks();
        updateComposition();
    }

    function updateComposition() {
        elements.detailComposition.textContent =
            `${state.draftNodes.length} Nodes · ${state.draftLinks.length} Links`;
    }

    async function createFlow(event) {
        event.preventDefault();
        if (!ensureConnection()) {
            return;
        }

        const payload = {
            locationId: positiveNumber(elements.createLocationId.value),
            name: elements.createName.value.trim(),
            description: emptyToNull(elements.createDescription.value)
        };

        try {
            const created = await request("", {method: "POST", body: payload});
            state.locationId = payload.locationId;
            closeCreateDialog();
            elements.createForm.reset();
            elements.createLocationId.value = state.locationId;
            state.filter = "ALL";
            activateFilter("ALL");
            showToast(`“${created.name}” Flow를 만들었습니다.`);
            await loadFlows(created.flowId);
        } catch (error) {
            showToast(error.message, true);
        }
    }

    async function updateFlow(event) {
        event.preventDefault();
        if (state.detail == null) {
            return;
        }

        const validationMessage = validateDraftConfiguration();
        if (validationMessage != null) {
            showToast(validationMessage, true);
            return;
        }

        let nodes;
        try {
            nodes = state.draftNodes.map((node) => ({
                clientNodeKey: node.clientNodeKey.trim(),
                nodeType: node.nodeType,
                configuration: JSON.parse(node.configuration || "{}")
            }));
        } catch (error) {
            showToast("Node configuration은 올바른 JSON이어야 합니다.", true);
            return;
        }

        const payload = {
            name: elements.updateName.value.trim(),
            description: emptyToNull(elements.updateDescription.value),
            nodes,
            links: state.draftLinks.map((link) => ({
                sourceClientNodeKey: link.sourceClientNodeKey.trim(),
                targetClientNodeKey: link.targetClientNodeKey.trim(),
                sourcePort: link.sourcePort.trim(),
                targetPort: link.targetPort.trim()
            }))
        };

        if (payload.nodes.length === 0 || payload.links.length === 0) {
            showToast("수정본 저장을 위해 Node와 Link를 최소 1개 이상 추가해 주세요.", true);
            return;
        }

        try {
            const updated = await request(`/${state.detail.flowId}`, {
                method: "PUT",
                body: payload
            });
            state.archivedLoaded = false;
            showToast(`수정본을 저장했습니다. 새 Flow ID는 ${updated.flowId}입니다.`);
            await loadFlows(updated.flowId);
        } catch (error) {
            showToast(error.message, true);
        }
    }

    // 서버로 보내기 전에 Node와 Link의 필수·길이 제약을 안내하기 위해 검사한다.
    function validateDraftConfiguration() {
        const invalidNode = state.draftNodes.find((node) => {
            const clientNodeKey = node.clientNodeKey.trim();
            return clientNodeKey === "" || clientNodeKey.length > 100;
        });
        if (invalidNode != null) {
            return "Client Key는 필수이며 100자 이하여야 합니다.";
        }

        const invalidLinkKey = state.draftLinks.find((link) => {
            const sourceKey = link.sourceClientNodeKey.trim();
            const targetKey = link.targetClientNodeKey.trim();
            return sourceKey === "" || sourceKey.length > 100
                || targetKey === "" || targetKey.length > 100;
        });
        if (invalidLinkKey != null) {
            return "Link의 Source와 Target Node를 올바르게 선택해 주세요.";
        }

        const invalidPort = state.draftLinks.find((link) => {
            const sourcePort = link.sourcePort.trim();
            const targetPort = link.targetPort.trim();
            return sourcePort === "" || sourcePort.length > 50
                || targetPort === "" || targetPort.length > 50;
        });
        if (invalidPort != null) {
            return "Source와 Target Port는 필수이며 50자 이하여야 합니다.";
        }
        return null;
    }

    async function archiveFlow() {
        const confirmed = window.confirm(`“${state.detail.name}” Flow를 휴지통으로 이동할까요?`);
        if (!confirmed) {
            return;
        }

        try {
            const archived = await request(`/${state.detail.flowId}/archive`, {method: "POST"});
            state.archivedLoaded = false;
            showToast(`${archived.name} Flow를 휴지통으로 이동했습니다.`);
            clearDetail();
            await loadFlows();
        } catch (error) {
            showToast(error.message, true);
        }
    }

    async function changeStatus(event) {
        const nextStatus = event.currentTarget.dataset.status;
        try {
            const updated = await request(`/${state.detail.flowId}/status`, {
                method: "PUT",
                body: {status: nextStatus}
            });
            showToast(`${updated.name} Flow를 ${nextStatus === "ACTIVE" ? "활성화" : "비활성화"}했습니다.`);
            await loadFlows(updated.flowId);
        } catch (error) {
            showToast(error.message, true);
        }
    }

    async function restoreFlow() {
        try {
            const restored = await request(`/${state.detail.flowId}/restore`, {method: "POST"});
            state.archivedLoaded = false;
            state.filter = "ALL";
            activateFilter("ALL");
            showToast(`${restored.name} Flow를 대기 상태로 복구했습니다.`);
            await loadFlows(restored.flowId);
        } catch (error) {
            showToast(error.message, true);
        }
    }

    async function deleteFlow() {
        const confirmed = window.confirm(
            `“${state.detail.name}” Flow를 영구 삭제할까요?\n삭제한 Flow는 복구할 수 없습니다.`);
        if (!confirmed) {
            return;
        }

        try {
            await request(`/${state.detail.flowId}`, {method: "DELETE"});
            showToast("Flow를 영구 삭제했습니다.");
            clearDetail();
            state.archivedLoaded = false;
            await loadArchivedFlows();
        } catch (error) {
            showToast(error.message, true);
        }
    }

    async function refreshCurrentFilter() {
        if (state.filter === "ARCHIVED") {
            state.archivedLoaded = false;
            await loadArchivedFlows();
            return;
        }
        await loadFlows();
    }

    function clearDetail() {
        state.detail = null;
        state.draftNodes = [];
        state.draftLinks = [];
        elements.emptyState.hidden = false;
        elements.detailContent.hidden = true;
        renderFlowList();
    }

    function openCreateDialog() {
        if (!ensureConnection()) {
            return;
        }
        elements.createLocationId.value = state.locationId ?? "";
        elements.createDialog.showModal();
        window.setTimeout(() => elements.createName.focus(), 0);
    }

    function closeCreateDialog() {
        elements.createDialog.close();
    }

    function ensureConnection() {
        if (hasConnection()) {
            return true;
        }
        elements.connectionPanel.hidden = false;
        showToast("먼저 API 연결 정보를 입력해 주세요.", true);
        return false;
    }

    function hasConnection() {
        return state.groupId != null && state.userId != null;
    }

    async function request(path, options) {
        if (!hasConnection()) {
            throw new Error("Group ID와 User ID가 필요합니다.");
        }

        const query = new URLSearchParams({groupId: String(state.groupId)});
        Object.entries(options.query || {}).forEach(([key, value]) => {
            if (value != null && value !== "") {
                query.set(key, value);
            }
        });
        const headers = {"X-User-Id": String(state.userId)};
        const init = {method: options.method, headers};
        if (options.body != null) {
            headers["Content-Type"] = "application/json";
            init.body = JSON.stringify(options.body);
        }

        showLoading();
        try {
            const response = await fetch(`${API_BASE}${path}?${query}`, init);
            const contentType = response.headers.get("content-type") || "";
            const body = response.status === 204
                ? null
                : contentType.includes("application/json") ? await response.json() : await response.text();
            if (!response.ok) {
                const error = new Error(body?.message || body || `요청에 실패했습니다. (${response.status})`);
                error.status = response.status;
                throw error;
            }
            return body;
        } finally {
            hideLoading();
        }
    }

    function showLoading() {
        state.pendingRequests += 1;
        elements.loadingLayer.hidden = false;
    }

    function hideLoading() {
        state.pendingRequests = Math.max(0, state.pendingRequests - 1);
        if (state.pendingRequests === 0) {
            elements.loadingLayer.hidden = true;
        }
    }

    function showToast(message, isError = false) {
        const toast = document.createElement("div");
        toast.className = `toast${isError ? " error" : ""}`;
        toast.textContent = message;
        elements.toastRegion.append(toast);
        window.setTimeout(() => toast.remove(), 4200);
    }

    function activateFilter(filter) {
        elements.filterRow.querySelectorAll("[data-filter]").forEach((chip) => {
            chip.classList.toggle("active", chip.dataset.filter === filter);
        });
        elements.listTitle.textContent = filter === "ARCHIVED" ? "휴지통" : "Flow 목록";
    }

    function uniqueNodeKey(prefix) {
        let sequence = state.draftNodes.length + 1;
        let candidate = `${prefix}-${sequence}`;
        const keys = new Set(state.draftNodes.map((node) => node.clientNodeKey));
        while (keys.has(candidate)) {
            sequence += 1;
            candidate = `${prefix}-${sequence}`;
        }
        return candidate;
    }

    function defaultSourcePort(nodeType) {
        if (TRIGGER_TYPES.has(nodeType)) {
            return "out";
        }
        return "true";
    }

    function defaultConfiguration(nodeType) {
        if (nodeType === "SENSOR") {
            return JSON.stringify({sensorId: 1});
        }
        if (nodeType === "LOCATION") {
            return "{}";
        }
        return "{}";
    }

    function suggestUpdatedName(name) {
        const versionMatch = name.match(/^(.*) v(\d+)$/i);
        if (versionMatch == null) {
            return `${name} v2`.slice(0, 100);
        }
        return `${versionMatch[1]} v${Number(versionMatch[2]) + 1}`.slice(0, 100);
    }

    function compactJson(value) {
        try {
            return JSON.stringify(JSON.parse(value || "{}"));
        } catch (error) {
            return value;
        }
    }

    function formatDate(value) {
        if (value == null) {
            return "—";
        }
        return new Intl.DateTimeFormat("ko-KR", {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit"
        }).format(new Date(value));
    }

    function positiveNumber(value) {
        if (value == null || value === "") {
            return null;
        }
        const parsed = Number(value);
        return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
    }

    function emptyToNull(value) {
        const trimmed = value.trim();
        return trimmed.length === 0 ? null : trimmed;
    }

    function escapeHtml(value) {
        return String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#039;");
    }

    function escapeAttribute(value) {
        return escapeHtml(value).replaceAll("\n", "&#10;");
    }
})();
