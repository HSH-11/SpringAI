package com.example.chat.service;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class RagChatService {

    private final ChatClient chatClient;

    public RagChatService(ChatClient.Builder chatClientBuilder, Advisor[] advisors) {
        this.chatClient = chatClientBuilder.defaultOptions(ChatOptions.builder().temperature(0.0))
                .defaultAdvisors(advisors).build();
    }

    public Flux<String> stream(Prompt prompt, String conversationId, Optional<String> filterExpressionAsOpt) {
        return prepareRequest(prompt, conversationId, filterExpressionAsOpt)
                .stream()
                .content();
    }

    public @Nullable ChatResponse call(Prompt prompt, String conversationId, Optional<String> filterExpressionAsOpt) {
        return prepareRequest(prompt, conversationId, filterExpressionAsOpt)
                .call()
                .chatResponse();
    }

    private ChatClient.ChatClientRequestSpec prepareRequest(Prompt prompt, String conversationId,
                                                            Optional<String> filterExpressionAsOpt) {
        // FilterExpression 필수 적용
        /*
        - 다중 사용자 환경에서의 '보안 및 데이터 격리’ (연봉계약서)
        - 할루시네이션(환각) 방지 및 정확도 극대화
        - 인프라 비용 절감과 검색 속도 향상(date >= '2026-01-01’)
         */
        return chatClient.prompt(prompt)
                .advisors(advisorSpec ->
                        advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .advisors(advisorSpec ->
                        advisorSpec.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION,
                                filterExpressionAsOpt.orElse("")));

    }
}
