package com.ikae.snowthing.testsupport;

import java.sql.Statement;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 테스트 스키마는 Hibernate create-drop으로 엔티티에서 만들어져 FULLTEXT ngram 인덱스가 없다. 운영 027 마이그레이션과 같은 인덱스를 같은 불용어
 * 설정으로 만든다. 이미 있으면 아무것도 하지 않는다.
 */
public final class SearchDocumentFullTextIndex {

    public static final String INDEX_NAME = "ftx_search_document_title_body";

    private SearchDocumentFullTextIndex() {}

    public static void ensure(JdbcTemplate jdbc) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.statistics"
                                + " WHERE table_schema = DATABASE()"
                                + " AND table_name = 'search_document' AND index_name = ?",
                        Integer.class,
                        INDEX_NAME);
        if (count != null && count > 0) {
            return;
        }
        jdbc.execute(
                (ConnectionCallback<Void>)
                        connection -> {
                            try (Statement statement = connection.createStatement()) {
                                statement.execute(
                                        "SET @previous_ft_stopword ="
                                                + " @@SESSION.innodb_ft_enable_stopword");
                                statement.execute("SET SESSION innodb_ft_enable_stopword = OFF");
                                try {
                                    statement.execute(
                                            "ALTER TABLE search_document ADD FULLTEXT INDEX "
                                                    + INDEX_NAME
                                                    + " (title, body) WITH PARSER ngram");
                                } finally {
                                    statement.execute(
                                            "SET SESSION innodb_ft_enable_stopword ="
                                                    + " @previous_ft_stopword");
                                }
                            }
                            return null;
                        });
    }
}
