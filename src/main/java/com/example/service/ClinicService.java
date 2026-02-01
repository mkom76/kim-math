package com.example.service;

import com.example.dto.*;
import com.example.entity.*;
import com.example.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ClinicService {
    private final ClinicRepository clinicRepository;
    private final ClinicRegistrationRepository clinicRegistrationRepository;
    private final AcademyClassRepository academyClassRepository;
    private final StudentRepository studentRepository;
    private final StudentHomeworkRepository studentHomeworkRepository;
    private final ClinicHomeworkProgressRepository clinicHomeworkProgressRepository;

    /**
     * 이번주 클리닉 생성 (반의 기본 설정 기반)
     */
    public ClinicDto createClinicForThisWeek(Long classId) {
        AcademyClass academyClass = academyClassRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        if (academyClass.getClinicDayOfWeek() == null || academyClass.getClinicTime() == null) {
            throw new RuntimeException("클리닉 요일/시간이 설정되지 않았습니다");
        }

        // Calculate next clinic date based on day of week setting
        LocalDate today = LocalDate.now();
        DayOfWeek targetDay = academyClass.getClinicDayOfWeek();
        LocalDate clinicDate = today.with(TemporalAdjusters.nextOrSame(targetDay));

        // Check if clinic already exists for this date
        Optional<Clinic> existing = clinicRepository.findByClassIdAndDate(classId, clinicDate);
        if (existing.isPresent()) {
            throw new RuntimeException("해당 날짜의 클리닉이 이미 존재합니다");
        }

        Clinic clinic = Clinic.builder()
                .academyClass(academyClass)
                .clinicDate(clinicDate)
                .clinicTime(academyClass.getClinicTime())
                .status(ClinicStatus.OPEN)
                .build();

        clinic = clinicRepository.save(clinic);
        return ClinicDto.from(clinic);
    }

    /**
     * 특정 날짜로 클리닉 생성
     */
    public ClinicDto createClinic(Long classId, LocalDate clinicDate, java.time.LocalTime clinicTime) {
        AcademyClass academyClass = academyClassRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Class not found"));

        Optional<Clinic> existing = clinicRepository.findByClassIdAndDate(classId, clinicDate);
        if (existing.isPresent()) {
            throw new RuntimeException("해당 날짜의 클리닉이 이미 존재합니다");
        }

        Clinic clinic = Clinic.builder()
                .academyClass(academyClass)
                .clinicDate(clinicDate)
                .clinicTime(clinicTime)
                .status(ClinicStatus.OPEN)
                .build();

        clinic = clinicRepository.save(clinic);
        return ClinicDto.from(clinic);
    }

    /**
     * 반별 클리닉 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ClinicDto> getClinicsByClass(Long classId) {
        return clinicRepository.findByAcademyClassIdOrderByClinicDateDesc(classId)
                .stream()
                .map(ClinicDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 오늘 기준 가장 가까운 클리닉 조회
     */
    @Transactional(readOnly = true)
    public Optional<ClinicDto> getUpcomingClinic(Long classId) {
        LocalDate today = LocalDate.now();
        List<Clinic> upcomingClinics = clinicRepository.findUpcomingClinicsByClass(classId, today);

        if (upcomingClinics.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ClinicDto.from(upcomingClinics.get(0)));
    }

    /**
     * 클리닉 상세 조회 (신청자 + 숙제 완성도)
     */
    @Transactional(readOnly = true)
    public ClinicDetailDto getClinicDetail(Long clinicId) {
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new RuntimeException("Clinic not found"));

        // Get all students in the class
        List<Student> students = studentRepository.findAll().stream()
                .filter(s -> s.getAcademyClass() != null &&
                            s.getAcademyClass().getId().equals(clinic.getAcademyClass().getId()))
                .collect(Collectors.toList());

        // Get all registrations for this clinic
        List<ClinicRegistration> registrations = clinicRegistrationRepository.findByClinicId(clinicId);
        var registrationMap = registrations.stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), ClinicRegistrationDto::from));

        // Build student list with homework info
        List<ClinicDetailDto.StudentClinicHomeworkDto> studentDtos = students.stream()
                .map(student -> {
                    // 1. Get incomplete homeworks (completion < 100%)
                    List<StudentHomework> allStudentHomeworks = studentHomeworkRepository
                            .findByStudentId(student.getId());

                    List<StudentHomework> incompleteHomeworks = allStudentHomeworks.stream()
                            .filter(sh -> sh.getCompletion() == null || sh.getCompletion() < 100)
                            .collect(Collectors.toList());

                    // 2. Get homework IDs that are being tracked in this clinic
                    List<Long> trackedHomeworkIds = clinicHomeworkProgressRepository
                            .findByClinicIdAndStudentId(clinicId, student.getId()).stream()
                            .map(p -> p.getHomework().getId())
                            .collect(Collectors.toList());

                    // 3. Add tracked homeworks that reached 100% (not in incomplete list)
                    List<StudentHomework> displayHomeworks = new ArrayList<>(incompleteHomeworks);
                    allStudentHomeworks.stream()
                            .filter(sh -> trackedHomeworkIds.contains(sh.getHomework().getId()))
                            .filter(sh -> displayHomeworks.stream()
                                    .noneMatch(dh -> dh.getHomework().getId().equals(sh.getHomework().getId())))
                            .forEach(displayHomeworks::add);

                    List<ClinicDetailDto.HomeworkProgressDto> homeworkDtos = displayHomeworks.stream()
                            .map(sh -> ClinicDetailDto.HomeworkProgressDto.builder()
                                    .homeworkId(sh.getHomework().getId())
                                    .homeworkTitle(sh.getHomework().getTitle())
                                    .questionCount(sh.getHomework().getQuestionCount())
                                    .incorrectCount(sh.getIncorrectCount())
                                    .unsolvedCount(sh.getUnsolvedCount())
                                    .completion(sh.getCompletion())
                                    .lessonId(sh.getHomework().getLesson() != null ?
                                             sh.getHomework().getLesson().getId() : null)
                                    .lessonDate(sh.getHomework().getLesson() != null ?
                                               sh.getHomework().getLesson().getLessonDate().toString() : null)
                                    .build())
                            .collect(Collectors.toList());

                    return ClinicDetailDto.StudentClinicHomeworkDto.builder()
                            .studentId(student.getId())
                            .studentName(student.getName())
                            .registration(registrationMap.get(student.getId()))
                            .homeworks(homeworkDtos)
                            .build();
                })
                .collect(Collectors.toList());

        return ClinicDetailDto.builder()
                .clinic(ClinicDto.from(clinic))
                .students(studentDtos)
                .build();
    }

    /**
     * 학생이 클리닉 신청
     */
    public ClinicRegistrationDto registerForClinic(Long clinicId, Long studentId) {
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new RuntimeException("Clinic not found"));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // Check if clinic is open
        if (clinic.getStatus() != ClinicStatus.OPEN) {
            throw new RuntimeException("클리닉 신청이 마감되었습니다");
        }

        // Check if clinic date has passed
        if (clinic.getClinicDate().isBefore(LocalDate.now())) {
            throw new RuntimeException("지난 클리닉에는 신청할 수 없습니다");
        }

        // Check if already registered
        Optional<ClinicRegistration> existing = clinicRegistrationRepository
                .findByClinicIdAndStudentId(clinicId, studentId);
        if (existing.isPresent()) {
            if (existing.get().getStatus() == ClinicRegistrationStatus.CANCELLED) {
                // Re-register
                existing.get().setStatus(ClinicRegistrationStatus.REGISTERED);
                return ClinicRegistrationDto.from(clinicRegistrationRepository.save(existing.get()));
            }
            throw new RuntimeException("이미 신청되었습니다");
        }

        ClinicRegistration registration = ClinicRegistration.builder()
                .clinic(clinic)
                .student(student)
                .status(ClinicRegistrationStatus.REGISTERED)
                .build();

        registration = clinicRegistrationRepository.save(registration);
        return ClinicRegistrationDto.from(registration);
    }

    /**
     * 신청 취소
     */
    public void cancelRegistration(Long clinicId, Long studentId) {
        ClinicRegistration registration = clinicRegistrationRepository
                .findByClinicIdAndStudentId(clinicId, studentId)
                .orElseThrow(() -> new RuntimeException("Registration not found"));

        registration.setStatus(ClinicRegistrationStatus.CANCELLED);
        clinicRegistrationRepository.save(registration);
    }

    /**
     * 참석 체크
     */
    public ClinicRegistrationDto updateAttendance(Long registrationId, ClinicRegistrationStatus status) {
        ClinicRegistration registration = clinicRegistrationRepository.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Registration not found"));

        registration.setStatus(status);
        registration = clinicRegistrationRepository.save(registration);
        return ClinicRegistrationDto.from(registration);
    }

    /**
     * 학생용 클리닉 정보 조회 (완성도 체크 포함)
     */
    @Transactional(readOnly = true)
    public StudentClinicInfoDto getStudentClinicInfo(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        // Get upcoming clinic for student's class
        Optional<ClinicDto> upcomingClinic = getUpcomingClinic(student.getAcademyClass().getId());

        if (upcomingClinic.isEmpty()) {
            return StudentClinicInfoDto.builder()
                    .upcomingClinic(null)
                    .myRegistration(null)
                    .shouldAttend(false)
                    .incompleteHomeworks(new ArrayList<>())
                    .build();
        }

        // Get registration status
        Optional<ClinicRegistration> registration = clinicRegistrationRepository
                .findByClinicIdAndStudentId(upcomingClinic.get().getId(), studentId);

        // Get incomplete homeworks (completion < 100% or not submitted)
        List<StudentHomework> incompleteHomeworks = studentHomeworkRepository
                .findByStudentId(studentId).stream()
                .filter(sh -> sh.getCompletion() == null || sh.getCompletion() < 100)
                .collect(Collectors.toList());

        List<StudentClinicInfoDto.IncompleteHomeworkDto> homeworkDtos = incompleteHomeworks.stream()
                .map(sh -> StudentClinicInfoDto.IncompleteHomeworkDto.builder()
                        .homeworkId(sh.getHomework().getId())
                        .homeworkTitle(sh.getHomework().getTitle())
                        .completion(sh.getCompletion())
                        .lessonDate(sh.getHomework().getLesson() != null ?
                                   sh.getHomework().getLesson().getLessonDate().toString() : null)
                        .build())
                .collect(Collectors.toList());

        return StudentClinicInfoDto.builder()
                .upcomingClinic(upcomingClinic.get())
                .myRegistration(registration.map(ClinicRegistrationDto::from).orElse(null))
                .shouldAttend(!incompleteHomeworks.isEmpty())
                .incompleteHomeworks(homeworkDtos)
                .build();
    }

    /**
     * 클리닉 종료
     */
    public ClinicDto closeClinic(Long clinicId) {
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new RuntimeException("Clinic not found"));

        clinic.setStatus(ClinicStatus.CLOSED);
        clinic = clinicRepository.save(clinic);
        return ClinicDto.from(clinic);
    }

    /**
     * 클리닉 삭제
     */
    public void deleteClinic(Long clinicId) {
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new RuntimeException("Clinic not found"));

        // Check if there are any registrations
        if (!clinic.getRegistrations().isEmpty()) {
            throw new RuntimeException("신청자가 있는 클리닉은 삭제할 수 없습니다");
        }

        clinicRepository.deleteById(clinicId);
    }

    /**
     * 클리닉 시작 - 모든 학생의 미완성 숙제 상태를 "전" 스냅샷으로 저장
     */
    public void startClinic(Long clinicId) {
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new RuntimeException("Clinic not found"));

        // Get all students in the class
        List<Student> students = studentRepository.findAll().stream()
                .filter(s -> s.getAcademyClass() != null &&
                            s.getAcademyClass().getId().equals(clinic.getAcademyClass().getId()))
                .collect(Collectors.toList());

        // For each student, snapshot all incomplete homeworks (completion < 100%)
        List<ClinicHomeworkProgress> progressList = new ArrayList<>();

        for (Student student : students) {
            List<StudentHomework> incompleteHomeworks = studentHomeworkRepository
                    .findByStudentId(student.getId()).stream()
                    .filter(sh -> sh.getCompletion() == null || sh.getCompletion() < 100)
                    .collect(Collectors.toList());

            for (StudentHomework sh : incompleteHomeworks) {
                // Check if progress already exists
                Optional<ClinicHomeworkProgress> existing = clinicHomeworkProgressRepository
                        .findByClinicIdAndStudentIdAndHomeworkId(clinicId, student.getId(), sh.getHomework().getId());

                ClinicHomeworkProgress progress;
                if (existing.isPresent()) {
                    // Update existing
                    progress = existing.get();
                } else {
                    // Create new
                    progress = ClinicHomeworkProgress.builder()
                            .clinic(clinic)
                            .student(student)
                            .homework(sh.getHomework())
                            .build();
                }

                // Save "before" snapshot
                progress.setIncorrectCountBefore(sh.getIncorrectCount());
                progress.setUnsolvedCountBefore(sh.getUnsolvedCount());

                progressList.add(progress);
            }
        }

        clinicHomeworkProgressRepository.saveAll(progressList);
    }

    /**
     * 클리닉 종료 - 진행 추적 중인 모든 숙제의 "후" 스냅샷 저장
     */
    public void endClinic(Long clinicId) {
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new RuntimeException("Clinic not found"));

        // Set clinic status to CLOSED
        clinic.setStatus(ClinicStatus.CLOSED);
        clinicRepository.save(clinic);

        // Get all progress records for this clinic
        List<ClinicHomeworkProgress> progressList = clinicHomeworkProgressRepository.findByClinicId(clinicId);

        // Update "after" snapshot for each tracked homework
        for (ClinicHomeworkProgress progress : progressList) {
            Long studentId = progress.getStudent().getId();
            Long homeworkId = progress.getHomework().getId();

            // Get current homework state
            Optional<StudentHomework> currentHomework = studentHomeworkRepository
                    .findByStudentId(studentId).stream()
                    .filter(sh -> sh.getHomework().getId().equals(homeworkId))
                    .findFirst();

            if (currentHomework.isPresent()) {
                StudentHomework sh = currentHomework.get();
                // Save "after" snapshot (even if completion is 100%)
                progress.setIncorrectCountAfter(sh.getIncorrectCount());
                progress.setUnsolvedCountAfter(sh.getUnsolvedCount());
            }
        }

        clinicHomeworkProgressRepository.saveAll(progressList);
    }

    /**
     * 클리닉 진행 상황 조회
     */
    @Transactional(readOnly = true)
    public List<ClinicHomeworkProgressDto> getClinicProgress(Long clinicId) {
        return clinicHomeworkProgressRepository.findByClinicId(clinicId).stream()
                .map(ClinicHomeworkProgressDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 학생의 최근 클리닉 결과 조회
     */
    @Transactional(readOnly = true)
    public Optional<RecentClinicResultDto> getRecentClinicResult(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        if (student.getAcademyClass() == null) {
            return Optional.empty();
        }

        // Get all CLOSED clinics for the student's class
        List<Clinic> closedClinics = clinicRepository
                .findByAcademyClassIdOrderByClinicDateDesc(student.getAcademyClass().getId()).stream()
                .filter(c -> c.getStatus() == ClinicStatus.CLOSED)
                .collect(Collectors.toList());

        // Find the most recent clinic where student ATTENDED
        Optional<Clinic> recentClinic = closedClinics.stream()
                .filter(clinic -> {
                    Optional<ClinicRegistration> reg = clinicRegistrationRepository
                            .findByClinicIdAndStudentId(clinic.getId(), studentId);
                    return reg.isPresent() && reg.get().getStatus() == ClinicRegistrationStatus.ATTENDED;
                })
                .findFirst();

        if (recentClinic.isEmpty()) {
            return Optional.empty();
        }

        Clinic clinic = recentClinic.get();

        // Get clinic homework progress for this student
        List<ClinicHomeworkProgress> progressList = clinicHomeworkProgressRepository
                .findByClinicIdAndStudentId(clinic.getId(), studentId);

        if (progressList.isEmpty()) {
            return Optional.empty();
        }

        // Build homework DTOs
        List<RecentClinicResultDto.RecentClinicHomeworkDto> homeworkDtos = new ArrayList<>();
        int totalIncorrectBefore = 0;
        int totalIncorrectAfter = 0;
        int totalUnsolvedBefore = 0;
        int totalUnsolvedAfter = 0;
        int improvedCount = 0;

        for (ClinicHomeworkProgress progress : progressList) {
            int incorrectBefore = progress.getIncorrectCountBefore() != null ? progress.getIncorrectCountBefore() : 0;
            int incorrectAfter = progress.getIncorrectCountAfter() != null ? progress.getIncorrectCountAfter() : 0;
            int unsolvedBefore = progress.getUnsolvedCountBefore() != null ? progress.getUnsolvedCountBefore() : 0;
            int unsolvedAfter = progress.getUnsolvedCountAfter() != null ? progress.getUnsolvedCountAfter() : 0;

            int incorrectChange = incorrectAfter - incorrectBefore;
            int unsolvedChange = unsolvedAfter - unsolvedBefore;

            // 오답 또는 안 푼 문제가 감소했으면 개선된 것으로 카운트
            if (incorrectChange < 0 || unsolvedChange < 0) {
                improvedCount++;
            }

            totalIncorrectBefore += incorrectBefore;
            totalIncorrectAfter += incorrectAfter;
            totalUnsolvedBefore += unsolvedBefore;
            totalUnsolvedAfter += unsolvedAfter;

            homeworkDtos.add(RecentClinicResultDto.RecentClinicHomeworkDto.builder()
                    .homeworkId(progress.getHomework().getId())
                    .homeworkTitle(progress.getHomework().getTitle())
                    .incorrectCountBefore(incorrectBefore)
                    .incorrectCountAfter(incorrectAfter)
                    .incorrectCountChange(incorrectChange)
                    .unsolvedCountBefore(unsolvedBefore)
                    .unsolvedCountAfter(unsolvedAfter)
                    .unsolvedCountChange(unsolvedChange)
                    .build());
        }

        return Optional.of(RecentClinicResultDto.builder()
                .clinicId(clinic.getId())
                .clinicDate(clinic.getClinicDate().toString())
                .clinicTime(clinic.getClinicTime().toString())
                .improvedHomeworkCount(improvedCount)
                .totalIncorrectCountBefore(totalIncorrectBefore)
                .totalIncorrectCountAfter(totalIncorrectAfter)
                .totalIncorrectCountChange(totalIncorrectAfter - totalIncorrectBefore)
                .totalUnsolvedCountBefore(totalUnsolvedBefore)
                .totalUnsolvedCountAfter(totalUnsolvedAfter)
                .totalUnsolvedCountChange(totalUnsolvedAfter - totalUnsolvedBefore)
                .homeworks(homeworkDtos)
                .build());
    }
}
