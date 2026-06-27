package com.example.mcpserver.tool;

import com.example.mcpserver.rag.RagChatService;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Tools {

    private final RagChatService ragChatService;

    // RagChatService도 tools을 필요로 하고 tools도 RagChatService를 필요로 하기에 순환 참조로 인한 Lazy 추가
    public Tools(@Lazy RagChatService ragChatService) {
        this.ragChatService = ragChatService;
    }

    @Tool(description = "Spring AI 개념에 대해 RAG 기반 답변을 제공합니다.", returnDirect = true)
    public String ragTool(@ToolParam(description = "Spring AI 강의에 대한 질문") String userPrompt) {

        log.info("ragTool UserPrompt = {}", userPrompt);

        return this.ragChatService.call(
                        Prompt.builder().messages(UserMessage.builder().text(userPrompt).build()).build(), "mcp",
                        Optional.empty())
                .getResult().getOutput().getText();
    }

}
