package com.acme.salary.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FxRatesTest {

    private final FxRates fx = new FxRates();

    @Test
    void convertsToDollarsUsingTheFixedRate() {
        assertThat(fx.toUsd(8_300_000, "INR")).isEqualTo(100_000.0);
        assertThat(fx.toUsd(150_000, "USD")).isEqualTo(150_000.0);
        assertThat(fx.toUsd(15_000_000, "JPY")).isEqualTo(100_000.0);
    }

    @Test
    void aCurrencyWithoutARateIsAnError() {
        assertThatThrownBy(() -> fx.toUsd(100, "XYZ")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("XYZ");
    }

    @Test
    void theSqlVersionKnowsEveryCurrencyAndUsesTheSameRates() {
        String sql = fx.sqlToUsd("e.annual_salary", "e.currency");

        assertThat(sql).startsWith("CASE e.currency").endsWith("END")
                .contains("WHEN 'INR' THEN e.annual_salary / 83.0")
                .contains("WHEN 'EUR' THEN e.annual_salary / 0.92");
    }

    @Test
    void describesTheRatesSoNobodyMistakesThemForLiveOnes() {
        assertThat(fx.describe()).contains("fixed rates").contains("83.0 INR").contains("0.92 EUR");
    }
}
