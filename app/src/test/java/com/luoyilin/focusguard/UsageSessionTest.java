package com.luoyilin.focusguard;

import org.junit.Test;
import static org.junit.Assert.*;

public class UsageSessionTest {
    @Test public void closedFloatingActivityStopsWithoutAnotherResume() {
        UsageSession session = new UsageSession();
        session.resume("FloatingActivity", 1000);
        session.end("FloatingActivity", 5000);
        assertFalse(session.isActive());
        assertEquals(4000, session.totalAt(20000));
        assertEquals(4000, session.totalAt(30000));
    }

    @Test public void overlappingActivitiesCountOnce() {
        UsageSession session = new UsageSession();
        session.resume("A", 1000);
        session.resume("B", 2000);
        session.end("A", 3000);
        assertTrue(session.isActive());
        session.end("B", 5000);
        session.end("B", 6000);
        assertEquals(4000, session.totalAt(10000));
    }

    @Test public void screenOffStopsAndReopeningStartsNewInterval() {
        UsageSession session = new UsageSession();
        session.resume("A", 1000);
        session.resume("A", 2000);
        session.endAll(3000);
        assertFalse(session.isActive());
        session.resume("A", 8000);
        assertEquals(3000, session.totalAt(9000));
    }
}
