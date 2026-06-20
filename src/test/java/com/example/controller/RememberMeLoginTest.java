package com.example.controller;

import com.example.entity.Academy;
import com.example.entity.AcademyClass;
import com.example.entity.Student;
import com.example.repository.AcademyClassRepository;
import com.example.repository.AcademyRepository;
import com.example.repository.StudentRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RememberMeLoginTest {

    @Autowired MockMvc mockMvc;
    @Autowired AcademyRepository academyRepository;
    @Autowired AcademyClassRepository academyClassRepository;
    @Autowired StudentRepository studentRepository;

    @Test
    void remember_me_cookie_restores_student_session_after_session_is_gone() throws Exception {
        Academy academy = academyRepository.save(Academy.builder().name("자동학원").build());
        AcademyClass clazz = academyClassRepository.save(AcademyClass.builder()
                .name("자동반")
                .academy(academy)
                .build());
        Student student = studentRepository.save(Student.builder()
                .name("자동학생")
                .grade("고1")
                .school("테스트고")
                .pin("1111")
                .academy(academy)
                .academyClass(clazz)
                .build());

        MvcResult login = mockMvc.perform(post("/api/auth/student/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentId":%d,"pin":"1111","rememberMe":true}
                                """.formatted(student.getId())))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("KM_REMEMBER"))
                .andReturn();

        Cookie rememberCookie = login.getResponse().getCookie("KM_REMEMBER");
        assertThat(rememberCookie).isNotNull();

        mockMvc.perform(get("/api/auth/me").cookie(rememberCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(student.getId()))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }
}
