package ru.korteng.finance_manager;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Sql(scripts = {
        "/test-data-categories.sql",
        "/test-data-transactions.sql"
})
public abstract class AbstractIntegrationTest {
}
