import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getConversations, getConversationMessages, sendChatMessage, deleteConversation } from "../../api/chatbot";
import { STALE_TIMES } from "../../api/queryClient";

export function useConversations() {
  return useQuery({
    queryKey: ["conversations"],
    queryFn: getConversations,
    staleTime: STALE_TIMES.CONVERSATIONS,
  });
}

export function useConversationMessages(conversationId) {
  return useQuery({
    queryKey: ["conversation-messages", conversationId],
    queryFn: () => getConversationMessages(conversationId),
    enabled: !!conversationId,
    staleTime: STALE_TIMES.CONVERSATIONS,
  });
}

export function useConversationMutations() {
  const qc = useQueryClient();

  const send = useMutation({
    mutationFn: ({ message, conversationId, model }) =>
      sendChatMessage(message, conversationId, model),
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: ["conversations"] });
      if (data.conversationId) {
        qc.invalidateQueries({ queryKey: ["conversation-messages", data.conversationId] });
      }
    },
  });

  const remove = useMutation({
    mutationFn: deleteConversation,
    onSuccess: () => qc.invalidateQueries({ queryKey: ["conversations"] }),
  });

  return { send, remove };
}
