package com.pixelmc.pixellogic.core.graph;

public record ValidationIssue(Severity severity, String code, String message) {
    public enum Severity {
        ERROR,
        WARNING
    }
}
