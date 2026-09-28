package com.example.mcpserver.rag;

import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.util.StringUtils;

public class LengthTextSplitter extends TextSplitter {

    private final int chunkSize;    // 한 조각(Chunk)의 최대 글자 수
    private final int chunkOverlap; // 다음 조각과 겹치게 할 글자수

    public LengthTextSplitter(int chunkSize, int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }


    @Override
    protected List<String> splitText(String text) { // "안녕하세요반갑습니다"(10글자)

        // 1. 잘라낼 조각들을 담을 빈 리스트 생성
        List<String> chunks = new ArrayList<>();

        // 2. 만약 넘어온 텍스트가 비어있거나, 공백뿐이라면 자를게 없으므로 그대로 리턴
        if (!StringUtils.hasText(text)) {
            return chunks;
        }

        int textLength = text.length(); // 10글자

        // 3. 자르기 시작할 '시작점(인덱스)'를 0으로 세팅
        int chunkStart = 0;

        // 4. 끝까지 반복해서 자르기
        while (chunkStart < textLength) {

            // [끝점 계산] 자를 조각의 끝점 계산
            // (시작점 + 자를 크기)를 하되, 만약 남은 글자가 부족해서 전체길이를 넘어가 버리면 전체 길이에서 딱 멈추기
            int chunkEnd = Math.min(chunkStart + chunkSize, textLength); // 5, 10

            // [자르기]
            String slicedText = text.substring(chunkStart, chunkEnd); // "안녕하세요", "세요반갑습"
            chunks.add(slicedText);

            // [다음 시작점 계산]
            // 방금 자른 조각의 '끝점'에서 '겹칠 크기(Overlap)' 만큼 뒤로 되돌아간 곳이 다음번 시작점이 됨
            int nextStart = chunkEnd - chunkOverlap;

            // 남은 텍스트가 너무 짧거나 설정 오류로 인해 다음 시작점이 제자리에서 머물거나 뒤로 밀리면 무한루프에 빠짐
            if (nextStart <= chunkStart) {
                break;
            }

            // [위치이동] 시작점을 방금 계산한 다음시작점으로 이동시키고 다시 루프를 돌기
            chunkStart = nextStart;
        }

        // 5. 리스트 반환
        return chunks;

    }
}