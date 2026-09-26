package org.example.ai_agent.service.memory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationMemoryMigrationContractTest {

    @Test
    void v11IsExpandOnlyAndEveryMemoryTableFollowsItsConversation() throws IOException {
        String migration = migrationSql();

        for (String table : new String[]{"conversation_memory", "conversation_summary",
                "conversation_constraint", "conversation_archive_turn"}) {
            assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS " + table + " ("), table);
        }
        assertEquals(4, migration.split("REFERENCES conversation \\(id\\) ON DELETE CASCADE", -1).length - 1,
                "derived memory must never outlive its conversation");
        assertTrue(migration.contains("UNIQUE KEY uk_conversation_summary_version (conversation_id, version)"));
        assertTrue(migration.contains("UNIQUE KEY uk_conversation_archive_turn_seq (conversation_id, seq)"));
        assertTrue(migration.contains("WITH PARSER ngram"), "Chinese recall needs the ngram parser");
        assertFalse(migration.contains("ALTER TABLE message"), "the hot message table is not rebuilt");
        assertFalse(migration.contains("DROP "));
    }

    private String migrationSql() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V11__add_conversation_memory.sql")) {
            if (input == null) throw new IllegalStateException("V11 migration resource is missing");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
