package pi.integrated.preevaluation.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiUserMessageMapperTest {

    @Test
    void mapsModelNotFoundToFriendlyMessage() {
        String msg = AiUserMessageMapper.toUserFriendlyError(
                "The model 'llama-3.1-8b-instant' does not exist or you do not have access to it.", 404);
        assertTrue(msg.contains("GROQ_MODEL"));
        assertFalse(msg.contains("llama-3.1-8b-instant"));
    }

    @Test
    void mapsRateLimitWithoutEchoingProviderDetails() {
        String msg = AiUserMessageMapper.toUserFriendlyError("rate limit exceeded for tenant", 429);
        assertTrue(msg.toLowerCase().contains("busy"));
    }
}
