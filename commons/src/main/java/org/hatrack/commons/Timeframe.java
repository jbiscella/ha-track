package org.hatrack.commons;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Timeframe(int amount, Unit unit) {

    public enum Unit {
        SECOND,
        MINUTE,
        HOUR,
        DAY,
        WEEK,
        MONTH,
        YEAR
    }

    private static final Pattern WIRE = Pattern.compile("(\\d+)([smhdwMy])");

    public Timeframe {
        Objects.requireNonNull(unit, "unit");
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be >= 1, was " + amount);
        }
    }

    public static Timeframe fromWire(String wire) {
        Objects.requireNonNull(wire, "wire");
        Matcher matcher = WIRE.matcher(wire);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("invalid timeframe wire format: " + wire);
        }
        int amount = Integer.parseInt(matcher.group(1));
        return new Timeframe(amount, unitForSuffix(matcher.group(2)));
    }

    public String wire() {
        return amount + suffixForUnit(unit);
    }

    private static Unit unitForSuffix(String suffix) {
        return switch (suffix) {
            case "s" -> Unit.SECOND;
            case "m" -> Unit.MINUTE;
            case "h" -> Unit.HOUR;
            case "d" -> Unit.DAY;
            case "w" -> Unit.WEEK;
            case "M" -> Unit.MONTH;
            case "y" -> Unit.YEAR;
            default -> throw new IllegalArgumentException("unknown unit suffix: " + suffix);
        };
    }

    private static String suffixForUnit(Unit unit) {
        return switch (unit) {
            case SECOND -> "s";
            case MINUTE -> "m";
            case HOUR -> "h";
            case DAY -> "d";
            case WEEK -> "w";
            case MONTH -> "M";
            case YEAR -> "y";
        };
    }
}
