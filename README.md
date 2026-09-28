# Spring AI 학습 프로젝트

Spring AI 2.0 기반으로 **기본 채팅**, **RAG**, **Tool Calling**, **MCP(Model Context Protocol)** 를 단계별로 실습한 학습용 저장소입니다.

각 모듈은 독립적인 Spring Boot 애플리케이션이며, Gradle로 개별 빌드·실행합니다.

---

## 기술 스택

| 항목 | 버전 / 도구 |
|------|-------------|
| Java | 21 (`chat`), 17 (`tool`, `mcp-server`) |
| Spring Boot | 4.0.6 |
| Spring AI | 2.0.0-M6 |
| LLM | Ollama (로컬), OpenAI / GitHub Models (선택) |
| Embedding | Ollama `bge-m3` |
| Vector Store | Elasticsearch (`chat`), SimpleVectorStore (`mcp-server`) |
| 문서 파싱 | Apache Tika (`spring-ai-tika-document-reader`) |
| API 문서 | SpringDoc OpenAPI (`tool`) |

---

## 프로젝트 구조

```
SpringAI/
├── chat/          # 기본 채팅 + RAG (Elasticsearch)
├── tool/          # Tool Calling + MCP Client
└── mcp-server/    # MCP Server (RAG Tool 제공)
```

---

## 학습 내용 요약

### 1. ChatClient & Advisor (`chat`)

- `ChatClient.Builder`로 LLM 호출 추상화
- **Advisor** 패턴으로 요청/응답 가로채기
  - `SimpleLoggerAdvisor` — 요청·응답 로깅
  - `MessageChatMemoryAdvisor` — 대화 기록 주입
- **ChatMemory** — `MessageWindowChatMemory` (최근 10개 메시지, JVM 메모리)
- **스트리밍** — `Flux<String>` 기반 SSE 응답
- **구조화 출력** — `.call().entity(CsEvaluation.class)` 로 JSON → Record 매핑

**REST API**

| Method | Path | 설명 |
|--------|------|------|
| POST | `/call` | 동기 채팅 |
| POST | `/stream` | 스트리밍 채팅 (SSE) |
| POST | `/cs` | CS 문의 분류 (카테고리·긴급도·키워드) |

---

### 2. RAG — Retrieval Augmented Generation (`chat`, `mcp-server`)

문서 기반 질의응답을 위해 **ETL 파이프라인**과 **RetrievalAugmentationAdvisor** 를 구성했습니다.

#### ETL 파이프라인

| 단계 | 구현 | 설명 |
|------|------|------|
| **Extract** | `TikaDocumentReader` | PDF 등 문서 읽기 |
| **Transform** | `LengthTextSplitter` | 200자 단위 분할, 100자 오버랩 |
| **Transform** | `KeywordMetadataEnricher` | LLM으로 키워드 메타데이터 추출 (선택) |
| **Load** | `VectorStore` | Elasticsearch 또는 In-Memory 저장 |

#### RAG Advisor 구성

- `VectorStoreDocumentRetriever` — 유사도 0.3 이상, Top-K 3
- `ContextualQueryAugmenter` — 검색 결과를 프롬프트에 결합
- `MultiQueryExpander` — 질의 확장 (다중 검색 쿼리 생성)
- `TranslationQueryTransformer` — 한국어 쿼리 변환
- `DocumentPostProcessor` — 검색 결과 콘솔/로그 출력
- `FilterExpression` — 메타데이터 기반 문서 필터링 (다중 사용자 격리, 할루시네이션 방지)

**REST API (`chat`)**

| Method | Path | 설명 |
|--------|------|------|
| POST | `/rag/call` | RAG 동기 채팅 |
| POST | `/rag/stream` | RAG 스트리밍 채팅 |

---

### 3. Tool Calling / Function Calling (`tool`)

LLM이 외부 API를 직접 호출하도록 **@Tool** 어노테이션 기반 함수를 등록했습니다.

- `Tools.getWeather()` — wttr.in 날씨 API (문자열 반환, `returnDirect = true`)
- `Tools.getWeatherDetails()` — 3일 예보 + 천문 정보 (Record DTO 반환)
- `ToolCallingChatOptions` — temperature 0.2
- `ChatClient.defaultTools(tools)` — Tool 자동 등록

**REST API**

| Method | Path | 설명 |
|--------|------|------|
| POST | `/tool/call` | Tool Calling 동기 |
| POST | `/tool/stream` | Tool Calling 스트리밍 |

---

### 4. MCP — Model Context Protocol (`tool` ↔ `mcp-server`)

