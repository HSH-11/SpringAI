package com.example.chat.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    // chatClient 빌더 객체 생성
    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/ai")
    public String generation(String userPrompt) {
        return this.chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();
    }
}
