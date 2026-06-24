package com.example.chat.rag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class RagConfig {

    /*
        Extract
     */
    @Bean
    public List<DocumentReader> documentReaders(
            @Value("${app.rag.documents-location-pattern}") String documentsLocationPattern) throws IOException {
        // 1. 설정한 경로 패턴에 맞는 파일 찾기
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(documentsLocationPattern);

        // 2. 찾아온 파일들을 담을 빈 리스트 생성
        List<DocumentReader> readers = new ArrayList<>();

        // 3. 파일의 개수만큼 for문을 돌면서 tika로 예쁘게 포장해서 리스트에 넣기
        for (Resource resource : resources) {
            readers.add(new TikaDocumentReader(resource));
        }

        // 4. 리스트 반환
        return readers;

    }

    /*
        Transform
     */
    @Bean
    public DocumentTransformer textSplitter() {
        return new LengthTextSplitter(200, 100);
    }

    @Bean
    public DocumentTransformer keywordMetadataEnricher(ChatModel chatModel) {
        return new KeywordMetadataEnricher(chatModel, 4);
    }

    /*
     * Load
     */
    @Bean
    public DocumentWriter jsonConsoleDocumentWriter(ObjectMapper objectMapper) {
        // 앞 단계에서 가공되어 넘어온 문서 조각 리스트(documents)를 받아서 로직 실행
        return documents -> {
            // 1. 현재 들어온 총 문서 조각(Chunk)의 개수가 몇 개인지 콘솔에 명확하게 표기
            System.out.println("===== 저장할 문서 조각 개수 : " + documents.size() + "=====");

            // 들여쓰기와 줄바꿈 적용된 예쁜 JSON 문자열
            String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(documents);
            System.out.println(jsonString);

        };

    }
}
