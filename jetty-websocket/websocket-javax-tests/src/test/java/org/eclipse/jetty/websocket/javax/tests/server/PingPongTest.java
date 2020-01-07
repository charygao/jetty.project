//
//  ========================================================================
//  Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.javax.tests.server;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.acme.websocket.PongContextListener;
import com.acme.websocket.PongMessageEndpoint;
import com.acme.websocket.PongSocket;
import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.core.CoreSession;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.OpCode;
import org.eclipse.jetty.websocket.core.client.WebSocketCoreClient;
import org.eclipse.jetty.websocket.javax.tests.Timeouts;
import org.eclipse.jetty.websocket.javax.tests.WSServer;
import org.eclipse.jetty.websocket.javax.tests.framehandlers.FrameHandlerTracker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTimeout;

public class PingPongTest
{
    private static WSServer server;
    private static WebSocketCoreClient client;

    @BeforeAll
    public static void startServer() throws Exception
    {
        Path testdir = MavenTestingUtils.getTargetTestingPath(PingPongTest.class.getName());
        server = new WSServer(testdir, "app");
        server.copyWebInf("pong-config-web.xml");

        server.copyClass(PongContextListener.class);
        server.copyClass(PongMessageEndpoint.class);
        server.copyClass(PongSocket.class);

        server.start();

        WebAppContext webapp = server.createWebAppContext();
        server.deployWebapp(webapp);
    }

    @BeforeAll
    public static void startClient() throws Exception
    {
        client = new WebSocketCoreClient();
        client.start();
    }

    @AfterAll
    public static void stopServer() throws Exception
    {
        server.stop();
    }

    private void assertEcho(String endpointPath, Consumer<CoreSession> sendAction, String... expectedMsgs) throws Exception
    {
        FrameHandlerTracker clientSocket = new FrameHandlerTracker();
        URI toUri = server.getWsUri().resolve(endpointPath);

        // Connect
        Future<CoreSession> futureSession = client.connect(clientSocket, toUri);
        CoreSession coreSession = futureSession.get(Timeouts.CONNECT_MS, TimeUnit.MILLISECONDS);
        try
        {
            // Apply send action
            sendAction.accept(coreSession);

            // Validate Responses
            for (int i = 0; i < expectedMsgs.length; i++)
            {
                String pingMsg = clientSocket.messageQueue.poll(1, TimeUnit.SECONDS);
                assertThat("Expected message[" + i + "]", pingMsg, containsString(expectedMsgs[i]));
            }
        }
        finally
        {
            coreSession.close(Callback.NOOP);
        }
    }

    @Test
    public void testPongEndpoint() throws Exception
    {
        assertTimeout(Duration.ofMillis(6000), () ->
        {
            assertEcho("/app/pong", (session) ->
            {
                session.sendFrame(new Frame(OpCode.PONG).setPayload("hello"), Callback.NOOP, false);
            }, "PongMessageEndpoint.onMessage(PongMessage):[/pong]:hello");
        });
    }

    @Test
    public void testPongSocket() throws Exception
    {
        assertTimeout(Duration.ofMillis(6000), () ->
        {
            assertEcho("/app/pong-socket", (session) ->
            {
                session.sendFrame(new Frame(OpCode.PONG).setPayload("hello"), Callback.NOOP, false);
            }, "PongSocket.onPong(PongMessage)[/pong-socket]:hello");
        });
    }
}
