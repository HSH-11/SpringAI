package com.example.chat.rag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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

    @ConditionalOnProperty(prefix = "app.vectorstore.in-memory", name = "enabled", havingValue = "true")
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @ConditionalOnProperty(prefix = "app.etl.pipeline", name = "init", havingValue = "true")
    @Order(1) // 다른 실행 코드들 보다 가장 먼저 파이프라인을 가동하라
    @Bean
    public ApplicationRunner initEtlPipeline(
            List<DocumentReader> documentReaders,   // 1. Extract
            DocumentTransformer textSplitter,       // 2. Transform
            // DocumentTransformer keywordMetadataEnricher,
            List<DocumentWriter> documentWriters    // 3. Load(콘솔 출력기, VectorDB)
    ) {
        return args -> {
            // 1. 등록된 모든 파일 리더기들을 하나씩 꺼내서 실행
            for (DocumentReader reader : documentReaders) {

                // 1. Extract 원본 파일에서 거대한 텍스트 덩어리를 읽어오기
                List<Document> rawDocuments = reader.get();
                System.out.println("[Extract] 파일 읽기 완료");

                // 2. Transform 읽어온 문서를 AI가 소화하기 좋게 조각조각 자르기
                List<Document> chunkedDocuments = textSplitter.apply(rawDocuments);
                System.out.println("[Transform] 문서 분할 완료");

                // 키워드 추출기 이어서 적용
                // chunkedDocuments = keywordMetadataEnricher.apply(chunkedDocuments);

                // 3. [Load] 가공된 문서 조각들을 준비된 모든 저장소에 집어 넣음
                for (DocumentWriter writer : documentWriters) {
                    writer.accept(chunkedDocuments);
                }

                System.out.println("[Load] 저장소 적재 완료");

            }

            System.out.println("[System] ETL 파이프라인 적재 종료");
        };
    }

    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(
            VectorStore vectorStore,
            ChatClient.Builder chatClientBuilder,
            @Autowired(required = false) DocumentPostProcessor documentPostProcessor
    ) {
        // 1. 문서 검색기 도구
        // vectorDB에서 유사도 30% 이상인 문서를 최대 3개 찾아오도록 세팅
        VectorStoreDocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.3)
                .topK(3)
                .build();

        // 2. 프롬프트 결합기 도구
        // 검색된 문서가 하나도 없더라도 에러를 내지말고 LLM에게 유연하게 넘기도록 세팅
        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .build();

        // 3.쿼리 익스펜더
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();

        // 4. 쿼리 트랜스포머
        TranslationQueryTransformer queryTransformer = TranslationQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .targetLanguage("korean")
                .build();

        // 5. 최종 합체 (RetrievalAugmentationAdvisor 빌더에서 후처리기 등록)
        RetrievalAugmentationAdvisor.Builder builder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(queryAugmenter)
                .queryExpander(queryExpander)
                .queryTransformers(queryTransformer);

    }
}
