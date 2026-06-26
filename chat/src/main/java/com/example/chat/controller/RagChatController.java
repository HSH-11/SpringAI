package com.example.chat.controller;

import com.example.chat.service.RagChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/rag")
public class RagChatController {
    private final RagChatService ragChatService;

    public RagChatController(RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    private static Prompt createPrompt(RagPromptBody ragPromptBody) {
        // 1. 메시지들을 차곡차곡 담을 빈 리스트 생성
        List<Message> messages = new ArrayList<>();

        // 2. systemPrompt가 입력으로 들어왔다면 리스트에 넣자!
        if (ragPromptBody.systemPrompt() != null && !ragPromptBody.systemPrompt().isBlank()) {
            messages.add(new SystemMessage(ragPromptBody.systemPrompt()));
        }

        // 3. userPrompt는 필수 값이니 무조건 리스트에 넣자
        messages.add(new UserMessage(ragPromptBody.userPrompt()));

        // 4. 리스트에 담긴 메시지들로 프롬프트 조립하기
        Prompt.Builder promptBuilder = Prompt.builder().messages(messages);

        // 5. chatOptions가 있다면 적용하기
        if (ragPromptBody.chatOptions() != null) {
            promptBuilder.chatOptions(ragPromptBody.chatOptions());
        }
        return promptBuilder.build();
    }

    @PostMapping(value = "/call", produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse call(@RequestBody @Valid RagPromptBody ragPromptBody) {

        Prompt prompt = createPrompt(ragPromptBody);
        return ragChatService.call(prompt, ragPromptBody.conversationId(),
                Optional.ofNullable(ragPromptBody.filterExpression()));

    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody @Valid RagPromptBody ragPromptBody) {
        Prompt prompt = createPrompt(ragPromptBody);
        return ragChatService.stream(prompt, ragPromptBody.conversationId(),
                Optional.ofNullable(ragPromptBody.filterExpression()));
    }

    public record RagPromptBody(@NotEmpty String conversationId,
                                @NotEmpty String userPrompt,
                                @Nullable String systemPrompt,
                                DefaultChatOptions chatOptions,
                                @Nullable String filterExpression) {
    }
}
