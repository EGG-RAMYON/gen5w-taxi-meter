package dev.gen5w.taxi;

public final class SpeedIntervalsTest {
    private static void expect(long value, long expected) {
        if (value != expected) throw new AssertionError(value + " != " + expected);
    }

    public static void main(String[] args) {
        SpeedIntervals interval = new SpeedIntervals();
        expect(interval.accept(0, 100), 0);
        expect(interval.accept(0, 350), 250);
        expect(interval.accept(Float.NaN, 600), 0);
        expect(interval.accept(36, 850), 0);
        expect(interval.accept(36, 1100), 250);
        expect(interval.accept(36, 2200), 0);
        expect(interval.accept(36, 2450), 250);
        expect(interval.accept(36, 2450), 0);
        interval.reset();
        expect(interval.accept(36, 2700), 0);
        for (String bad : new String[]{null, "-", "-2", "Infinity", "NaN", "301", "bad"}) {
            if (!Float.isNaN(SpeedIntervals.parse(bad))) throw new AssertionError(bad);
        }
        if (SpeedIntervals.parse("36.5") != 36.5f) throw new AssertionError("decimal speed");
        System.out.println("SpeedIntervalsTest OK");
    }
}
