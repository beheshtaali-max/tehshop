package dev.dev7.lib.v2ray.interfaces;

/** Receives progress from a batch of real HTTP delay tests through sing-box outbounds. */
public interface RealDelayTestListener {
    void onProgress(
            int completed,
            int total,
            String testedServerId,
            long testedDelayMs,
            String bestServerId,
            long bestDelayMs
    );

    void onCompleted(String bestServerId, long bestDelayMs);

    void onCancelled();

    void onError(String message);
}
