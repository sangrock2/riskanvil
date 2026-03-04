package com.sw103302.backend.controller;

import com.sw103302.backend.dto.*;
import com.sw103302.backend.service.ChatbotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chatbot")
@Tag(name = "Chatbot", description = "AI chatbot APIs")
public class ChatbotController {
    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/chat")
    @Operation(summary = "Send message", description = "Send a message to AI chatbot")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Message sent"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest req) {
        return ResponseEntity.ok(chatbotService.chat(req));
    }

    @GetMapping("/conversations")
    @Operation(summary = "List conversations", description = "Get user's chat conversations")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Conversations retrieved"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<ConversationListResponse>> listConversations() {
        return ResponseEntity.ok(chatbotService.listConversations());
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "Get messages", description = "Get messages from a conversation")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Messages retrieved"),
        @ApiResponse(responseCode = "404", description = "Conversation not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<ChatMessageResponse>> getMessages(@PathVariable Long id) {
        return ResponseEntity.ok(chatbotService.getMessages(id));
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "Delete conversation", description = "Delete a chat conversation")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Conversation deleted"),
        @ApiResponse(responseCode = "404", description = "Conversation not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Void> deleteConversation(@PathVariable Long id) {
        chatbotService.deleteConversation(id);
        return ResponseEntity.ok().build();
    }
}
