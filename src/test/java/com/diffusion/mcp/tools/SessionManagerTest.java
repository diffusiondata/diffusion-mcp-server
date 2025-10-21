package com.diffusion.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.session.SessionFactory;

/**
 * Unit tests for {@link SessionManager}.
 */
class SessionManagerTest {

    private SessionFactory factory;
    private ScheduledExecutorService scheduler;
    private SessionManager manager;

    // The task scheduled by the constructor (drives checkSessions()).
    private Runnable monitorTask;

    @BeforeEach
    void setUp() {
        factory = mock(SessionFactory.class, RETURNS_SELF);
        scheduler = mock(ScheduledExecutorService.class);

        ArgumentCaptor<Runnable> runCap = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleAtFixedRate(
                runCap.capture(), anyLong(), anyLong(), any(TimeUnit.class)))
            .thenReturn(mock(ScheduledFuture.class));

        manager = new SessionManager(factory, scheduler);
        monitorTask = runCap.getValue();

        // called once; we don't over-specify exact cadence
        verify(scheduler, times(1)).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any());
    }

    @Test
    void testConnectThenGetAndReplacementClosesOld() {
        Session s1 = mockSession(false);
        Session s2 = mockSession(false);

        when(factory.open(anyString())).thenReturn(s1).thenReturn(s2);

        // First connect
        Session r1 = manager.connect("mcp-1", "user", "pass", "ws://example", Map.of());
        assertThat(r1).isSameAs(s1);
        assertThat(manager.get("mcp-1")).isSameAs(s1);

        // Second connect with same id should close the old session and replace
        Session r2 = manager.connect("mcp-1", "user", "pass", "ws://example", Map.of("x", "y"));
        assertThat(r2).isSameAs(s2);
        assertThat(manager.get("mcp-1")).isSameAs(s2);
        verify(s1, times(1)).close();

        // Basic factory chaining happened (don’t over-constrain ordering)
        verify(factory, atLeastOnce()).principal("user");
        verify(factory, atLeastOnce()).password("pass");
        verify(factory, atLeastOnce()).listener(any());
        verify(factory, atLeastOnce()).errorHandler(any());
        verify(factory, atLeastOnce()).open("ws://example");
        // properties() only when non-empty
        verify(factory, atLeastOnce()).properties(anyMap());
    }

    @Test
    void testDisconnectClosesAndRemoves() {
        Session s = mockSession(false);
        when(factory.open(anyString())).thenReturn(s);

        manager.connect("mcp-1", "u", "p", "ws://x", Map.of());

        Session removed = manager.disconnect("mcp-1");
        assertThat(removed).isSameAs(s);
        verify(s, times(1)).close();
        assertThat(manager.get("mcp-1")).isNull();
    }

    @Test
    void testMonitorRemovesAlreadyClosedSessionWithoutClosing() {
        Session closed = mockSession(true); // isClosed() == true
        when(factory.open(anyString())).thenReturn(closed);

        manager.connect("mcp-1", "u", "p", "ws://x", Map.of());

        // Run the scheduled monitor once
        monitorTask.run();

        // Should remove the closed session; not expected to call close()
        assertThat(manager.get("mcp-1")).isNull();
        verify(closed, never()).close();
        verify(closed, atLeastOnce()).getState();
    }

    @Test
    void testMonitorClosesIdleSession() throws Exception {
        Session idle = mockSession(false); // open but idle too long
        when(factory.open(anyString())).thenReturn(idle);

        manager.connect("mcp-1", "u", "p", "ws://x", Map.of());

        // Force last activity to be older than the idle timeout
        long idleMillis = reflectIdleTimeoutMillis();
        @SuppressWarnings("unchecked")
        Map<String, Long> lastActivity = (Map<String, Long>) reflectField(manager, "theLastActivity");
        lastActivity.put("mcp-1", System.currentTimeMillis() - idleMillis - 1000);

        monitorTask.run();

        assertThat(manager.get("mcp-1")).isNull();
        verify(idle, times(1)).close();
    }

    @Test
    void testShutdownClosesAllAndStopsScheduler() {
        Session s1 = mockSession(false);
        Session s2 = mockSession(false);
        when(factory.open(anyString())).thenReturn(s1, s2);

        manager.connect("a", "u", "p", "ws://x", Map.of());
        manager.connect("b", "u", "p", "ws://x", Map.of());

        manager.shutdown();

        verify(scheduler, times(1)).shutdown();
        verify(s1, times(1)).close();
        verify(s2, times(1)).close();

        assertThat(manager.get("a")).isNull();
        assertThat(manager.get("b")).isNull();
    }

    // ---- helpers (do not start with 'test') ----

    private static Session mockSession(boolean closed) {
        Session.State state = mock(Session.State.class);
        when(state.isClosed()).thenReturn(closed);

        Session s = mock(Session.class);
        when(s.getState()).thenReturn(state);
        return s;
    }

    private static Object reflectField(Object target, String fieldName) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        Object val = f.get(target);
        if (val == null && "theLastActivity".equals(fieldName)) {
            val = new HashMap<String, Long>();
            f.set(target, val);
        }
        return val;
    }

    private static long reflectIdleTimeoutMillis() throws Exception {
        Field f = SessionManager.class.getDeclaredField("IDLE_TIMEOUT");
        f.setAccessible(true);
        Object v = f.get(null);
        if (v instanceof Duration d) {
            return d.toMillis();
        } else if (v instanceof Number n) {
            return n.longValue();
        } else {
            throw new IllegalStateException("Unsupported IDLE_TIMEOUT type: " + (v == null ? "null" : v.getClass()));
        }
    }
}
