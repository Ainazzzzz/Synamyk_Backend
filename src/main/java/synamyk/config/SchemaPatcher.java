package synamyk.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Idempotent schema tweaks that {@code ddl-auto: update} cannot do on its own
 * (it never relaxes constraints). Mirrors {@code V17__ort_full_test.sql}.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SchemaPatcher implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    private static final List<String> STATEMENTS = List.of(
            // Catalogue purchases (ALL_TESTS / ALL_TEXTS) are not tied to a test.
            "ALTER TABLE payments ALTER COLUMN test_id DROP NOT NULL",
            // Sections are no longer sold separately: the entity stopped writing these columns,
            // so the leftover NOT NULL columns need a default to keep inserts working.
            "ALTER TABLE sub_tests ALTER COLUMN is_paid SET DEFAULT false",
            "UPDATE sub_tests SET is_paid = false, price = 0 WHERE is_paid = true OR price <> 0"
    );

    @Override
    public void run(ApplicationArguments args) {
        for (String sql : STATEMENTS) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.warn("Schema patch skipped ({}): {}", sql, e.getMessage());
            }
        }
    }
}
