import { useState, useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useToast } from "../components/ui/Toast";
import { useTranslation } from "../hooks/useTranslation";
import { useConversations, useConversationMessages, useConversationMutations } from "../hooks/queries";
import styles from "../css/Chatbot.module.css";

export default function Chatbot() {
  const [currentConversation, setCurrentConversation] = useState(null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [model, setModel] = useState("opus");
  const messagesEndRef = useRef(null);
  const toast = useToast();
  const { t } = useTranslation();
  const qc = useQueryClient();

  // React Query hooks
  const { data: conversations = [] } = useConversations();
  const { data: fetchedMessages } = useConversationMessages(currentConversation);
  const chatMutations = useConversationMutations();

  // Sync fetched messages into local state (for optimistic updates)
  useEffect(() => {
    if (fetchedMessages) {
      setMessages(fetchedMessages);
    }
  }, [fetchedMessages]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const sendMessage = async () => {
    const trimmed = input.trim();
    if (!trimmed) return;

    const userMessage = trimmed;
    setInput("");
    setLoading(true);

    // Optimistic update
    const tempMsg = {
      role: "user",
      content: userMessage,
      createdAt: new Date().toISOString()
    };
    setMessages(prev => [...prev, tempMsg]);

    try {
      const response = await chatMutations.send.mutateAsync({
        message: userMessage,
        conversationId: currentConversation,
        model
      });

      // Update conversation ID if new
      if (!currentConversation) {
        setCurrentConversation(response.conversationId);
      }

      // Add assistant message
      const assistantMsg = {
        role: "assistant",
        content: response.message,
        model: response.model,
        tokensIn: response.tokensIn,
        tokensOut: response.tokensOut,
        createdAt: response.timestamp
      };

      setMessages(prev => [...prev.filter(m => m !== tempMsg), tempMsg, assistantMsg]);
    } catch (e) {
      toast.error(e.message || t('chatbot.failedToSend'));
      // Remove optimistic message on error
      setMessages(prev => prev.filter(m => m !== tempMsg));
    } finally {
      setLoading(false);
    }
  };

  const newConversation = () => {
    setCurrentConversation(null);
    setMessages([]);
  };

  const handleDeleteConversation = async (id) => {
    if (!window.confirm(t('chatbot.deleteConversation'))) return;

    try {
      await chatMutations.remove.mutateAsync(id);
      toast.success(t('chatbot.conversationDeleted'));
      if (currentConversation === id) {
        newConversation();
      }
    } catch (e) {
      toast.error(e.message || t('chatbot.failedToDelete'));
    }
  };

  return (
    <div className={styles.container}>
      {/* Sidebar */}
      <aside className={styles.sidebar}>
        <button className={styles.newChatBtn} onClick={newConversation}>
          + {t('chatbot.newChat')}
        </button>

        <div className={styles.conversationList}>
          {conversations.map(c => (
            <div
              key={c.id}
              className={`${styles.conversationItem} ${currentConversation === c.id ? styles.active : ""}`}
              onClick={() => setCurrentConversation(c.id)}
            >
              <div className={styles.conversationTitle}>
                {c.title || t('chatbot.newConversation')}
              </div>
              <div className={styles.conversationMeta}>
                {c.messageCount} {t('chatbot.messages')} · {c.model}
              </div>
              <button
                className={styles.deleteConvBtn}
                onClick={(e) => {
                  e.stopPropagation();
                  handleDeleteConversation(c.id);
                }}
              >
                ×
              </button>
            </div>
          ))}
        </div>
      </aside>

      {/* Main Chat Area */}
      <main className={styles.main}>
        <header className={styles.header}>
          <h1>{t('chatbot.title')}</h1>
          <select value={model} onChange={(e) => setModel(e.target.value)} className={styles.modelSelect}>
            <option value="opus">{t('chatbot.modelOpus')}</option>
            <option value="sonnet">{t('chatbot.modelSonnet')}</option>
            <option value="haiku">{t('chatbot.modelHaiku')}</option>
          </select>
        </header>

        <div className={styles.messages}>
          {messages.length === 0 && (
            <div className={styles.welcome}>
              <h2>{t('chatbot.welcome')}</h2>
              <p>{t('chatbot.welcomeMessage')}</p>

              <div className={styles.suggestions}>
                <button onClick={() => setInput(t('chatbot.suggestion1'))}>
                  {t('chatbot.suggestion1')}
                </button>
                <button onClick={() => setInput(t('chatbot.suggestion2'))}>
                  {t('chatbot.suggestion2')}
                </button>
                <button onClick={() => setInput(t('chatbot.suggestion3'))}>
                  {t('chatbot.suggestion3')}
                </button>
              </div>
            </div>
          )}

          {messages.map((msg, idx) => (
            <div key={idx} className={`${styles.message} ${msg.role === "user" ? styles.userMessage : styles.assistantMessage}`}>
              <div className={styles.messageHeader}>
                <strong>{msg.role === "user" ? t('chatbot.you') : t('chatbot.assistant')}</strong>
                {msg.model && <span className={styles.model}>({msg.model})</span>}
              </div>
              <div className={styles.messageContent}>
                {msg.content}
              </div>
              {msg.tokensIn && (
                <div className={styles.messageFooter}>
                  {t('chatbot.tokens')}: {msg.tokensIn} in / {msg.tokensOut} out
                </div>
              )}
            </div>
          ))}

          {loading && (
            <div className={styles.typing}>
              <span></span><span></span><span></span>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        <div className={styles.inputArea}>
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyPress={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
              }
            }}
            placeholder={t('chatbot.inputPlaceholder')}
            rows={3}
            className={styles.textarea}
            disabled={loading}
          />
          <button onClick={sendMessage} disabled={loading || !input.trim()} className={styles.sendBtn}>
            {loading ? t('chatbot.sending') : t('chatbot.send')}
          </button>
        </div>
      </main>
    </div>
  );
}
