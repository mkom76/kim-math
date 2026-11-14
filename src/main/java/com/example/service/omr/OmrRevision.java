package com.example.service.omr;

import com.example.entity.StudentSubmission;
import com.example.entity.StudentSubmissionDetail;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Comparator;
import java.util.HexFormat;

/** Content snapshot, including essay grades, without adding a database version column. */
public final class OmrRevision {
    private OmrRevision() {}
    public static String of(StudentSubmission submission) {
        if (submission == null) return "new";
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            Object[] parent = {submission.getId(), submission.getUpdatedAt(), submission.getSubmittedAt(),
                    submission.getTotalScore(), submission.getCalculationMistake(), submission.getConceptGap(),
                    submission.getConditionMisread(), submission.getIdeaGap(), submission.getTimeManagement(), submission.isErrorCausesNeedReview()};
            for (Object value : parent) write(out, value);
            for (var detail : submission.getDetails().stream().sorted(Comparator.comparing(StudentSubmissionDetail::getId)).toList()) {
                Object[] fields = {detail.getId(), detail.getQuestion().getId(), detail.getStudentAnswer(),
                        detail.getIsCorrect(), detail.getEarnedPoints(), detail.getTeacherComment(), detail.getUpdatedAt()};
                for (Object value : fields) write(out, value);
            }
            out.flush();
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void write(DataOutputStream out, Object value) throws java.io.IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            byte[] bytes = value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            out.writeInt(bytes.length); out.write(bytes);
        }
    }
}
