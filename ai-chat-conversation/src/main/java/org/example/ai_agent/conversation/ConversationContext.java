package org.example.ai_agent.conversation;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Loads completed history and selects a complete model input under the model budget. */
public interface ConversationContext {
    CompletionStage<PreparedInput> prepare(InputRequest request);

    /** Candidates preserve the question and contain successively less relevant evidence. */
    record InputRequest(Long conversationId, String modelId, List<String> inputCandidates,
                        String systemPrompt, List<String> toolDescriptions) {
        public InputRequest {
            inputCandidates = List.copyOf(inputCandidates);
            toolDescriptions = List.copyOf(toolDescriptions);
            if (inputCandidates.isEmpty()) throw new IllegalArgumentException("Model input is required");
        }
    }

    record PreparedInput(String input, int candidateIndex, List<CompletedContextTurn> turns,
                         int estimatedInputTokens) {
        public PreparedInput { turns = List.copyOf(turns); }
    }
}
