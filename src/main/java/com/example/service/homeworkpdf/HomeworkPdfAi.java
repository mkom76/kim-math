package com.example.service.homeworkpdf;

import java.util.List;
import java.util.Map;

public interface HomeworkPdfAi {
    boolean ready();
    String model();
    PdfTypes.Analysis analyze(byte[] pdf, List<Map<String,Object>> candidates);
}
