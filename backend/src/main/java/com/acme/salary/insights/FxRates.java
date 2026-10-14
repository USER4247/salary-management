package com.acme.salary.insights;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Exchange rates used to compare pay across countries. They are FIXED on purpose: live rates would make the
 * numbers change between two page loads and need an outside service. A real system would load dated rates.
 * Each value says how many units of that currency make one US dollar.
 */
@Component
public class FxRates {

    private static final Map<String, Double> PER_USD = Map.of(
            "USD", 1.0, "INR", 83.0, "GBP", 0.79, "EUR", 0.92, "CAD", 1.36,
            "AUD", 1.52, "JPY", 150.0, "SGD", 1.34, "BRL", 5.0);

    public double toUsd(long amount, String currency) {
        Double rate = PER_USD.get(currency);
        if (rate == null) {
            throw new IllegalArgumentException("No exchange rate for " + currency);
        }
        return amount / rate;
    }

    /**
     * The same conversion written as SQL, so the database can add up and average in dollars itself:
     * {@code CASE currency WHEN 'INR' THEN annual_salary / 83.0 ... END}. The numbers come from the constant above
     * (never from a user), so building SQL text from them is safe. A currency without a rate gives NULL.
     */
    public String sqlToUsd(String amountColumn, String currencyColumn) {
        String branches = PER_USD.entrySet().stream()
                .map(rate -> "WHEN '" + rate.getKey() + "' THEN " + amountColumn + " / " + rate.getValue())
                .collect(Collectors.joining(" "));
        return "CASE " + currencyColumn + " " + branches + " END";
    }

    /** Human-readable version of the rates, shown next to the numbers so nobody mistakes them for live rates. */
    public String describe() {
        String rates = PER_USD.entrySet().stream()
                .filter(rate -> !rate.getKey().equals("USD"))
                .sorted(Map.Entry.comparingByKey())
                .map(rate -> rate.getValue() + " " + rate.getKey())
                .collect(Collectors.joining(", "));
        return "Converted to US dollars at fixed rates: 1 USD = " + rates + ".";
    }
}
