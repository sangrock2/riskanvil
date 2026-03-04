import { apiFetch } from "./http";

/**
 * AI Chatbot API client
 */

export async function sendChatMessage(message, conversationId = null, model = "opus") {
  return apiFetch("/api/chatbot/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      message,
      conversationId,
      model
    })
  });
}

export async function getConversations() {
  return apiFetch("/api/chatbot/conversations");
}

export async function getConversationMessages(conversationId) {
  return apiFetch(`/api/chatbot/conversations/${conversationId}/messages`);
}

export async function deleteConversation(conversationId) {
  return apiFetch(`/api/chatbot/conversations/${conversationId}`, {
    method: "DELETE"
  });
}
