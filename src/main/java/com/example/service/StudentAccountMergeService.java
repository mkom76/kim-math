package com.example.service;

import com.example.config.security.TenantContext;
import com.example.dto.StudentAccountMergeCandidateDto;
import com.example.dto.StudentAccountMergeConflictDto;
import com.example.dto.StudentAccountMergeImpactDto;
import com.example.dto.StudentAccountMergePreviewDto;
import com.example.dto.StudentAccountMergeRequest;
import com.example.dto.StudentAccountMergeResultDto;
import com.example.dto.StudentMergeEnrollmentDto;
import com.example.dto.StudentMergeStudentDto;
import com.example.entity.Student;
import com.example.entity.StudentAccountMerge;
import com.example.entity.StudentClassEnrollment;
import com.example.entity.StudentClassEnrollmentStatus;
import com.example.repository.StudentAccountMergeRepository;
import com.example.repository.StudentClassEnrollmentRepository;
import com.example.repository.StudentRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentAccountMergeService {

    private static final String MOVE = "MOVE";
    private static final String DELETE = "DELETE";

    /**
     * This fixed inventory is intentionally explicit: adding another student-owned table must also
     * add one entry here so preview and execution cannot silently diverge.
     */
    private static final List<StudentReference> STUDENT_REFERENCES = List.of(
            new StudentReference("enrollments", "반 소속", "student_class_enrollments", "class_id", null),
            new StudentReference("lessons", "출결·수업 피드백", "student_lessons", "lesson_id", null),
            new StudentReference("homeworks", "숙제 기록", "student_homeworks", "homework_id", null),
            new StudentReference("submissions", "시험 제출", "student_submissions", "test_id", null),
            new StudentReference("videoProgress", "영상 진도", "student_video_progress", "lesson_video_id", null),
            new StudentReference("clinicRegistrations", "클리닉 신청", "clinic_registrations", "clinic_id", null),
            new StudentReference("clinicHomeworkProgress", "클리닉 숙제 기록", "clinic_homework_progress",
                    "clinic_id, homework_id", null),
            new StudentReference("consents", "개인정보 동의 이력", "student_consents", null, null),
            new StudentReference("deviceTokens", "푸시 기기", "device_tokens", null, null),
            new StudentReference("notifications", "알림", "student_notifications", "source_key",
                    "source_key IS NOT NULL"),
            new StudentReference("uiFeedback", "UI 의견", "student_ui_feedback", null, null)
    );

    private final StudentRepository studentRepository;
    private final StudentClassEnrollmentRepository enrollmentRepository;
    private final StudentAccountMergeRepository mergeRepository;
    private final AuthorizationService authorizationService;
    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<StudentAccountMergeCandidateDto> getCandidates() {
        TenantContext.Context context = requireAdmin();
        List<Student> students = studentRepository
                .findByAcademy_IdOrderByCreatedAtAscIdAsc(context.academyId());
        if (students.size() < 2) {
            return List.of();
        }

        DisjointSet groups = new DisjointSet(students.size());
        for (int left = 0; left < students.size(); left++) {
            for (int right = left + 1; right < students.size(); right++) {
                if (!matchReasons(students.get(left), students.get(right)).isEmpty()) {
                    groups.union(left, right);
                }
            }
        }

        Map<Integer, List<Student>> grouped = new LinkedHashMap<>();
        for (int index = 0; index < students.size(); index++) {
            grouped.computeIfAbsent(groups.find(index), ignored -> new ArrayList<>())
                    .add(students.get(index));
        }

        Map<Long, List<StudentMergeEnrollmentDto>> enrollments = enrollmentMap(
                students.stream().map(Student::getId).toList());

        return grouped.values().stream()
                .filter(group -> group.size() > 1)
                .map(group -> new StudentAccountMergeCandidateDto(
                        group.stream().map(student -> toDto(student, enrollments)).toList(),
                        reasonsForGroup(group)))
                .sorted(Comparator.comparing(candidate -> candidate.students().get(0).id()))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentAccountMergePreviewDto preview(StudentAccountMergeRequest request) {
        Selection selection = loadSelection(request, false);
        return buildPreview(selection);
    }

    @Transactional
    public StudentAccountMergeResultDto merge(StudentAccountMergeRequest request) {
        Selection selection = loadSelection(request, true);
        StudentAccountMergePreviewDto preview = buildPreview(selection);
        if (!preview.mergeable()) {
            throw new IllegalArgumentException("겹치는 학습 기록이 있어 통합할 수 없습니다. 충돌 항목을 먼저 정리해주세요");
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("targetId", selection.target().getId())
                .addValue("sourceIds", selection.sources().stream().map(Student::getId).toList());
        for (StudentReference reference : STUDENT_REFERENCES) {
            jdbc.update("UPDATE " + reference.table() +
                    " SET student_id = :targetId WHERE student_id IN (:sourceIds)", parameters);
        }
        jdbc.update("DELETE FROM remember_me_tokens " +
                "WHERE user_role = 'STUDENT' AND user_id IN (:sourceIds)", parameters);

        LocalDateTime mergedAt = LocalDateTime.now();
        List<StudentAccountMerge> audits = selection.sources().stream()
                .map(source -> StudentAccountMerge.builder()
                        .academyId(selection.context().academyId())
                        .sourceStudentId(source.getId())
                        .targetStudentId(selection.target().getId())
                        .mergedByTeacherId(selection.context().teacherId())
                        .mergedAt(mergedAt)
                        .build())
                .toList();
        mergeRepository.saveAllAndFlush(audits);

        int deleted = jdbc.update("DELETE FROM students WHERE id IN (:sourceIds)", parameters);
        if (deleted != selection.sources().size()) {
            throw new IllegalStateException("통합할 학생 계정을 모두 정리하지 못했습니다");
        }
        entityManager.clear();

        return new StudentAccountMergeResultDto(
                selection.target().getId(),
                selection.sources().stream().map(Student::getId).toList(),
                preview.impacts());
    }

    private StudentAccountMergePreviewDto buildPreview(Selection selection) {
        List<Long> sourceIds = selection.sources().stream().map(Student::getId).toList();
        List<Long> allIds = new ArrayList<>(sourceIds);
        allIds.add(selection.target().getId());
        MapSqlParameterSource sourceParameters = new MapSqlParameterSource("sourceIds", sourceIds);
        MapSqlParameterSource allParameters = new MapSqlParameterSource("studentIds", allIds);

        List<StudentAccountMergeImpactDto> impacts = new ArrayList<>();
        List<StudentAccountMergeConflictDto> conflicts = new ArrayList<>();
        for (StudentReference reference : STUDENT_REFERENCES) {
            long count = count("SELECT COUNT(*) FROM " + reference.table() +
                    " WHERE student_id IN (:sourceIds)", sourceParameters);
            impacts.add(new StudentAccountMergeImpactDto(reference.key(), reference.label(), count, MOVE));

            if (reference.conflictColumns() != null) {
                String extraWhere = reference.conflictWhere() == null
                        ? "" : " AND " + reference.conflictWhere();
                long conflictCount = count("SELECT COUNT(*) FROM (SELECT " + reference.conflictColumns() +
                        " FROM " + reference.table() +
                        " WHERE student_id IN (:studentIds)" + extraWhere +
                        " GROUP BY " + reference.conflictColumns() +
                        " HAVING COUNT(DISTINCT student_id) > 1) merge_conflicts", allParameters);
                if (conflictCount > 0) {
                    conflicts.add(new StudentAccountMergeConflictDto(
                            reference.key(), reference.label(), conflictCount));
                }
            }
        }

        long rememberTokenCount = count("SELECT COUNT(*) FROM remember_me_tokens " +
                "WHERE user_role = 'STUDENT' AND user_id IN (:sourceIds)", sourceParameters);
        impacts.add(new StudentAccountMergeImpactDto(
                "rememberMeTokens", "자동 로그인 토큰", rememberTokenCount, DELETE));

        Map<Long, List<StudentMergeEnrollmentDto>> enrollments = enrollmentMap(allIds);
        return new StudentAccountMergePreviewDto(
                toDto(selection.target(), enrollments),
                selection.sources().stream().map(student -> toDto(student, enrollments)).toList(),
                impacts,
                conflicts,
                conflicts.isEmpty());
    }

    private Selection loadSelection(StudentAccountMergeRequest request, boolean lock) {
        TenantContext.Context context = requireAdmin();
        if (request == null || request.targetStudentId() == null
                || request.sourceStudentIds() == null || request.sourceStudentIds().isEmpty()) {
            throw new IllegalArgumentException("대표 계정과 통합할 계정을 선택해주세요");
        }

        List<Long> sourceIds = request.sourceStudentIds();
        if (sourceIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("학생 ID가 올바르지 않습니다");
        }
        List<Long> distinctSourceIds = sourceIds.stream().distinct().sorted().toList();
        if (distinctSourceIds.size() != sourceIds.size()) {
            throw new IllegalArgumentException("통합할 학생 계정이 중복되었습니다");
        }
        if (distinctSourceIds.contains(request.targetStudentId())) {
            throw new IllegalArgumentException("대표 계정은 통합 대상에 포함할 수 없습니다");
        }

        List<Long> allIds = new ArrayList<>(distinctSourceIds);
        allIds.add(request.targetStudentId());
        allIds.sort(Long::compareTo);
        List<Student> students = lock
                ? studentRepository.findAllLockedById(allIds)
                : studentRepository.findAllById(allIds);
        if (students.size() != allIds.size()) {
            throw new IllegalArgumentException("선택한 학생 계정 중 찾을 수 없는 계정이 있습니다");
        }
        if (students.stream().anyMatch(student -> student.getAcademy() == null
                || !context.academyId().equals(student.getAcademy().getId()))) {
            throw new IllegalArgumentException("같은 학원의 학생 계정만 통합할 수 있습니다");
        }

        Map<Long, Student> byId = students.stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        return new Selection(
                context,
                byId.get(request.targetStudentId()),
                distinctSourceIds.stream().map(byId::get).toList());
    }

    private TenantContext.Context requireAdmin() {
        authorizationService.assertIsAcademyAdmin();
        TenantContext.Context context = TenantContext.current();
        if (context == null || context.academyId() == null || context.teacherId() == null) {
            throw new IllegalArgumentException("활성 학원 정보를 확인할 수 없습니다");
        }
        return context;
    }

    private Map<Long, List<StudentMergeEnrollmentDto>> enrollmentMap(List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        List<StudentClassEnrollment> rows = enrollmentRepository
                .findByStudentIdInOrderByStartedAtDescIdDesc(studentIds);
        Map<Long, List<StudentMergeEnrollmentDto>> result = new HashMap<>();
        for (StudentClassEnrollment row : rows) {
            result.computeIfAbsent(row.getStudent().getId(), ignored -> new ArrayList<>())
                    .add(new StudentMergeEnrollmentDto(
                            row.getAcademyClass().getId(),
                            row.getAcademyClass().getName(),
                            row.getStatus()));
        }
        return result;
    }

    private StudentMergeStudentDto toDto(
            Student student,
            Map<Long, List<StudentMergeEnrollmentDto>> enrollments) {
        List<StudentMergeEnrollmentDto> memberships = enrollments.get(student.getId());
        if (memberships == null || memberships.isEmpty()) {
            StudentClassEnrollmentStatus legacyStatus = student.getAcademyClass().isEnded()
                    ? StudentClassEnrollmentStatus.COMPLETED
                    : StudentClassEnrollmentStatus.ACTIVE;
            memberships = List.of(new StudentMergeEnrollmentDto(
                    student.getAcademyClass().getId(),
                    student.getAcademyClass().getName(),
                    legacyStatus));
        }
        return new StudentMergeStudentDto(
                student.getId(),
                student.getName(),
                student.getGrade(),
                student.getSchool(),
                student.getParentName(),
                maskPhone(student.getParentPhone()),
                maskPhone(student.getContactPhone()),
                student.getStatus(),
                memberships,
                student.getCreatedAt());
    }

    private List<String> reasonsForGroup(List<Student> group) {
        Set<String> reasons = new LinkedHashSet<>();
        for (int left = 0; left < group.size(); left++) {
            for (int right = left + 1; right < group.size(); right++) {
                reasons.addAll(matchReasons(group.get(left), group.get(right)));
            }
        }
        return List.copyOf(reasons);
    }

    private List<String> matchReasons(Student left, Student right) {
        if (!samePresent(normalizeText(left.getName()), normalizeText(right.getName()))) {
            return List.of();
        }
        List<String> reasons = new ArrayList<>();
        if (samePresent(normalizePhone(left.getContactPhone()), normalizePhone(right.getContactPhone()))) {
            reasons.add("학생명·학생 전화번호 일치");
        }
        if (samePresent(normalizePhone(left.getParentPhone()), normalizePhone(right.getParentPhone()))) {
            reasons.add("학생명·보호자 전화번호 일치");
        }
        if (samePresent(normalizeText(left.getSchool()), normalizeText(right.getSchool()))
                && samePresent(normalizeText(left.getGrade()), normalizeText(right.getGrade()))) {
            reasons.add("학생명·학교·학년 일치");
        }
        return reasons;
    }

    private long count(String sql, MapSqlParameterSource parameters) {
        Long value = jdbc.queryForObject(sql, parameters, Long.class);
        return value == null ? 0 : value;
    }

    private static boolean samePresent(String left, String right) {
        return left != null && !left.isEmpty() && left.equals(right);
    }

    private static String normalizeText(String raw) {
        if (raw == null) return null;
        return Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}]", "");
    }

    private static String normalizePhone(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        return digits.length() >= 8 ? digits : null;
    }

    private static String maskPhone(String raw) {
        String digits = normalizePhone(raw);
        return digits == null ? null : "****-" + digits.substring(digits.length() - 4);
    }

    private record StudentReference(
            String key,
            String label,
            String table,
            String conflictColumns,
            String conflictWhere) {
    }

    private record Selection(
            TenantContext.Context context,
            Student target,
            List<Student> sources) {
    }

    private static final class DisjointSet {
        private final int[] parent;

        private DisjointSet(int size) {
            parent = new int[size];
            for (int index = 0; index < size; index++) {
                parent[index] = index;
            }
        }

        private int find(int value) {
            if (parent[value] != value) {
                parent[value] = find(parent[value]);
            }
            return parent[value];
        }

        private void union(int left, int right) {
            int leftRoot = find(left);
            int rightRoot = find(right);
            if (leftRoot != rightRoot) {
                parent[rightRoot] = leftRoot;
            }
        }
    }
}
