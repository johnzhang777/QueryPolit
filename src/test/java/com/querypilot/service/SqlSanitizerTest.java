package com.querypilot.service;

import com.querypilot.exception.SqlSafetyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SqlSanitizerTest {

    private final SqlSanitizer sanitizer = new SqlSanitizer();

    // --- SELECT statements should pass ---

    @Test
    @DisplayName("Simple SELECT passes sanitization")
    void testSimpleSelectPasses() {
        String sql = "SELECT * FROM users";
        String result = sanitizer.sanitize(sql);
        assertNotNull(result);
        assertTrue(result.toUpperCase().contains("SELECT"));
    }

    @Test
    @DisplayName("SELECT with JOIN passes sanitization")
    void testSelectWithJoinPasses() {
        String sql = "SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id";
        String result = sanitizer.sanitize(sql);
        assertNotNull(result);
        assertTrue(result.toUpperCase().contains("SELECT"));
    }

    @Test
    @DisplayName("SELECT with subquery passes sanitization")
    void testSelectWithSubqueryPasses() {
        String sql = "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders)";
        String result = sanitizer.sanitize(sql);
        assertNotNull(result);
    }

    // --- Destructive statements should be blocked ---

    @Test
    @DisplayName("DELETE statement is blocked")
    void testDeleteBlocked() {
        SqlSafetyException ex = assertThrows(SqlSafetyException.class,
                () -> sanitizer.sanitize("DELETE FROM users WHERE id = 1"));
        assertTrue(ex.getMessage().contains("DELETE"));
    }

    @Test
    @DisplayName("UPDATE statement is blocked")
    void testUpdateBlocked() {
        SqlSafetyException ex = assertThrows(SqlSafetyException.class,
                () -> sanitizer.sanitize("UPDATE users SET name = 'hacked' WHERE id = 1"));
        assertTrue(ex.getMessage().contains("UPDATE"));
    }

    @Test
    @DisplayName("INSERT statement is blocked")
    void testInsertBlocked() {
        SqlSafetyException ex = assertThrows(SqlSafetyException.class,
                () -> sanitizer.sanitize("INSERT INTO users (name) VALUES ('test')"));
        assertTrue(ex.getMessage().contains("INSERT"));
    }

    @Test
    @DisplayName("DROP statement is blocked")
    void testDropBlocked() {
        SqlSafetyException ex = assertThrows(SqlSafetyException.class,
                () -> sanitizer.sanitize("DROP TABLE users"));
        assertTrue(ex.getMessage().contains("DROP"));
    }

    @Test
    @DisplayName("TRUNCATE statement is blocked")
    void testTruncateBlocked() {
        SqlSafetyException ex = assertThrows(SqlSafetyException.class,
                () -> sanitizer.sanitize("TRUNCATE TABLE users"));
        assertTrue(ex.getMessage().contains("TRUNCATE"));
    }

    @Test
    @DisplayName("ALTER statement is blocked")
    void testAlterBlocked() {
        SqlSafetyException ex = assertThrows(SqlSafetyException.class,
                () -> sanitizer.sanitize("ALTER TABLE users ADD COLUMN email VARCHAR(255)"));
        assertTrue(ex.getMessage().contains("ALTER"));
    }

    // --- LIMIT enforcement ---

    @Test
    @DisplayName("LIMIT 100 is appended when missing")
    void testLimitAppended() {
        String result = sanitizer.sanitize("SELECT * FROM users");
        assertTrue(result.contains("LIMIT 100"));
    }

    @Test
    @DisplayName("Existing LIMIT is preserved")
    void testExistingLimitPreserved() {
        String result = sanitizer.sanitize("SELECT * FROM users LIMIT 50");
        assertTrue(result.contains("LIMIT 50"));
        assertFalse(result.contains("LIMIT 100"));
    }

    @Test
    @DisplayName("LIMIT is appended after removing trailing semicolon")
    void testLimitWithSemicolon() {
        String result = sanitizer.sanitize("SELECT * FROM users;");
        assertTrue(result.contains("LIMIT 100"));
        assertFalse(result.contains(";"));
    }

    // --- Comment stripping ---

    @Test
    @DisplayName("Single-line comments are stripped")
    void testSingleLineCommentStripped() {
        String sql = "SELECT * FROM users -- this is a comment";
        String cleaned = sanitizer.stripComments(sql);
        assertFalse(cleaned.contains("--"));
        assertFalse(cleaned.contains("this is a comment"));
    }

    @Test
    @DisplayName("Multi-line comments are stripped")
    void testMultiLineCommentStripped() {
        String sql = "SELECT * /* hidden */ FROM users";
        String cleaned = sanitizer.stripComments(sql);
        assertFalse(cleaned.contains("/*"));
        assertFalse(cleaned.contains("hidden"));
    }

    @Test
    @DisplayName("SQL with comment-hidden DROP is caught after stripping")
    void testCommentHiddenAttack() {
        // Attempt: use comments to hide destructive SQL
        String sql = "DROP /* this hides the SELECT */ TABLE users";
        SqlSafetyException ex = assertThrows(SqlSafetyException.class, () -> sanitizer.sanitize(sql));
        assertTrue(ex.getMessage().contains("DROP"));
    }

    // --- Edge cases ---

    @Test
    @DisplayName("Null SQL throws exception")
    void testNullSqlThrows() {
        SqlSafetyException ex = assertThrows(SqlSafetyException.class, () -> sanitizer.sanitize(null));
        assertNotNull(ex.getMessage());
    }

    @Test
    @DisplayName("Blank SQL throws exception")
    void testBlankSqlThrows() {
        SqlSafetyException ex = assertThrows(SqlSafetyException.class, () -> sanitizer.sanitize("   "));
        assertNotNull(ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not valid sql at all", "GRANT ALL ON *.* TO root"})
    @DisplayName("Invalid or blocked SQL throws exception")
    void testInvalidSqlThrows(String sql) {
        SqlSafetyException ex = assertThrows(SqlSafetyException.class, () -> sanitizer.sanitize(sql));
        assertNotNull(ex.getMessage());
    }
}
