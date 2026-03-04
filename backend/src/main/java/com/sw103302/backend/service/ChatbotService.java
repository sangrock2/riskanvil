package com.sw103302.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sw103302.backend.component.AiClient;
import com.sw103302.backend.dto.ChatRequest;
import com.sw103302.backend.dto.ChatMessageResponse;
import com.sw103302.backend.dto.ChatResponse;
import com.sw103302.backend.dto.ConversationListResponse;
import com.sw103302.backend.entity.ChatConversation;
import com.sw103302.backend.entity.ChatMessage;
import com.sw103302.backend.entity.Portfolio;
import com.sw103302.backend.entity.PortfolioPosition;
import com.sw103302.backend.entity.User;
import com.sw103302.backend.entity.WatchlistItem;
import com.sw103302.backend.repository.ChatConversationRepository;
import com.sw103302.backend.repository.ChatMessageRepository;
import com.sw103302.backend.repository.PortfolioRepository;
import com.sw103302.backend.repository.PortfolioPositionRepository;
import com.sw103302.backend.repository.UserRepository;
import com.sw103302.backend.repository.WatchlistRepository;
import com.sw103302.backend.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatbotService {
    private static final int MAX_HISTORY_MESSAGES = 30;

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioPositionRepository positionRepository;
    private final WatchlistRepository watchlistRepository;

    public ChatbotService(ChatConversationRepository conversationRepository,
                          ChatMessageRepository messageRepository,
                          UserRepository userRepository,
                          AiClient aiClient,
                          ObjectMapper objectMapper,
                          PortfolioRepository portfolioRepository,
                          PortfolioPositionRepository positionRepository,
                          WatchlistRepository watchlistRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.watchlistRepository = watchlistRepository;
    }

    @Transactional
    public ChatResponse chat(ChatRequest req) {
        User user = currentUser();

        ChatConversation conversation;
        if (req.conversationId() != null) {
            conversation = conversationRepository.findByIdAndUser_Id(req.conversationId(), user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        } else {
            // Create new conversation
            conversation = new ChatConversation(user, req.model());
            conversation = conversationRepository.save(conversation);
        }

        // Save user message
        ChatMessage userMsg = new ChatMessage(conversation, "user", req.message());
        messageRepository.save(userMsg);

        // Get conversation history
        List<ChatMessage> history = messageRepository
            .findByConversation_IdOrderByCreatedAtAsc(conversation.getId());

        // Exclude the latest user message from history to avoid duplicate prompt payload.
        int endExclusive = Math.max(0, history.size() - 1);
        int startInclusive = Math.max(0, endExclusive - MAX_HISTORY_MESSAGES);
        List<ChatMessage> historyForModel = history.subList(startInclusive, endExclusive);

        // Build AI request
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("message", req.message());
        aiRequest.put("model", req.model() != null ? req.model() : "opus");
        aiRequest.put("history", historyForModel.stream()
            .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
            .collect(Collectors.toList()));

        // Inject portfolio and watchlist context
        String context = buildUserContext(user);
        if (context != null && !context.isBlank()) {
            aiRequest.put("context", context);
        }

        // Call AI service
        try {
            String jsonResponse = aiClient.post("/chatbot", aiRequest);
            JsonNode aiResponse = objectMapper.readTree(jsonResponse);

            String responseMessage = aiResponse.get("message").asText();
            int tokensIn = aiResponse.has("tokensIn") ? aiResponse.get("tokensIn").asInt() : 0;
            int tokensOut = aiResponse.has("tokensOut") ? aiResponse.get("tokensOut").asInt() : 0;

            // Save assistant message
            ChatMessage assistantMsg = new ChatMessage(conversation, "assistant", responseMessage);
            assistantMsg.setModel(req.model());
            assistantMsg.setTokensIn(tokensIn);
            assistantMsg.setTokensOut(tokensOut);
            messageRepository.save(assistantMsg);

            // Update conversation title if first exchange
            if (conversation.getTitle() == null && history.size() <= 2) {
                conversation.setTitle(generateTitle(req.message()));
                conversationRepository.save(conversation);
            }

            return new ChatResponse(
                conversation.getId(),
                responseMessage,
                req.model(),
                tokensIn,
                tokensOut,
                LocalDateTime.now()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to get chatbot response: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<ConversationListResponse> listConversations() {
        User user = currentUser();
        List<ChatConversation> conversations = conversationRepository
            .findByUser_IdOrderByUpdatedAtDesc(user.getId());

        return conversations.stream().map(c -> {
            int msgCount = messageRepository.countByConversation_Id(c.getId());
            return new ConversationListResponse(
                c.getId(),
                c.getTitle(),
                c.getModel(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                msgCount
            );
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long conversationId) {
        User user = currentUser();
        ChatConversation conversation = conversationRepository.findByIdAndUser_Id(conversationId, user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        List<ChatMessage> messages = messageRepository
            .findByConversation_IdOrderByCreatedAtAsc(conversationId);

        return messages.stream()
            .map(m -> new ChatMessageResponse(
                m.getId(),
                m.getRole(),
                m.getContent(),
                m.getModel(),
                m.getTokensIn(),
                m.getTokensOut(),
                m.getCreatedAt()
            ))
            .collect(Collectors.toList());
    }

    @Transactional
    public void deleteConversation(Long conversationId) {
        User user = currentUser();
        ChatConversation conversation = conversationRepository.findByIdAndUser_Id(conversationId, user.getId())
            .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        conversationRepository.delete(conversation);
    }

    private String buildUserContext(User user) {
        StringBuilder sb = new StringBuilder();

        try {
            // Portfolio context
            List<Portfolio> portfolios = portfolioRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
            if (!portfolios.isEmpty()) {
                sb.append("【보유 포트폴리오】\n");
                List<Long> portfolioIds = portfolios.stream().map(Portfolio::getId).collect(Collectors.toList());
                List<PortfolioPosition> allPositions = positionRepository.findByPortfolio_IdInOrderByCreatedAtDesc(portfolioIds);
                Map<Long, List<PortfolioPosition>> positionsByPortfolio = allPositions.stream()
                    .collect(Collectors.groupingBy(p -> p.getPortfolio().getId()));

                for (Portfolio portfolio : portfolios) {
                    sb.append("- ").append(portfolio.getName()).append(": ");
                    List<PortfolioPosition> positions = positionsByPortfolio.getOrDefault(portfolio.getId(), List.of());
                    if (positions.isEmpty()) {
                        sb.append("(포지션 없음)");
                    } else {
                        String posStr = positions.stream()
                            .map(p -> p.getTicker() + "(" + p.getMarket() + ") " + p.getQuantity() + "주 @" + p.getEntryPrice())
                            .collect(Collectors.joining(", "));
                        sb.append(posStr);
                    }
                    sb.append("\n");
                }
            }

            // Watchlist context
            List<WatchlistItem> watchlist = watchlistRepository.findByUser_IdAndTestModeOrderByCreatedAtDesc(user.getId(), false);
            if (!watchlist.isEmpty()) {
                sb.append("【관심종목】\n");
                String watchStr = watchlist.stream()
                    .map(w -> w.getTicker() + "(" + w.getMarket() + ")")
                    .distinct()
                    .limit(20)
                    .collect(Collectors.joining(", "));
                sb.append(watchStr).append("\n");
            }
        } catch (Exception e) {
            // Context injection is best-effort; do not fail the chat request
        }

        return sb.toString();
    }

    private String generateTitle(String firstMessage) {
        return firstMessage.length() > 50
            ? firstMessage.substring(0, 47) + "..."
            : firstMessage;
    }

    private User currentUser() {
        String email = SecurityUtil.currentEmail();
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
