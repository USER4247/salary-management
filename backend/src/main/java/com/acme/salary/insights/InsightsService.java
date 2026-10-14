package com.acme.salary.insights;

import com.acme.salary.auth.Ability;
import com.acme.salary.auth.Ability.Action;
import com.acme.salary.auth.Ability.Subject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "How do we pay people?": headcount, total, average, median, lowest and highest pay, overall and per group,
 * all in US dollars. The database does the counting and adding, so the 10,000 rows never leave it.
 */
@Service
public class InsightsService {

    private static final Logger log = LoggerFactory.getLogger(InsightsService.class);

    /** What to group by. The column names are fixed here and never come from the user, so they are safe in SQL. */
    public enum GroupBy {
        COUNTRY("country"), DEPARTMENT("department"), JOB_TITLE("job_title");

        private final String column;

        GroupBy(String column) {
            this.column = column;
        }
    }

    /** Pay figures for one group, in whole US dollars per year. */
    public record Stats(String key, long headcount, long total, long average, long median, long min, long max) {
    }

    public record PayInsights(GroupBy groupBy, String currency, String assumption, Stats overall, List<Stats> groups) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final FxRates fx;

    public InsightsService(NamedParameterJdbcTemplate jdbc, FxRates fx) {
        this.jdbc = jdbc;
        this.fx = fx;
    }

    /**
     * @param status "ACTIVE", "ON_LEAVE", "TERMINATED", or "ALL" for everyone
     * @param ability decides which countries the caller may see; a regional officer only gets their own
     */
    @Transactional(readOnly = true)   // goes to the read-only connections
    public PayInsights pay(GroupBy groupBy, String status, Ability ability) {
        Optional<Set<String>> allowedCountries = ability.countryLimit(Action.READ, Subject.INSIGHTS);
        if (allowedCountries.isPresent() && allowedCountries.get().isEmpty()) {
            return new PayInsights(groupBy, "USD", fx.describe(), emptyStats("All"), List.of());   // allowed to see nothing
        }

        long started = System.nanoTime();
        List<Stats> groups = query("e." + groupBy.column, status, allowedCountries);
        Stats overall = query("'All'", status, allowedCountries).stream().findFirst().orElse(emptyStats("All"));
        log.debug("Pay insights by {} ({} groups) took {} ms", groupBy, groups.size(), (System.nanoTime() - started) / 1_000_000);

        return new PayInsights(groupBy, "USD", fx.describe(), overall, groups);
    }

    /**
     * One query that returns a row per group. Reading it from the inside out:
     * <ol>
     *   <li>{@code converted}: every employee's salary turned into dollars;</li>
     *   <li>{@code ranked}: each salary numbered within its group, from lowest to highest, plus the size of the group;</li>
     *   <li>the final SELECT adds up per group. SQLite has no built-in median, so we average the middle one or two
     *       salaries: row number (count+1)/2 and (count+2)/2 are the same row for an odd count and the two middle
     *       rows for an even count.</li>
     * </ol>
     */
    private List<Stats> query(String groupExpression, String status, Optional<Set<String>> allowedCountries) {
        Map<String, Object> params = new HashMap<>();
        StringBuilder where = new StringBuilder("WHERE 1 = 1");
        if (status != null && !"ALL".equals(status)) {
            where.append(" AND e.status = :status");
            params.put("status", status);
        }
        if (allowedCountries.isPresent()) {
            where.append(" AND e.country IN (:countries)");
            params.put("countries", allowedCountries.get());
        }

        String sql = """
                WITH converted AS (
                    SELECT %s AS grp, %s AS usd
                    FROM employees e %s
                ), ranked AS (
                    SELECT grp, usd,
                           ROW_NUMBER() OVER (PARTITION BY grp ORDER BY usd) AS position,
                           COUNT(*)     OVER (PARTITION BY grp)              AS size
                    FROM converted
                    WHERE usd IS NOT NULL
                )
                SELECT grp, COUNT(*) AS headcount, SUM(usd) AS total, AVG(usd) AS average,
                       AVG(CASE WHEN position IN ((size + 1) / 2, (size + 2) / 2) THEN usd END) AS median,
                       MIN(usd) AS lowest, MAX(usd) AS highest
                FROM ranked
                GROUP BY grp
                ORDER BY grp
                """.formatted(groupExpression, fx.sqlToUsd("e.annual_salary", "e.currency"), where);

        return jdbc.query(sql, params, (row, i) -> new Stats(
                row.getString("grp"), row.getLong("headcount"), Math.round(row.getDouble("total")),
                Math.round(row.getDouble("average")), Math.round(row.getDouble("median")),
                Math.round(row.getDouble("lowest")), Math.round(row.getDouble("highest"))));
    }

    private static Stats emptyStats(String key) {
        return new Stats(key, 0, 0, 0, 0, 0, 0);
    }
}
