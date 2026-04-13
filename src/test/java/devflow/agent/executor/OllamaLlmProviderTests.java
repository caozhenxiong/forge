package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.context.ContextBudgetPlanner;
import devflow.agent.executor.context.ContextCompactor;
import devflow.agent.executor.context.OutputBudgetCalculator;
import devflow.agent.executor.context.PromptTokenEstimator;
import devflow.agent.executor.generation.GenerationBudgetProperties;
import devflow.agent.executor.generation.GenerationTelemetry;
import devflow.agent.executor.llm.LlmFailureReason;
import devflow.agent.executor.llm.LlmGenerateRequest;
import devflow.agent.executor.llm.LlmInvocationException;
import devflow.agent.executor.llm.LlmOptions;
import devflow.agent.executor.llm.ModelBudgetRegistry;
import devflow.agent.executor.llm.OllamaLlmProvider;
import devflow.agent.executor.llm.OllamaProperties;
import devflow.agent.executor.llm.StructuredPayloadException;
import devflow.agent.executor.llm.StructuredPayloadFailureReason;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import devflow.agent.review.FixMode;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.StructuredReviewResult;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaLlmProviderTests {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void generateHonorsOuterTimeoutForHungResponses() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", new SlowHandler());
        server.start();

        int port = server.getAddress().getPort();
        OllamaProperties properties = new OllamaProperties("http://127.0.0.1:" + port, "fake-model", 1, null);
        OllamaLlmProvider provider = new OllamaLlmProvider(
                properties,
                new ObjectMapper(),
                new OutputBudgetCalculator(
                        new ModelBudgetRegistry(new GenerationBudgetProperties(null, null)),
                        new PromptTokenEstimator()
                ),
                new ContextCompactor(
                        new ContextBudgetPlanner(
                                new ModelBudgetRegistry(new GenerationBudgetProperties(null, null)),
                                new PromptTokenEstimator()
                        )
                )
        );

        LlmInvocationException exception = assertThrows(
                LlmInvocationException.class,
                () -> provider.generate(LlmGenerateRequest.workingPrompt("system", "user", LlmOptions.numPredict(16), null))
        );
        assertEquals(LlmFailureReason.TIMEOUT, exception.reason());
        assertTrue(exception.getMessage().contains("timed out after 1 seconds"));
    }

    @Test
    void generateMapsLengthTerminationToTypedTruncationFailure() throws Exception {
        LengthTerminationHandler handler = new LengthTerminationHandler();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", handler);
        server.start();

        int port = server.getAddress().getPort();
        OllamaProperties properties = new OllamaProperties("http://127.0.0.1:" + port, "fake-model", 3, null);
        OllamaLlmProvider provider = new OllamaLlmProvider(
                properties,
                new ObjectMapper(),
                new OutputBudgetCalculator(
                        new ModelBudgetRegistry(new GenerationBudgetProperties(null, null)),
                        new PromptTokenEstimator()
                ),
                new ContextCompactor(
                        new ContextBudgetPlanner(
                                new ModelBudgetRegistry(new GenerationBudgetProperties(null, null)),
                                new PromptTokenEstimator()
                        )
                )
        );

        LlmInvocationException exception = assertThrows(
                LlmInvocationException.class,
                () -> provider.generate(LlmGenerateRequest.workingPrompt("system", "user", LlmOptions.numPredict(16), null))
        );
        assertEquals(LlmFailureReason.OUTPUT_TRUNCATED, exception.reason());
        assertTrue(exception.getMessage().contains("done_reason=length"));
        assertEquals(1, handler.requestCount(), "provider 不应对同一个 length 终止结果继续重打同一请求");
    }

    @Test
    void reviewStructuredParsesSemanticFlagsFromJsonPayload() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", new JsonReviewHandler());
        server.start();

        int port = server.getAddress().getPort();
        OllamaProperties properties = new OllamaProperties("http://127.0.0.1:" + port, "fake-model", 3, null);
        OllamaLlmProvider provider = new OllamaLlmProvider(
                properties,
                new ObjectMapper(),
                new OutputBudgetCalculator(
                        new ModelBudgetRegistry(new GenerationBudgetProperties(null, null)),
                        new PromptTokenEstimator()
                ),
                new ContextCompactor(
                        new ContextBudgetPlanner(
                                new ModelBudgetRegistry(new GenerationBudgetProperties(null, null)),
                                new PromptTokenEstimator()
                        )
                )
        );

        StructuredReviewResult result = provider.reviewStructured("system", "candidate", LlmOptions.numPredict(64));

        assertEquals(ReviewDecision.REVISION_REQUIRED, result.result().decision());
        assertEquals(FixMode.PATCH, result.result().fixMode());
        assertTrue(result.semantics().provided());
        assertTrue(result.semantics().targetsTrackedOpenQuestion());
        assertTrue(result.semantics().clarificationRequest());
        assertTrue(result.semantics().requestsQuantitativeHardening());
        assertTrue(result.semantics().downstreamDetailOnly());
        assertTrue(result.semantics().performanceClaim());
        assertTrue(result.semantics().measurementEvidencePresent());
    }

    @Test
    void reviewStructuredRejectsNonStructuredFallbackInsteadOfGuessingDecision() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", new PlainTextReviewHandler());
        server.start();

        int port = server.getAddress().getPort();
        OllamaProperties properties = new OllamaProperties("http://127.0.0.1:" + port, "fake-model", 3, null);
        OllamaLlmProvider provider = new OllamaLlmProvider(
                properties,
                new ObjectMapper(),
                new OutputBudgetCalculator(
                        new ModelBudgetRegistry(new GenerationBudgetProperties(null, null)),
                        new PromptTokenEstimator()
                ),
                new ContextCompactor(
                        new ContextBudgetPlanner(
                                new ModelBudgetRegistry(new GenerationBudgetProperties(null, null)),
                                new PromptTokenEstimator()
                        )
                )
        );

        StructuredPayloadException exception = assertThrows(
                StructuredPayloadException.class,
                () -> provider.reviewStructured("system", "candidate", LlmOptions.numPredict(64))
        );
        assertEquals(StructuredPayloadFailureReason.JSON_PAYLOAD_INVALID, exception.reason());
    }

    @Test
    void reviewStructuredHonorsExplicitNumPredictWithoutHiddenRatioCap() throws Exception {
        CapturingJsonReviewHandler handler = new CapturingJsonReviewHandler();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", handler);
        server.start();

        int port = server.getAddress().getPort();
        GenerationBudgetProperties budgetProperties = new GenerationBudgetProperties(
                new GenerationBudgetProperties.Defaults(4_096, 1.0d, 0.0625d, 256, 256, 160, 4.0d),
                Map.of("qwen3-coder", new GenerationBudgetProperties.Override(4_096, 1.0d, 0.0625d, 256, 256, 160, 4.0d))
        );
        OllamaProperties properties = new OllamaProperties("http://127.0.0.1:" + port, "qwen3-coder:30b", 3, null);
        OllamaLlmProvider provider = new OllamaLlmProvider(
                properties,
                new ObjectMapper(),
                new OutputBudgetCalculator(
                        new ModelBudgetRegistry(budgetProperties),
                        new PromptTokenEstimator()
                ),
                new ContextCompactor(
                        new ContextBudgetPlanner(
                                new ModelBudgetRegistry(budgetProperties),
                                new PromptTokenEstimator()
                        )
                )
        );

        provider.reviewStructured("system", "candidate", LlmOptions.numPredict(777));

        assertEquals(777, handler.capturedNumPredict());
        assertEquals(4_096, handler.capturedNumCtx());
    }

    @Test
    void generateCapsRequestedNumPredictBeforeSendingToModel() throws Exception {
        CapturingHandler handler = new CapturingHandler();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", handler);
        server.start();

        int port = server.getAddress().getPort();
        OllamaProperties properties = new OllamaProperties("http://127.0.0.1:" + port, "gemma4:26b", 3, null);
        GenerationBudgetProperties budgetProperties = new GenerationBudgetProperties(
                new GenerationBudgetProperties.Defaults(2_048, 1.0d, 0.125d, 256, 256, 160, 4.0d),
                Map.of("gemma4", new GenerationBudgetProperties.Override(2_048, 1.0d, 0.125d, 256, 256, 160, 4.0d))
        );
        OllamaLlmProvider provider = new OllamaLlmProvider(
                properties,
                new ObjectMapper(),
                new OutputBudgetCalculator(
                        new ModelBudgetRegistry(budgetProperties),
                        new PromptTokenEstimator()
                ),
                new ContextCompactor(
                        new ContextBudgetPlanner(
                                new ModelBudgetRegistry(budgetProperties),
                                new PromptTokenEstimator()
                        )
                )
        );

        provider.generate(LlmGenerateRequest.workingPrompt("system", "x".repeat(5_000), LlmOptions.numPredict(1_800), null));
        GenerationTelemetry telemetry = provider.consumeLastTelemetry();

        assertEquals(540, handler.capturedNumPredict());
        assertEquals(2_048, handler.capturedNumCtx());
        assertNotNull(telemetry);
        assertEquals(2_048, telemetry.contextWindowTokens());
        assertEquals(256, telemetry.reserveTokens());
        assertEquals(540, telemetry.availableOutputTokens());
        assertEquals(1_800, telemetry.requestedOutputTokens());
        assertEquals(540, telemetry.effectiveOutputTokens());
        assertEquals(100, telemetry.actualPromptTokens());
        assertEquals(1, telemetry.outputTokens());
    }

    @Test
    void generateCompactsOversizedPromptBeforeSending() throws Exception {
        CapturingHandler handler = new CapturingHandler();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", handler);
        server.start();

        int port = server.getAddress().getPort();
        GenerationBudgetProperties budgetProperties = new GenerationBudgetProperties(
                new GenerationBudgetProperties.Defaults(1_024, 1.0d, 0.15625d, 160, 160, 120, 2.0d),
                Map.of("tiny-model", new GenerationBudgetProperties.Override(1_024, 1.0d, 0.15625d, 160, 160, 120, 2.0d))
        );
        OllamaProperties properties = new OllamaProperties("http://127.0.0.1:" + port, "tiny-model", 3, null);
        ModelBudgetRegistry modelBudgetRegistry = new ModelBudgetRegistry(budgetProperties);
        PromptTokenEstimator promptTokenEstimator = new PromptTokenEstimator();
        OllamaLlmProvider provider = new OllamaLlmProvider(
                properties,
                new ObjectMapper(),
                new OutputBudgetCalculator(modelBudgetRegistry, promptTokenEstimator),
                new ContextCompactor(new ContextBudgetPlanner(modelBudgetRegistry, promptTokenEstimator))
        );

        String system = "S".repeat(4_000);
        String user = "U".repeat(8_000);
        provider.generate(LlmGenerateRequest.workingPrompt(system, user, LlmOptions.numPredict(600), null));

        assertTrue(handler.capturedSystemPrompt().length() < system.length());
        assertTrue(handler.capturedUserPrompt().length() < user.length());
    }

    private static class SlowHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Thread.sleep(2_500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            byte[] body = """
                    {"response":"late","done":true,"done_reason":"stop","eval_count":1}
                    """.strip().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }

    private static class JsonReviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] body = """
                    {
                      "response":"{\\"decision\\":\\"REVISION_REQUIRED\\",\\"fixMode\\":\\"PATCH\\",\\"summary\\":\\"需要补充\\",\\"changeRequest\\":\\"请补说明\\",\\"evidence\\":\\"已有证据\\",\\"actionItems\\":\\"执行动作\\",\\"semantics\\":{\\"targetsLowAuthorityContent\\":false,\\"targetsTrackedOpenQuestion\\":true,\\"clarificationRequest\\":true,\\"backedByHardAuthority\\":false,\\"requestsQuantitativeHardening\\":true,\\"requestsImplementationHardening\\":false,\\"downstreamDetailOnly\\":true,\\"coreStageGap\\":false,\\"performanceClaim\\":true,\\"measurementEvidencePresent\\":true}}",
                      "done":true,
                      "done_reason":"stop",
                      "eval_count":1
                    }
                    """.strip().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }

    private static class LengthTerminationHandler implements HttpHandler {
        private final AtomicInteger requestCount = new AtomicInteger();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            byte[] body = """
                    {"response":"partial","done":true,"done_reason":"length","eval_count":1}
                    """.strip().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }

        int requestCount() {
            return requestCount.get();
        }
    }

    private static class PlainTextReviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] body = """
                    {"response":"APPROVED\\nlooks good","done":true,"done_reason":"stop","eval_count":1}
                    """.strip().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }

    private static class CapturingHandler implements HttpHandler {

        private volatile int capturedNumPredict;
        private volatile int capturedNumCtx;
        private volatile String capturedSystemPrompt = "";
        private volatile String capturedUserPrompt = "";

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            capture(exchange);

            byte[] body = """
                    {"response":"ok","done":true,"done_reason":"stop","eval_count":1,"prompt_eval_count":100}
                    """.strip().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }

        protected void capture(HttpExchange exchange) throws IOException {
            ObjectMapper objectMapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> request = objectMapper.readValue(exchange.getRequestBody(), LinkedHashMap.class);
            capturedSystemPrompt = String.valueOf(request.get("system"));
            capturedUserPrompt = String.valueOf(request.get("prompt"));
            @SuppressWarnings("unchecked")
            Map<String, Object> options = (Map<String, Object>) request.get("options");
            Number numPredict = (Number) options.get("num_predict");
            Number numCtx = (Number) options.get("num_ctx");
            capturedNumPredict = numPredict.intValue();
            capturedNumCtx = numCtx.intValue();
        }

        int capturedNumPredict() {
            return capturedNumPredict;
        }

        int capturedNumCtx() {
            return capturedNumCtx;
        }

        String capturedSystemPrompt() {
            return capturedSystemPrompt;
        }

        String capturedUserPrompt() {
            return capturedUserPrompt;
        }
    }

    private static class CapturingJsonReviewHandler extends CapturingHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            capture(exchange);

            byte[] body = """
                    {
                      "response":"{\\"decision\\":\\"APPROVED\\",\\"fixMode\\":\\"NONE\\",\\"implementationPatchTarget\\":\\"NONE\\",\\"overrideChanges\\":[],\\"summary\\":\\"通过\\",\\"changeRequest\\":\\"\\",\\"evidence\\":\\"\\",\\"actionItems\\":\\"\\",\\"semantics\\":{\\"targetsLowAuthorityContent\\":false,\\"targetsTrackedOpenQuestion\\":false,\\"clarificationRequest\\":false,\\"backedByHardAuthority\\":false,\\"requestsQuantitativeHardening\\":false,\\"requestsImplementationHardening\\":false,\\"downstreamDetailOnly\\":false,\\"coreStageGap\\":false,\\"performanceClaim\\":false,\\"measurementEvidencePresent\\":false,\\"unsupportedQuantitativeConstraintPresent\\":false,\\"unsupportedImplementationConstraintPresent\\":false}}",
                      "done":true,
                      "done_reason":"stop",
                      "eval_count":1,
                      "prompt_eval_count":100
                    }
                    """.strip().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }
}
