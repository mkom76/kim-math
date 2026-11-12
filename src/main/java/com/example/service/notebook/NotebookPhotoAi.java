package com.example.service.notebook;

import com.example.service.notebook.NotebookTypes.Solution;

public interface NotebookPhotoAi {
    record Draft(String question, Solution solution) {}
    boolean ready();
    Draft solve(byte[] photo);
    boolean verifyMath(byte[] photo, Draft draft);
    byte[] render(Draft draft);
    boolean verifyImage(byte[] image, Draft draft);
    default NotebookVerification.Report verifyMathReport(byte[] photo, Draft draft) {
        return NotebookVerification.legacy(NotebookVerification.Stage.MATH, verifyMath(photo, draft));
    }
    default NotebookVerification.Report verifyImageReport(byte[] image, Draft draft) {
        return NotebookVerification.legacy(NotebookVerification.Stage.IMAGE, verifyImage(image, draft));
    }
}
