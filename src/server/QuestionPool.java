package server;

import java.io.*;
import java.util.*;

public class QuestionPool {
    private final List<String> questions = new ArrayList<>();
    private final List<Character> answers = new ArrayList<>();

    public QuestionPool() {
        loadQuestions();
    }

    private void loadQuestions() {
        for (int i = 1; i <= 20; i++) {
            try (InputStream is = getClass().getResourceAsStream("/resources/questions/q" + i + ".txt");
                 Scanner sc = new Scanner(is)) {
                StringBuilder qText = new StringBuilder();
                while (sc.hasNextLine()) {
                    String line = sc.nextLine();
                    if (line.startsWith("ANSWER:")) {
                        answers.add(line.charAt(8));
                    }
                    qText.append(line).append("\n");
                }
                questions.add(qText.toString());
            } catch (Exception e) {
                questions.add(createErrorQuestion(i));
                answers.add('A');
            }
        }
    }

    public String getQuestion(int num) {
        if (num <= 0 || num > questions.size()) return createErrorQuestion(num);
        return questions.get(num - 1);  // This should only return the question text, no "QUESTION:" prefix
    }

    public boolean checkAnswer(int questionNum, int answer) {
        if (questionNum < 0 || questionNum >= answers.size()) return false;
        char correct = answers.get(questionNum);
        return (answer == 1 && correct == 'A') ||
               (answer == 2 && correct == 'B') ||
               (answer == 3 && correct == 'C') ||
               (answer == 4 && correct == 'D');
    }

    private String createErrorQuestion(int num) {
        return "QUESTION: Error loading question " + num + "\n" +
               "OPTION_A: \nOPTION_B: \nOPTION_C: \nOPTION_D: \n" +
               "ANSWER: A";
    }

    public int getQuestionCount() {
        return questions.size();
    }
}