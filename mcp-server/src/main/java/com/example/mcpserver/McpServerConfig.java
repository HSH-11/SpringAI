package com.example.mcpserver;

import com.example.mcpserver.tool.Tools;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public SimpleLoggerAdvisor simpleLoggerAdvisor() {
        return SimpleLoggerAdvisor.builder().build();
    }

    // MCP 클라이언트가 접속해서 도구를 사용하려면 반드시 필요한 설정!(RAG)
    @Bean
    public ToolCallbackProvider toolCallbackProvider(Tools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
