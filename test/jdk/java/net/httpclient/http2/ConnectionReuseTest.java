/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.net.URISyntaxException;
import javax.net.ssl.SSLContext;

import jdk.test.lib.net.IPSupport;
import jdk.testlibrary.SimpleSSLContext;
import static java.net.http.HttpClient.Builder.NO_PROXY;
import static java.net.http.HttpClient.Version.HTTP_2;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/*
 * @test
 * @bug 8305906
 * @summary verify that the HttpClient pools and reuses a connection for HTTP/2 requests
 * @library /test/lib /lib/testlibrary server/ ../
 * @build jdk.testlibrary.SimpleSSLContext HttpServerAdapters
 *        ReferenceTracker jdk.test.lib.net.IPSupport
 * @modules java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 *          java.logging
 *          java.base/sun.net.www.http
 *          java.base/sun.net.www
 *          java.base/sun.net
 *
 * @run junit/othervm ConnectionReuseTest
 * @run junit/othervm -Djava.net.preferIPv6Addresses=true ConnectionReuseTest
 */

@RunWith(value = Parameterized.class)
public class ConnectionReuseTest implements HttpServerAdapters {

    private static SSLContext sslContext;
    private static HttpTestServer http2_Server; // h2 server over HTTP
    private static HttpTestServer https2_Server; // h2 server over HTTPS

    @BeforeClass
    public static void beforeAll() throws Exception {
        if (IPSupport.preferIPv6Addresses()) {
            IPSupport.printPlatformSupport(System.err); // for debug purposes
            // this test is run with -Djava.net.preferIPv6Addresses=true, so skip (all) tests
            // if IPv6 isn't supported on this host
            Assume.assumeTrue("Skipping tests - IPv6 is not supported", IPSupport.hasIPv6());
        }
        sslContext = new SimpleSSLContext().get();
        assertNotNull(sslContext.toString(), "Unexpected null sslContext");

        http2_Server = HttpTestServer.of(
                    new Http2TestServer("localhost", false, 0));
        http2_Server.addHandler(new Handler(), "/");
        http2_Server.start();
        System.out.println("Started HTTP v2 server at " + http2_Server.serverAuthority());

        https2_Server = HttpTestServer.of(
                    new Http2TestServer("localhost", true, sslContext));
        https2_Server.addHandler(new Handler(), "/");
        https2_Server.start();
        System.out.println("Started HTTPS v2 server at " + https2_Server.serverAuthority());
    }

    @AfterClass
    public static void afterAll() {
        if (https2_Server != null) {
            System.out.println("Stopping server " + https2_Server);
            https2_Server.stop();
        }
        if (http2_Server != null) {
            System.out.println("Stopping server " + http2_Server);
            http2_Server.stop();
        }
    }

    @Parameters
    public static Iterable<Object[]> requestURIs() throws Exception  {
        final List<Object[]> arguments = new ArrayList<>();
        if (IPSupport.preferIPv6Addresses()) {
            IPSupport.printPlatformSupport(System.err);
            Assume.assumeTrue("Skipping tests - IPv6 is not supported", IPSupport.hasIPv6());
        }
        // h2 over HTTPS
        sslContext = new SimpleSSLContext().get();
        https2_Server = HttpTestServer.of(new Http2TestServer("localhost", true, sslContext));
        https2_Server.addHandler(new Handler(), "/");
        https2_Server.start();
        arguments.add(new Object[]{new URI("https://" + https2_Server.serverAuthority() + "/")});
        // h2 over HTTP
        http2_Server = HttpTestServer.of(new Http2TestServer("localhost", false, 0));
        http2_Server.addHandler(new Handler(), "/");
        http2_Server.start();
        arguments.add(new Object[]{new URI("http://" + http2_Server.serverAuthority() + "/")});
        if (IPSupport.preferIPv6Addresses()) {
            if (http2_Server.getAddress().getAddress().isLoopbackAddress()) {
                // h2 over HTTP, use the short form of the host, in the request URI
                arguments.add(new Object[]{new URI("http://[::1]:" + http2_Server.getAddress().getPort() + "/")});
             }
        }
        return arguments;
    }

    private final URI requestURI;

    public ConnectionReuseTest(final URI requestURI) {
        this.requestURI = requestURI;
    }

    /**
     * Uses a single instance of a HttpClient and issues multiple requests to {@code requestURI}
     * and expects that each of the request internally uses the same connection
     */

    @Test
    public void testConnReuse() throws Throwable {
        final HttpClient.Builder builder = HttpClient.newBuilder()
                .proxy(NO_PROXY).sslContext(sslContext);
        final HttpRequest req = HttpRequest.newBuilder().uri(requestURI)
                .GET().version(HTTP_2).build();
        final ReferenceTracker tracker = ReferenceTracker.INSTANCE;
        Throwable testFailure = null;
        HttpClient client = tracker.track(builder.build());
        try {
            String clientConnAddr = null;
            for (int i = 1; i <= 5; i++) {
                System.out.println("Issuing request(" + i + ") " + req);
                final HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
                assertEquals("unexpected response code", 200, resp.statusCode());
                final String respBody = resp.body();
                System.out.println("Server side handler responded to a request from " + respBody);
                if (respBody == Handler.UNKNOWN_CLIENT_ADDR){
                    System.out.println("server handler couldn't determine client address in request");
                }
                if (i == 1) {
                    // for the first request we just keep track of the client connection address
                    // that got used for this request
                    clientConnAddr = respBody;
                } else {
                    // verify that the client connection used to issue the request is the same
                    // as the previous request's client connection
                    assertEquals("HttpClient unexpectedly used a" + " different connection for request(" + i + ")", clientConnAddr, respBody);
                }
            }
        } catch (Throwable t) {
            testFailure = t;
        } finally {
            // dereference the client to allow the tracker to verify the resources
            // have been released
            client = null;
            // wait for the client to be shutdown
            final AssertionError trackerFailure = tracker.check(2000);
            if (testFailure != null) {
                if (trackerFailure != null) {
                    // add the failure reported by the tracker as a suppressed
                    // exception and throw the original test failure
                    testFailure.addSuppressed(trackerFailure);
                }
                throw testFailure;
            }
            if (trackerFailure != null) {
                // the test itself didn't fail but the tracker check failed.
                // fail the test with this exception
                throw trackerFailure;
            }
        }
    }

    private static final class Handler implements HttpTestHandler {

        private static final String UNKNOWN_CLIENT_ADDR = "unknown";

        @Override
        public void handle(final HttpTestExchange t) throws IOException {
            final InetSocketAddress clientAddr = t.getRemoteAddress();
            System.out.println("Handling request " + t.getRequestURI() + " from " + clientAddr);
            // we write out the client address into the response body
            final byte[] responseBody = clientAddr == null
                    ? UNKNOWN_CLIENT_ADDR.getBytes(StandardCharsets.UTF_8)
                    : clientAddr.toString().getBytes(StandardCharsets.UTF_8);
            t.sendResponseHeaders(200, responseBody.length);
            try (final OutputStream os = t.getResponseBody()) {
                os.write(responseBody);
            }
        }
    }
}
