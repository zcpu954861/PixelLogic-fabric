package com.pixelmc.pixellogic.server.storage;

import com.pixelmc.pixellogic.core.runtime.RuntimeResult;
import com.pixelmc.pixellogic.core.trace.ExecutionTrace;
import com.pixelmc.pixellogic.server.PixelLogicSpikeService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.require;
import static com.pixelmc.pixellogic.selfcheck.SelfCheckSupport.run;

public final class GraphStorageSelfCheck {
    private GraphStorageSelfCheck() {
    }

    public static void main(String[] args) throws Exception {
        run("graphStorageSelfCheck", () -> {
        Path root = Files.createTempDirectory("pixel-logic-graph-storage-");
        UUID playerId = UUID.nameUUIDFromBytes("pixel-logic-graph-storage-self-check".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try (PixelLogicSpikeService service = new PixelLogicSpikeService(
                (ignored, message) -> {
                },
                Runnable::run,
                ignored -> {
                },
                Duration.ofSeconds(1),
                root
        )) {
            GraphDocument committed = service.committedGraph(GraphStorageService.DEFAULT_GRAPH_ID);
            require(Files.exists(root.resolve("graphs").resolve("committed").resolve("demo-start-flow.json")),
                    "default committed graph should be seeded");
            require(!service.draftExists(GraphStorageService.DEFAULT_GRAPH_ID), "draft should not exist after seed");

            GraphDocument edited = withConfig(committed, "welcome-message", "message", "欢迎进入新流程");
            GraphDocument draft = service.saveDraft(GraphStorageService.DEFAULT_GRAPH_ID, edited);
            require(service.draftExists(GraphStorageService.DEFAULT_GRAPH_ID), "draft should exist after save");
            require(!draft.fingerprint().equals(committed.fingerprint()), "draft fingerprint should change");

            GraphStorageService.ValidationReport validation = service.validateDraft(GraphStorageService.DEFAULT_GRAPH_ID);
            require(validation.valid(), "edited draft should validate");

            GraphStorageService.CommitReport commit = service.commitDraft(GraphStorageService.DEFAULT_GRAPH_ID);
            require(commit.committed(), "valid draft should commit");
            require(!service.draftExists(GraphStorageService.DEFAULT_GRAPH_ID), "commit should clear draft");

            service.resetPlayer(playerId);
            RuntimeResult run = service.startManualTest(playerId);
            ExecutionTrace trace = service.trace(run.traceId()).orElseThrow();
            require(run.success() && trace.containsMessage("欢迎进入新流程"),
                    "runtime should use committed graph after commit");

            String committedFingerprint = service.committedGraph(GraphStorageService.DEFAULT_GRAPH_ID).fingerprint();
            GraphDocument invalid = withConfig(commit.graph(), "condition-started", "key", "");
            service.saveDraft(GraphStorageService.DEFAULT_GRAPH_ID, invalid);
            GraphStorageService.ValidationReport invalidValidation = service.validateDraft(GraphStorageService.DEFAULT_GRAPH_ID);
            require(!invalidValidation.valid(), "invalid draft should fail validation");
            GraphStorageService.CommitReport invalidCommit = service.commitDraft(GraphStorageService.DEFAULT_GRAPH_ID);
            require(!invalidCommit.committed(), "invalid draft must not commit");
            require(service.committedGraph(GraphStorageService.DEFAULT_GRAPH_ID).fingerprint().equals(committedFingerprint),
                    "invalid draft must not replace committed graph");

            service.resetPlayer(playerId);
            RuntimeResult afterInvalid = service.startManualTest(playerId);
            ExecutionTrace afterInvalidTrace = service.trace(afterInvalid.traceId()).orElseThrow();
            require(afterInvalid.success() && afterInvalidTrace.containsMessage("欢迎进入新流程"),
                    "runtime should keep previous committed graph after invalid draft");

            service.resetPlayer(playerId);
            RuntimeResult pendingTimer = service.startManualTest(playerId);
            ExecutionTrace pendingTimerTrace = service.trace(pendingTimer.traceId()).orElseThrow();
            require(pendingTimer.success() && pendingTimerTrace.containsMessage("计时器启动"),
                    "timer run should start before reset");
            service.resetPlayer(playerId);
            Thread.sleep(1200L);
            require(!pendingTimerTrace.containsMessage("计时器完成") && service.pendingTimers() == 0,
                    "reset should invalidate and clear pending timer callbacks");

            try {
                service.committedGraph("../bad");
                throw new IllegalStateException("path traversal graph id should be rejected");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        } finally {
            deleteRecursively(root);
        }
        });
    }

    private static GraphDocument withConfig(GraphDocument document, String nodeId, String key, String value) {
        return new GraphDocument(
                document.schemaVersion(),
                document.id(),
                document.displayName(),
                document.createdAt(),
                document.updatedAt(),
                document.fingerprint(),
                document.nodes().stream().map(node -> {
                    if (!node.id().equals(nodeId)) {
                        return node;
                    }
                    Map<String, String> config = new TreeMap<>(node.config());
                    config.put(key, value);
                    return new GraphDocument.NodeDocument(
                            node.id(),
                            node.type(),
                            node.blockId(),
                            node.parentContainerId(),
                            node.parentSlot(),
                            node.displayName(),
                            config,
                            node.position(),
                            node.slots()
                    );
                }).toList(),
                document.edges(),
                document.triggerEntries()
        );
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (Files.notExists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

}
