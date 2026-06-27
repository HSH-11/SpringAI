package com.example.mcpserver.rag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.DocumentWriter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Configuration
public class RagConfig {

    /*
        Extract
     */
    @Bean
    public List<DocumentReader> documentReaders(
            @Value("${app.rag.documents-location-pattern}") String documentsLocationPattern) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(documentsLocationPattern);

        List<DocumentReader> readers = new ArrayList<>();

        for (Resource resource : resources) {
            readers.add(new TikaDocumentReader(resource));
        }

        return readers;
    }

    /*
        Transform
     */
    @Bean
    public DocumentTransformer textSplitter() {
        return new LengthTextSplitter(200, 100);
    }

    /*
       Load
     */
    @Bean
    public DocumentWriter jsonConsoleDocumentWriter(ObjectMapper objectMapper) {
        return documents -> {
            log.info("======= 저장할 문서 조각(Chunk) 개수: {} ========", documents.size());
            try {
                String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(documents);
                log.debug("Chunk JSON: {}", jsonString);
            } catch (Exception e) {
                log.error("JSON 변환 중 에러가 발생했습니다: {}", e.getMessage());
            }
            log.info("======================================================");
        };
    }

    @ConditionalOnProperty(prefix = "app.vectorstore.in-memory", name = "enabled", havingValue = "true")
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @ConditionalOnProperty(prefix = "app.etl.pipeline", name = "init", havingValue = "true")
    @Order(1)
    @Bean
    public ApplicationRunner initEtlPipeline(
            List<DocumentReader> documentReaders,
            DocumentTransformer textSplitter,
            DocumentTransformer keywordMetadataEnricher,
            List<DocumentWriter> documentWriters) {

        return args -> {
            log.info("[System] ETL 파이프라인 가동 시작");

            for (DocumentReader reader : documentReaders) {
                List<Document> rawDocuments = reader.get();
                log.info("[Extract] 파일 읽기 완료");

                List<Document> chunkedDocuments = textSplitter.apply(rawDocuments);
                log.info("[Transform] 문서 분할 완료");

                chunkedDocuments = keywordMetadataEnricher.apply(chunkedDocuments);

                for (DocumentWriter writer : documentWriters) {
                    writer.accept(chunkedDocuments);
                }
                log.info("[Load] 저장소 적재 완료");
            }

            log.info("[System] ETL 파이프라인 적재 종료");
        };
    }

    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(VectorStore vectorStore,
                                                                     ChatClient.Builder chatClientBuilder,
                                                                     Optional<DocumentPostProcessor> printDocumentsPostProcessor) {

        VectorStoreDocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.3)
                .topK(3)
                .build();

        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .build();

        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();

        TranslationQueryTransformer queryTransformer = TranslationQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .targetLanguage("korean")
                .build();

        RetrievalAugmentationAdvisor.Builder advisorBuilder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                .queryAugmenter(queryAugmenter)
                //.queryExpander(queryExpander)
                //.queryTransformers(queryTransformer)
                ;

        printDocumentsPostProcessor.ifPresent(processor ->
                advisorBuilder.documentPostProcessors(processor));

        return advisorBuilder.build();
    }

    @ConditionalOnProperty(prefix = "app.cli", name = "enabled", havingValue = "true")
    @Bean
    public DocumentPostProcessor printDocumentsPostProcessor() {
        return (query, documents) -> {
            log.info("[ Search Results ]");
            log.info("===============================================");

            if (documents == null || documents.isEmpty()) {
                log.info("  No search results found.");
                log.info("===============================================");
                return documents;
            }

            for (int i = 0; i < documents.size(); i++) {
                Document document = documents.get(i);
                // String.format을 사용하여 소수점 둘째 자리까지 잘라서 로깅
                log.info("▶ {} Document, Score: {}", i + 1, String.format("%.2f", document.getScore()));
                log.info("-----------------------------------------------");

                Optional.ofNullable(document.getText()).stream()
                        .map(text -> text.split("\n")).flatMap(Arrays::stream)
                        // 중괄호 바인딩을 통해 혹시 모를 텍스트 내의 특수문자 에러 방지
                        .forEach(line -> log.info("{}", line));

                log.info("===============================================");
            }
            log.info("[ RAG 사용 응답 ]");
            return documents;
        };
    }


}
