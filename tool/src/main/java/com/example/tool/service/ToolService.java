package com.example.tool.service;

import com.example.tool.Tools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ToolService {

    private final ChatClient chatClient;

    public ToolService(ChatClient.Builder chatClientBuilder,
                       Advisor[] advisors,
                       @Value("${app.chat.default-system-prompt:}") String systemPrompt,
                       Tools tools) {
        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultTools(tools)
                .defaultOptions(ToolCallingChatOptions.builder()
                        .temperature(0.2).build().mutate())
                .defaultAdvisors(advisors)
                .build();
    }

    private ChatClientRequestSpec createRequest(String conversationId, Prompt prompt) {
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
