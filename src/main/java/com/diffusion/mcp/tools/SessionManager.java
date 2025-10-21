/*
 * Copyright (c) 2025 DiffusionData Ltd. All rights reserved.
 */
package com.diffusion.mcp.tools;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pushtechnology.diffusion.client.Diffusion;
import com.pushtechnology.diffusion.client.session.Session;
import com.pushtechnology.diffusion.client.session.Session.SessionError;
import com.pushtechnology.diffusion.client.session.Session.State;
import com.pushtechnology.diffusion.client.session.SessionFactory;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

/**
 * Manages Diffusion sessions for MCP clients.
 *
 * @author DiffusionData Limited
 */
@ThreadSafe
public final class SessionManager {

    private static final Logger LOG =
        LoggerFactory.getLogger(SessionManager.class);

    private static final long IDLE_TIMEOUT = MINUTES.toMillis(5);

    private final SessionFactory theSessionFactory;
    private final ScheduledExecutorService theScheduler;

    @GuardedBy("this")
    private final Map<String, Session> theSessions = new HashMap<>();

    @GuardedBy("this")
    private final Map<String, Long> theLastActivity = new HashMap<>();

    public SessionManager() {
        this(Diffusion.sessions(),
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "session-monitor");
                t.setDaemon(true);
                return t;
            }));
    }

    /**
     * Constructor for unit tests.
     */
    SessionManager(
        SessionFactory sessionFactory,
        ScheduledExecutorService scheduler) {
        theSessionFactory = sessionFactory;
        theScheduler = scheduler;

        theScheduler.scheduleAtFixedRate(
            this::checkSessions,
            30,      // initial delay
            30,      // period
            SECONDS
        );

        LOG.info("Session activity monitor started (idle timeout: {} minutes)",
            IDLE_TIMEOUT / (1000 * 60));
    }

    public synchronized Session get(String mcpSessionId) {
        theLastActivity.put(mcpSessionId, System.currentTimeMillis());
        return theSessions.get(mcpSessionId);
    }

    public synchronized Session connect(
        String mcpSessionId,
        String principal,
        String password,
        String url,
        Map<String, String> sessionProperties) {

        LOG.info(
            "Connecting Diffusion for {} with principal {} at URL {}",
            mcpSessionId,
            principal,
            url);

        final Session oldSession = theSessions.remove(mcpSessionId);

        if (oldSession != null) {
            LOG.info(
                "Closing existing session {} for {}",
                oldSession,
                mcpSessionId);
            oldSession.close();
        }

        SessionFactory sessionFactory =
            theSessionFactory
            .principal(principal)
            .password(password)
            .listener(new SessionListener(mcpSessionId))
            .errorHandler(new SessionErrorHandler(mcpSessionId));

        // Add session properties if provided
        if (sessionProperties != null && !sessionProperties.isEmpty()) {
            sessionFactory = sessionFactory.properties(sessionProperties);
            LOG.info(
                "Connecting with session properties: {}",
                sessionProperties);
        }

        final Session session =  sessionFactory.open(url);

        theSessions.put(mcpSessionId, session);

        theLastActivity.put(mcpSessionId, System.currentTimeMillis());

        LOG.info(
            "Connected Diffusion session {} for {}",
            session,
            mcpSessionId);

        return session;
    }

    public synchronized Session disconnect(String mcpSessionId) {

        final Session session = theSessions.remove(mcpSessionId);
        theLastActivity.remove(mcpSessionId);

        if (session != null) {

            LOG.info(
                "Closing Diffusion session {} for MCP session {}",
                session,
                mcpSessionId);

            session.close();
        }

        return session;
    }

    private void checkSessions() {
        try {
            synchronized (this) {
                final long now = System.currentTimeMillis();

                // Create a snapshot to avoid concurrent modification
                final Map<String, Session> sessionsCopy =
                    new HashMap<>(theSessions);
                final List<String> toRemove = new ArrayList<>();

                sessionsCopy.forEach((mcpSessionId, session) -> {
                    // Check if Diffusion session is closed
                    if (session.getState().isClosed()) {
                        LOG.info(
                            "Removing closed Diffusion session for MCP: {}",
                            mcpSessionId);
                        toRemove.add(mcpSessionId);
                    }
                    // Check for idle timeout
                    else {
                        final Long lastActive =
                            theLastActivity.get(mcpSessionId);
                        if (lastActive != null &&
                            (now - lastActive) > IDLE_TIMEOUT) {
                            LOG.info(
                                "Closing idle Diffusion session for MCP: {} (idle for {}ms)",
                                mcpSessionId,
                                now - lastActive);
                            session.close();
                            toRemove.add(mcpSessionId);
                        }
                    }
                });

                // Now remove all identified sessions from the actual maps
                toRemove.forEach(mcpSessionId -> {
                    theSessions.remove(mcpSessionId);
                    theLastActivity.remove(mcpSessionId);
                });
            }
        }
        catch (Exception e) {
            LOG.error("Error in session activity monitor", e);
        }
    }

    public synchronized void shutdown() {
        LOG.info("Shutting down session manager");
        theScheduler.shutdown();
        try {
            if (!theScheduler.awaitTermination(5, SECONDS)) {
                theScheduler.shutdownNow();
            }
        }
        catch (InterruptedException e) {
            theScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        theLastActivity.clear();

        // Create a copy of entries to avoid concurrent modification
        final Map<String, Session> sessionsToClose = new HashMap<>(theSessions);
        theSessions.clear(); // Clear immediately so listener won't try to remove

        sessionsToClose.forEach((mcpSessionId, session) -> {
            LOG.info(
                "Closing Diffusion session {} for MCP session {}",
                session,
                mcpSessionId);
            session.close();
        });
    }

    private class SessionListener implements Session.Listener {

        private final String theMcpSession;

        private SessionListener(String mcpSession) {
            theMcpSession = mcpSession;
        }

        @Override
        public void onSessionStateChanged(
            Session session,
            State oldState,
            State newState) {
            if (newState == State.CONNECTED_ACTIVE) {
                LOG.info(
                    "Connected Diffusion session {} for {}",
                    session,
                    theMcpSession);
            }
            else if (newState.isClosed()) {
                LOG.info(
                    "Diffusion session {} for {} has closed : {}",
                    session,
                    theMcpSession,
                    newState);
                synchronized (SessionManager.this) {  // Match other methods
                    theSessions.remove(theMcpSession);
                    theLastActivity.remove(theMcpSession);
                }
            }
            else {
                LOG.info(
                    "Diffusion session {} for {} state : {}",
                    session,
                    theMcpSession,
                    newState);
            }
        }
    }

    private class SessionErrorHandler implements Session.ErrorHandler {

        private final String theMcpSession;

        private SessionErrorHandler(String mcpSession) {
            theMcpSession = mcpSession;
        }

        @Override
        public void onError(Session session, SessionError error) {
            LOG.info(
                "Diffusion session {} for {} error : {}",
                session,
                theMcpSession,
                error);
        }

    }
}
