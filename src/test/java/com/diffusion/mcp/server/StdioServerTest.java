package com.diffusion.mcp.server;

import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class StdioServerTest {

    @Test
    void startAndBlock_respects_interrupt_and_exits() throws Exception {
        final Method startAndBlock = findZeroArgStartAndBlock();
        final boolean isStatic = Modifier.isStatic(startAndBlock.getModifiers());
        final Object target = isStatic ? null : newInstanceAllowingPrivate(StdioServer.class);

        final CountDownLatch finished = new CountDownLatch(1);
        final Throwable[] thrown = new Throwable[1];

        Thread t = new Thread(() -> {
            try {
                startAndBlock.invoke(target);
            } catch (Throwable th) {
                // unwrap to the real cause if present
                thrown[0] = (th.getCause() != null) ? th.getCause() : th;
            } finally {
                finished.countDown();
            }
        }, "stdio-startAndBlock-test");
        t.setDaemon(true);
        t.start();

        // Let it reach the await(), then interrupt so it unwinds
        Thread.sleep(200);
        t.interrupt();

        boolean done = finished.await(3, TimeUnit.SECONDS);
        if (!done) {
            fail("startAndBlock() did not terminate within " + Duration.ofSeconds(3) + " after thread interrupt");
        }

        // Treat InterruptedException as a *pass*; anything else is a failure
        if (thrown[0] != null && !(thrown[0] instanceof InterruptedException)) {
            fail("startAndBlock() threw: " + thrown[0]);
        }
    }

    // ---- helpers ----

    private static Method findZeroArgStartAndBlock() {
        try {
            Method m;
            try {
                m = StdioServer.class.getDeclaredMethod("startAndBlock");
            } catch (NoSuchMethodException e) {
                m = StdioServer.class.getMethod("startAndBlock");
            }
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            fail("Could not find zero-arg startAndBlock() on StdioServer");
            throw new IllegalStateException("unreachable");
        }
    }

    private static Object newInstanceAllowingPrivate(Class<?> cls) throws Exception {
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            if (c.getParameterCount() == 0) {
                c.setAccessible(true);
                return c.newInstance();
            }
        }
        throw new IllegalStateException("No no-arg constructor for " + cls.getName());
    }
}