[MCP](https://modelcontextprotocol.io/) 를 통해 AI와 외부 도구를 표준 프로토콜로 연결합니다.

#### mcp-server (Provider)

- `spring-ai-starter-mcp-server-webmvc` — MCP Server
- `MethodToolCallbackProvider` — `@Tool` 메서드를 MCP Tool로 노출
- `ragTool` — Spring AI 개념 RAG 기반 Q&A Tool
- STDIO / SSE 전송 방식 지원

#### tool (Consumer)

- `spring-ai-starter-mcp-client` — MCP Client
- STDIO로 `mcp-server` JAR 프로세스 연결
- `ToolCallbackProvider` → `ToolCallback[]` → `ChatClient.defaultToolCallbacks()`

**REST API**

| Method | Path | 설명 |
|--------|------|------|
| POST | `/mcp/call` | MCP Tool 연동 동기 |
| POST | `/mcp/stream` | MCP Tool 연동 스트리밍 |

---

## 사전 요구 사항

1. **Java 17+** (chat 모듈은 Java 21)
2. **Ollama** 설치 및 실행
   ```bash
   ollama pull hf.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF
   ollama pull bge-m3
   ```
3. **Elasticsearch** (`chat` 모듈 RAG 사용 시)
   ```bash
   # Docker 예시
   docker run -d --name elasticsearch -p 9200:9200 \
     -e "discovery.type=single-node" \
     -e "xpack.security.enabled=false" \
     elasticsearch:8.17.0
   ```
4. **환경 변수** (OpenAI / GitHub Models 사용 시)
   ```bash
   set OPENAI_API_KEY=your-api-key
   ```

---

## 실행 방법

### chat — 기본 채팅 & RAG

```bash
cd chat
./gradlew bootRun
```

- 기본 포트: `8080`
- CLI 모드: `application.yaml`에서 `app.cli.enabled: true` 설정
- ETL 초기화: `app.etl.pipeline.init: true` (앱 시작 시 PDF → Vector Store 적재)
- Swagger: 미포함 (REST Controller 직접 호출)

**RAG CLI 예시**

```yaml
# application.yaml
app:
  cli:
    enabled: true
  etl:
    pipeline:
      init: true
  vectorstore:
    in-memory:
      enabled: false   # Elasticsearch 사용
```

### tool — Tool Calling & MCP Client

```bash
# 1. mcp-server JAR 빌드 (MCP STDIO 연동 시)
cd mcp-server
./gradlew bootJar

# 2. tool 실행
cd ../tool
./gradlew bootRun
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- MCP Client 설정: `spring.ai.mcp.client.stdio.connections` (JAR 경로 확인 필요)

### mcp-server — MCP Server

```bash
cd mcp-server
./gradlew bootRun
```

- 기본 포트: `8081` (SSE 모드)
- In-Memory Vector Store 사용 (`app.vectorstore.in-memory.enabled: true`)
- STDIO 모드: `--spring.ai.mcp.server.stdio=true --spring.main.web-application-type=none`

---

## API 요청 예시

### 기본 채팅 (스트리밍)

```bash
curl -N -X POST http://localhost:8080/stream \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "user-1",
    "userPrompt": "Spring AI란 무엇인가요?"
  }'
```

### RAG 채팅

```bash
curl -N -X POST http://localhost:8080/rag/stream \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "user-1",
    "userPrompt": "Spring AI의 Advisor 패턴을 설명해줘",
    "filterExpression": ""
  }'
```

### Tool Calling (날씨 조회)

```bash
curl -N -X POST http://localhost:8080/tool/stream \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "user-1",
    "userPrompt": "서울 날씨 알려줘"
  }'
```

### CS 문의 분류 (구조화 출력)

```bash
curl -X POST http://localhost:8080/cs \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "cs-1",
    "userPrompt": "주문한 상품이 파손되어 왔어요. 환불해주세요."
  }'
```

---

## 핵심 개념 정리

| 개념 | 설명 |
|------|------|
| **ChatClient** | Spring AI의 LLM 호출 진입점. Fluent API로 prompt → call/stream |
| **Advisor** | AOP처럼 LLM 요청 전후에 로직 삽입 (메모리, RAG, 로깅 등) |
| **ChatMemory** | 대화 이력 저장. `CONVERSATION_ID`로 세션 구분 |
| **RAG** | 문서 검색 + LLM 생성. 할루시네이션 감소, 도메인 지식 활용 |
| **VectorStore** | 임베딩 벡터 저장·유사도 검색 (Elasticsearch, SimpleVectorStore) |
| **@Tool** | LLM이 호출 가능한 Java 메서드 등록 (Function Calling) |
| **MCP** | AI ↔ 외부 도구 간 표준 프로토콜. Client/Server 분리 아키텍처 |

---

## 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────┐
│                        chat module                          │
│  REST API ──► ChatClient ──► Advisor ──► Ollama / OpenAI   │
│                    │                                        │
│                    └──► RetrievalAugmentationAdvisor        │
│                              │                              │
│                              ▼                              │
│                     Elasticsearch VectorStore               │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                        tool module                          │
│  REST API ──► ChatClient ──► @Tool (날씨 API)              │
│                    │                                        │
│                    └──► MCP Client (STDIO)                  │
│                              │                              │
└──────────────────────────────┼──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                     mcp-server module                       │
│  MCP Server ──► ragTool ──► RagChatService                │
│                                   │                         │
│                                   ▼                         │
│                          SimpleVectorStore                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 참고 자료

- [Spring AI 공식 문서](https://docs.spring.io/spring-ai/reference/)
- [Spring AI GitHub](https://github.com/spring-projects/spring-ai)
- [Model Context Protocol](https://modelcontextprotocol.io/)
- [Ollama](https://ollama.com/)
