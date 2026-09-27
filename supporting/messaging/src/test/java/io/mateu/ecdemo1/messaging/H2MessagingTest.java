package io.mateu.ecdemo1.messaging;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/** The contract on H2, the database the front office's tests run on. */
class H2MessagingTest extends MessagingContract {

    static final DataSource H2 = new DriverManagerDataSource(
            "jdbc:h2:mem:messaging;DB_CLOSE_DELAY=-1;CASE_INSENSITIVE_IDENTIFIERS=TRUE", "sa", "");

    @Override
    DataSource dataSource() {
        return H2;
    }
}
