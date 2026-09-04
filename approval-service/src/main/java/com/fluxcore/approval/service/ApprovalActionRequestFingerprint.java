package com.fluxcore.approval.service;

import com.fluxcore.approval.dto.ApprovalActionRequest;
import com.fluxcore.approval.dto.ApprovalAddSignRequest;
import com.fluxcore.approval.dto.ApprovalTransferRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Builds the stable identity of an approval command.
 *
 * <p>The action request id is only an idempotency key. The fingerprint prevents
 * callers from reusing that key for a different command.</p>
 */
public final class ApprovalActionRequestFingerprint {
    private static final String FORMAT_VERSION = "v1";

    private ApprovalActionRequestFingerprint() {
    }

    public static String approve(long taskId, ApprovalActionRequest request) {
        return fingerprint("APPROVE", request.operatorId(), taskId, request.expectedVersion(),
                null, request.comment());
    }

    public static String reject(long taskId, ApprovalActionRequest request) {
        return fingerprint("REJECT", request.operatorId(), taskId, request.expectedVersion(),
                null, request.comment());
    }

    public static String withdraw(ApprovalActionRequest request) {
        return fingerprint("WITHDRAW", request.operatorId(), null, request.expectedVersion(),
                null, request.comment());
    }

    public static String transfer(long taskId, ApprovalTransferRequest request) {
        return fingerprint("TRANSFER", request.operatorId(), taskId, request.expectedVersion(),
                normalizeTarget(request.targetAssigneeId()), request.comment());
    }

    public static String addSign(long taskId, ApprovalAddSignRequest request) {
        return fingerprint("ADD_SIGN", request.operatorId(), taskId, request.expectedVersion(),
                normalizeTarget(request.additionalAssigneeId()), request.comment());
    }

    private static String fingerprint(String actionType, String operatorId, Long taskId,
                                      Long expectedVersion, String targetAssigneeId, String comment) {
        String canonical = FORMAT_VERSION
                + field(actionType)
                + field(operatorId)
                + field(taskId == null ? null : String.valueOf(taskId))
                + field(expectedVersion == null ? null : String.valueOf(expectedVersion))
                + field(targetAssigneeId)
                + field(comment);
        return sha256(canonical);
    }

    private static String normalizeTarget(String target) {
        return target == null ? null : target.trim();
    }

    private static String field(String value) {
        if (value == null) {
            return "N;";
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return "V" + bytes.length + ":" + value + ";";
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
