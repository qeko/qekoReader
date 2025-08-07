package com.qeko.utils;


import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SentenceSplitter {
    public static List<String> splitAll(List<String> pages) {
        List<String> allSentences = new ArrayList<>();
        for (String page : pages) {
            allSentences.addAll(split(page));
        }
        return allSentences;
    }

    public static List<String> split(String text) {
        return Arrays.asList(text.split("(?<=[。！？])")); // 可根据中文句号、问号等断句
    }


    public static List<List<String>> splitToPages(List<String> sentences, int sentencesPerPage) {
        List<List<String>> pages = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i += sentencesPerPage) {
            pages.add(sentences.subList(i, Math.min(i + sentencesPerPage, sentences.size())));
        }
        return pages;
    }

    public static List<String> splitToPages(String content, int pageCharCount) {
        List<String> pages = new ArrayList<>();
        int length = content.length();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + pageCharCount, length);
            pages.add(content.substring(start, end));
            start = end;
        }
        return pages;
    }

    public static List<String> splitToSentences(String pageContent) {
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.CHINA);
        iterator.setText(pageContent);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = pageContent.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }
}
