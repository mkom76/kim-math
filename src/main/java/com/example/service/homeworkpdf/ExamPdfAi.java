package com.example.service.homeworkpdf;

import java.util.List;
import java.util.Map;

public interface ExamPdfAi {
    boolean ready();
    String model();
    ExamPdfTypes.Analysis analyze(byte[] pdf, List<Map<String,Object>> candidates, int questionPageCount);
}
