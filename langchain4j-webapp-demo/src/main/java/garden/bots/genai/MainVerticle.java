package garden.bots.genai;

//import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
//import dev.langchain4j.service.AiServices;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import dev.langchain4j.model.chat.response.ChatResponse;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.*;
import java.util.stream.Stream;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;

public class MainVerticle extends AbstractVerticle {

  private boolean cancelRequest ;


  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    
    var apiKey = "I<3DockerModelRunner";
    var modelName = Optional.ofNullable(System.getenv("MODEL_RUNNER_CHAT_MODEL")).orElse("hf.co/qwen/qwen2.5-coder-1.5b-instruct-gguf:q4_k_m");

    var staticPath = Optional.ofNullable(System.getenv("STATIC_PATH")).orElse("/*");
    var httpPort = Optional.ofNullable(System.getenv("HTTP_PORT")).orElse("8888");

    var baseUrl = Optional.ofNullable(System.getenv("MODEL_RUNNER_BASE_URL")).orElse("http://host.docker.internal:8080/v1/chat/completions");
    var temperature = 0.2;

    // Ⓜ️ initialize the  model

    OpenAiStreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
      .apiKey(apiKey).baseUrl(baseUrl)
      .modelName(modelName)
      .temperature(temperature)
      .build();

    
    // Ⓜ️ handle the memory

    // ----------------------------------------
    // 🧠 Keep the memory of the conversation
    // ----------------------------------------
    var memory = MessageWindowChatMemory.withMaxMessages(5);

    Router router = Router.router(vertx);

    router.route().handler(BodyHandler.create());
    // Serving static resources
    var staticHandler = StaticHandler.create();
    staticHandler.setCachingEnabled(false);
    router.route(staticPath).handler(staticHandler);

    router.delete("/clear-history").handler(ctx -> {
      memory.clear();
      ctx.response()
        .putHeader("content-type", "text/plain;charset=utf-8")
        .end("👋 conversation memory is empty");
    });

    router.get("/message-history").handler(ctx -> {
      Stream<String> strList = memory.messages().stream().map(msg -> msg.toString());
      JsonArray messages = new JsonArray(strList.toList());
      ctx.response()
        .putHeader("Content-Type", "application/json;charset=utf-8")
        .end(messages.encodePrettily());
    });

    router.get("/model").handler(ctx -> {
      ctx.response()
        .putHeader("Content-Type", "application/json;charset=utf-8")
        .end("{\"model\":\""+modelName+"\",\"provider\":\"OpenAI\"}");
    });


    router.delete("/cancel-request").handler(ctx -> {
      cancelRequest = true;
      ctx.response()
        .putHeader("content-type", "text/plain;charset=utf-8")
        .end("👋 request aborted");
    });

    // Ⓜ️ process the prompt and send an answer

    // ----------------------------------------
    // 🤖 main route
    // ----------------------------------------
    router.post("/prompt").handler(ctx -> {

      var question = ctx.body().asJsonObject().getString("question");
      var systemContent = ctx.body().asJsonObject().getString("system");
      //var contextContent = ctx.body().asJsonObject().getString("context");

      // ----------------------------------------
      // 📝 Build the prompt
      // ----------------------------------------
      SystemMessage systemInstructions = systemMessage(systemContent);
      //SystemMessage contextMessage = systemMessage(contextContent);
      UserMessage humanMessage = UserMessage.userMessage(question);

      List<ChatMessage> messages = new ArrayList<>();
      
      messages.add(systemInstructions);
      messages.addAll(memory.messages());
      //messages.add(contextMessage);
      messages.add(humanMessage);

      System.out.println(messages);

      // ----------------------------------------
      // 🧠 Update the memory of the conversation
      // ----------------------------------------
      memory.add(humanMessage);

      HttpServerResponse response = ctx.response();

      response
        .putHeader("Content-Type", "application/octet-stream")
        .setChunked(true);

      // ----------------------------------------
      // 🤖 Generate the completion (stream)
      // ----------------------------------------
      streamingModel.chat(messages, new StreamingChatResponseHandler() {
        @Override
        public void onPartialResponse(String partialResponse) {
          if (cancelRequest) {
            cancelRequest = false;
            throw new RuntimeException("🤬 Shut up!");
          }

          System.out.println("New token: '" + partialResponse + "'");
          response.write(partialResponse);
        }

        @Override
        public void onCompleteResponse(ChatResponse chatResponse) {
          memory.add(chatResponse.aiMessage());
          System.out.println("Streaming completed: " + chatResponse);
          response.end();

        }

        @Override
        public void onError(Throwable throwable) {
          throwable.printStackTrace();
        }
      });

    });


    // Create an HTTP server
    var server = vertx.createHttpServer();

    //! Start the HTTP server
    server.requestHandler(router).listen(Integer.parseInt(httpPort), http -> {
      if (http.succeeded()) {
        startPromise.complete();
        System.out.println("GenAI Vert-x server started on port " + httpPort);
      } else {
        startPromise.fail(http.cause());
      }
    });

  }
}