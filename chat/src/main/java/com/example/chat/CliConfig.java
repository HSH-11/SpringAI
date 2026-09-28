package com.example.chat;

import ch.qos.logback.classic.LoggerContext;
import com.example.chat.service.ChatService;
import java.util.Scanner;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CliConfig {
    @ConditionalOnProperty(prefix = "spring.application", name = "cli", havingValue = "true")
    @Bean
    // 스프링 부트 서버가 완전히 켜지기 전에 단 한번 자동으로 실행
    public CommandLineRunner cli(@Value("${spring.application.name}") String applicationName, ChatService chatService) {
        return args -> {
            // 1. 스프링 기본 로그 끄기 (채팅에 방해되지 않도록)
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.getLogger("ROOT").detachAppender("CONSOLE");

            System.out.println("=======================================");
            System.out.println("🤖 [" + applicationName + "] CLI 챗봇을 시작합니다!");
            System.out.println("   (종료하려면 'exit' 또는 'quit' 입력)");
            System.out.println("=======================================");

            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("\nUSER: ");
                    String userMessage = scanner.nextLine();

                    // 2. 대화 종료 조건 (무한 루프 탈출)
                    if (userMessage.equalsIgnoreCase("exit") || userMessage.equalsIgnoreCase("quit")) {
                        System.out.println("대화를 종료합니다. 안녕히 계세요!");
                        break;
                    }

                    System.out.print("ASSISTANT: ");

                    // 3.스트리밍 처리 (핵심 변경 포인트)
                    // Flux(스트림)를 toIterable()로 바꾸면 일반적인 for-each 문으로 한 글자씩 꺼내 쓸 수 있음!
                    Iterable<String> chatStream = chatService.stream(new Prompt(userMessage), "cli").toIterable();

                    for (String token : chatStream) {
                        System.out.print(token); // 한 글자씩 화면에 출력 (타이핑 효과)
                    }
                    System.out.println(); // AI 대답이 끝나면 줄바꿈 한 번
                }
            }
        };
    }
}
