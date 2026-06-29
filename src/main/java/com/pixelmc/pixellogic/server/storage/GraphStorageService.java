package com.pixelmc.pixellogic.server.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.pixelmc.pixellogic.core.graph.GraphValidator;
import com.pixelmc.pixellogic.core.graph.ValidationIssue;
import com.pixelmc.pixellogic.core.model.GraphDefinition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class GraphStorageService {
    public static final String DEFAULT_GRAPH_ID = "demo-start-flow";
    public static final String DEFAULT_DISPLAY_NAME = "Demo 开始流程";

    private static final Pattern SAFE_GRAPH_ID = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path root;
    private final GraphValidator validator = new GraphValidator();

    public GraphStorageService(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public synchronized GraphDocument ensureCommitted(GraphDefinition seedGraph, String displayName) throws IOException {
        validateGraphId(seedGraph.id());
        Path path = committedPath(seedGraph.id());
        if (Files.notExists(path)) {
            GraphDocument seeded = GraphDocument.fromGraphDefinition(seedGraph, displayName);
            writeDocument(path, withFingerprint(seeded.normalized(seedGraph.id(), displayName, seeded.createdAt(), Instant.now().toString())));
        }
        return loadCommitted(seedGraph.id());
    }

    public synchronized List<GraphSummary> listGraphs() throws IOException {
        Path committedDir = committedDir();
        if (Files.notExists(committedDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(committedDir)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                    .filter(GraphStorageService::isSafeGraphId)
                    .map(id -> {
                        try {
                            GraphDocument graph = loadCommitted(id);
                            return new GraphSummary(graph.id(), graph.displayName(), graph.fingerprint(), draftExists(id));
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    public synchronized GraphDocument loadCommitted(String graphId) throws IOException {
        return readDocument(committedPath(graphId));
    }

    public synchronized Optional<GraphDocument> loadDraft(String graphId) throws IOException {
        Path path = draftPath(graphId);
        if (Files.notExists(path)) {
            return Optional.empty();
        }
        return Optional.of(readDocument(path));
    }

    public synchronized GraphDocument saveDraft(String graphId, GraphDocument draft) throws IOException {
        validateGraphId(graphId);
        if (draft == null) {
            throw new IllegalArgumentException("graph document is required");
        }
        draft.toGraphDefinition();
        String now = Instant.now().toString();
        String createdAt = draft.createdAt() == null || draft.createdAt().isBlank() ? now : draft.createdAt();
        GraphDocument normalized = withFingerprint(draft.normalized(graphId, DEFAULT_DISPLAY_NAME, createdAt, now));
        writeDocument(draftPath(graphId), normalized);
        return normalized;
    }

    public synchronized ValidationReport validateDraft(String graphId) throws IOException {
        GraphDocument draft = loadDraft(graphId)
                .orElseThrow(() -> new IllegalStateException("草稿不存在，请先保存草稿。"));
        return validate(draft);
    }

    public synchronized CommitReport commitDraft(String graphId) throws IOException {
        GraphDocument draft = loadDraft(graphId)
                .orElseThrow(() -> new IllegalStateException("草稿不存在，请先保存草稿。"));
        ValidationReport validation = validate(draft);
        if (!validation.valid()) {
            return new CommitReport(false, draft, validation);
        }

        String createdAt = loadCommitted(graphId).createdAt();
        GraphDocument committed = withFingerprint(draft.normalized(graphId, DEFAULT_DISPLAY_NAME, createdAt, Instant.now().toString()));
        writeDocument(committedPath(graphId), committed);
        Files.deleteIfExists(draftPath(graphId));
        return new CommitReport(true, committed, validation);
    }

    public synchronized ValidationReport validate(GraphDocument document) {
        GraphDefinition graph = document.toGraphDefinition();
        List<ValidationIssue> issues = List.copyOf(validator.validate(graph));
        return new ValidationReport(!validator.hasErrors(issues), issues);
    }

    public synchronized boolean draftExists(String graphId) {
        return Files.exists(draftPath(graphId));
    }

    public Path root() {
        return root;
    }

    public static void validateGraphId(String graphId) {
        if (!isSafeGraphId(graphId)) {
            throw new IllegalArgumentException("graph id 只能包含字母、数字、下划线和短横线。");
        }
    }

    private static boolean isSafeGraphId(String graphId) {
        return graphId != null && SAFE_GRAPH_ID.matcher(graphId).matches();
    }

    private GraphDocument readDocument(Path path) throws IOException {
        try {
            GraphDocument document = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), GraphDocument.class);
            if (document == null) {
                throw new IllegalArgumentException("graph JSON 为空。");
            }
            document.toGraphDefinition();
            return document;
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("graph JSON 无法解析。", exception);
        }
    }

    private GraphDocument withFingerprint(GraphDocument document) {
        String fingerprint = fingerprint(document);
        return new GraphDocument(
                document.schemaVersion(),
                document.id(),
                document.displayName(),
                document.createdAt(),
                document.updatedAt(),
                fingerprint,
                document.nodes(),
                document.edges(),
                document.triggerEntries()
        );
    }

    private static String fingerprint(GraphDocument document) {
        GraphDocument withoutFingerprint = new GraphDocument(
                document.schemaVersion(),
                document.id(),
                document.displayName(),
                document.createdAt(),
                document.updatedAt(),
                "",
                document.nodes(),
                document.edges(),
                document.triggerEntries()
        );
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(GSON.toJson(withoutFingerprint).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private void writeDocument(Path path, GraphDocument document) throws IOException {
        Path safePath = requireUnderRoot(path);
        Files.createDirectories(safePath.getParent());
        Path temp = Files.createTempFile(safePath.getParent(), safePath.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, safePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, safePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Path committedPath(String graphId) {
        validateGraphId(graphId);
        return requireUnderRoot(committedDir().resolve(graphId + ".json"));
    }

    private Path draftPath(String graphId) {
        validateGraphId(graphId);
        return requireUnderRoot(draftDir().resolve(graphId + ".json"));
    }

    private Path committedDir() {
        return root.resolve("graphs").resolve("committed");
    }

    private Path draftDir() {
        return root.resolve("graphs").resolve("drafts");
    }

    private Path requireUnderRoot(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalArgumentException("graph storage path escaped pixellogic root");
        }
        return normalized;
    }

    public record ValidationReport(boolean valid, List<ValidationIssue> issues) {
    }

    public record CommitReport(boolean committed, GraphDocument graph, ValidationReport validation) {
    }

    public record GraphSummary(String id, String displayName, String fingerprint, boolean hasDraft) {
    }
}
