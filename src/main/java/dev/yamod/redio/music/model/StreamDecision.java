package dev.yamod.redio.music.model;

public record StreamDecision(StreamAccess access, DownloadOption option, String explanation) {
    public StreamDecision {
        explanation = explanation == null ? "" : explanation;
    }
}
