package com.example.tool.service;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Slf4j
public class McpService {

    private final ChatClient chatClient;


    public McpService(ChatClient.Builder chatClientBuilder,
                      Advisor[] advisors,
                      @Value("${app.chat.default-system-prompt:}") String systemPrompt,
                      ToolCallbackProvider[] toolCallbackProviders) {

        // 1. ToolCallback 추출 및 로깅
        List<ToolCallback> callbackList = new ArrayList<>(); // 툴 콜백들을 임시로 담아둘 리스트 생성

        for (ToolCallbackProvider provider : toolCallbackProviders) { // 주입받은 프로바이더 배열 순회
            for (ToolCallback callback : provider.getToolCallbacks()) { // 각 프로바이더가 가지고 있는 콜백(기능)들을 다시 순회

                // 각 툴의 이름과 설명을 로그로 출력 (어떤 툴들이 로드되었는지 확인용)
                log.info("Tool loaded – name: {}, description: {}",
                        callback.getToolDefinition().name(),
                        callback.getToolDefinition().description());

                callbackList.add(callback); // 확인된 콜백을 리스트에 추가
            }
        }

        // 리스트에 모인 콜백들을 ChatClient 설정에 넣기 위해 배열 형태로 변환
        ToolCallback[] toolCallbacks = callbackList.toArray(new ToolCallback[0]);

        // 2. ChatClient 설정
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultOptions(ToolCallingChatOptions.builder().temperature(0.2).build().mutate())
                .defaultAdvisors(advisors)
                .defaultToolCallbacks(toolCallbacks)
                .build();
    }

    private ChatClient.ChatClientRequestSpec createRequest(String conversationId, Prompt prompt) {
        return chatClient.prompt(prompt)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId));
    }

    public Flux<String> stream(String conversationId, Prompt prompt) {
        return createRequest(conversationId, prompt).stream().content();
    }

    public ChatResponse call(String conversationId, Prompt prompt) {
        return createRequest(conversationId, prompt).call().chatResponse();
    }
}
