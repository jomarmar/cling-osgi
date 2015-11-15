/*
 * Copyright (C) 2013 4th Line GmbH, Switzerland
 *
 * The contents of this file are subject to the terms of either the GNU
 * Lesser General Public License Version 2 or later ("LGPL") or the
 * Common Development and Distribution License Version 1 or later
 * ("CDDL") (collectively, the "License"). You may not use this file
 * except in compliance with the License. See LICENSE.txt for more
 * information.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.fourthline.cling.transport.jetty9;


import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.ByteBufferContentProvider;
import org.eclipse.jetty.client.util.FutureResponseListener;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.fourthline.cling.model.message.*;
import org.fourthline.cling.model.message.header.ContentTypeHeader;
import org.fourthline.cling.transport.spi.AbstractStreamClient;
import org.fourthline.cling.transport.spi.InitializationException;
import org.fourthline.cling.transport.spi.StreamClient;
import org.seamless.util.MimeType;

import java.io.UnsupportedEncodingException;
import java.net.NoRouteToHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation based on Jetty 8 client API.
 * <p>
 * This implementation works on Android, dependencies are the <code>jetty-client</code>
 * Maven module.
 * </p>
 *
 * @author Christian Bauer
 */
public class StreamClientImpl extends AbstractStreamClient<StreamClientConfigurationImpl,FutureResponseListener> {

    final private static Logger log = Logger.getLogger(StreamClient.class.getName());

    final protected StreamClientConfigurationImpl configuration;
    final protected HttpClient client;

    public StreamClientImpl(StreamClientConfigurationImpl configuration) throws InitializationException {
        this.configuration = configuration;

        log.info("Starting Jetty HttpClient...");
        client = new HttpClient();

        // Jetty client needs threads for its internal expiration routines, which we don't need but
        // can't disable, so let's abuse the request executor service for this
//        client.setThreadPool(
//            new ExecutorThreadPool(getConfiguration().getRequestExecutorService()) {
//                @Override
//                protected void doStop() throws Exception {
//                    // Do nothing, don't shut down the Cling ExecutorService when Jetty stops!
//                }
//            }
//        );

        // These are some safety settings, we should never run into these timeouts as we
        // do our own expiration checking
//        client.setTimeout((configuration.getTimeoutSeconds()+5) * 1000);
        client.setIdleTimeout((configuration.getTimeoutSeconds()+5) * 1000);
        //client.setExecutor(Executors.newFixedThreadPool(4));
        client.setConnectTimeout((configuration.getTimeoutSeconds()+5) * 1000);
        ;
//        client.setMaxRetries(configuration.getRequestRetryCount());

        try {
            client.start();
        } catch (Exception ex) {
            throw new InitializationException(
                "Could not start Jetty HTTP client: " + ex, ex
            );
        }
    }

    @Override
    public StreamClientConfigurationImpl getConfiguration() {
        return configuration;
    }

    @Override
    protected FutureResponseListener createRequest(StreamRequestMessage requestMessage) {
        //return new HttpContentExchange(getConfiguration(), client, requestMessage);
        Request request = getRequest(requestMessage);
        if(request == null) {
            return null;
        }
        FutureResponseListener listener = new FutureResponseListener(request);

        request.send(listener);

        return listener;
    }

    @Override
    protected Callable<StreamResponseMessage> createCallable(final StreamRequestMessage requestMessage,
                                                             final FutureResponseListener listener) {
        return new Callable<StreamResponseMessage>() {
            public StreamResponseMessage call() throws Exception {

                if (log.isLoggable(Level.FINE))
                    log.fine("Sending HTTP request: " + requestMessage);

                //getRequest().send(listener);

//                System.out.println("Sending HTTP request: " + requestMessage);



                try {
                    ContentResponse response = listener.get(configuration.getTimeoutSeconds(), TimeUnit.SECONDS);

                   log.info(">>>>>>>>>>>>>>>    RESPONSE RECEIVED: "+ response.getStatus() +  "REQUEST: " + response.getRequest().getURI().toString());
                    return createResponse(response);


                } catch (ExecutionException e) {
                    //e.printStackTrace();
                    if(e.getCause() instanceof NoRouteToHostException) {
                        log.warning("No route to host: " + requestMessage.getUri().toString()+". Check firewall");
                    }
                }

                catch (TimeoutException e) {
                    log.warning("Timeout sending request: " + requestMessage.getUri().toString()+". Check network");
                }

                return null;
//                client.send(exchange);
//                int exchangeState = exchange.waitForDone();
//
//                if (exchangeState == HttpExchange.STATUS_COMPLETED) {
//                    try {
//                        return exchange.createResponse();
//                    } catch (Throwable t) {
//                        log.log(Level.WARNING, "Error reading response: " + requestMessage, Exceptions.unwrap(t));
//                        return null;
//                    }
//                } else if (exchangeState == HttpExchange.STATUS_CANCELLED) {
//                    // That's ok, happens when we abort the exchange after timeout
//                    return null;
//                } else if (exchangeState == HttpExchange.STATUS_EXCEPTED) {
//                    // The warnings of the "excepted" condition are logged in HttpContentExchange
//                    return null;
//                } else {
//                    log.warning("Unhandled HTTP exchange status: " + exchangeState);
//                    return null;
//                }
            }
        };
    }

    @Override
    protected void abort(FutureResponseListener exchange) {
        exchange.cancel(true);
    }

    @Override
    protected boolean logExecutionException(Throwable t) {
        return false;
    }

    @Override
    public void stop() {
        try {
            client.stop();
        } catch (Exception ex) {
            log.info("Error stopping HTTP client: " + ex);
        }
    }

    protected Request getRequest(StreamRequestMessage requestMessage) {
        final UpnpRequest requestOperation = requestMessage.getOperation();
        if (log.isLoggable(Level.FINE))
            log.fine(
                    "Preparing HTTP request message with method '"
                            + requestOperation.getHttpMethodName()
                            + "': " + requestMessage
            );

        Request request = applyRequestURLMethod(requestMessage);
        request = applyRequestHeaders(requestMessage, request);
        return applyRequestBody(requestMessage, request);
    }

//    @Override
//    public StreamResponseMessage sendRequest(StreamRequestMessage requestMessage) throws InterruptedException {
//
//        final UpnpRequest requestOperation = requestMessage.getOperation();
//        if (log.isLoggable(Level.FINE))
//            log.fine(
//                    "Preparing HTTP request message with method '"
//                            + requestOperation.getHttpMethodName()
//                            + "': " + requestMessage
//            );
//
//        Request request = applyRequestURLMethod(requestMessage);
//        request = applyRequestHeaders(requestMessage, request);
//        request = applyRequestBody(requestMessage, request);
//
//        FutureResponseListener listener = new FutureResponseListener(request);
//
//        request.send(listener);
//
//        try {
//            ContentResponse response = listener.get(5, TimeUnit.SECONDS);
//
//
//
//        } catch (ExecutionException e) {
//            e.printStackTrace();
//        } catch (TimeoutException e) {
//            e.printStackTrace();
//        }
//
//
//
//
//        return null;
//
//    }

    protected StreamResponseMessage createResponse(ContentResponse response) {
        // Status
        UpnpResponse responseOperation =
                new UpnpResponse(response.getStatus(),
                        UpnpResponse.Status.getByStatusCode(response.getStatus()).getStatusMsg()
                );

        if (log.isLoggable(Level.FINE))
            log.fine("Received response: " + responseOperation);

        StreamResponseMessage responseMessage = new StreamResponseMessage(responseOperation);

        // Headers
        UpnpHeaders headers = new UpnpHeaders();
        HttpFields responseFields = response.getHeaders();
        for (String name : responseFields.getFieldNamesCollection()) {
            for (String value : responseFields.getValuesList(name)) {
                headers.add(name, value);
            }
        }
        responseMessage.setHeaders(headers);

        // Body

        byte[] bytes = response.getContent();
        if (bytes != null && bytes.length > 0 && responseMessage.isContentTypeMissingOrText()) {

            if (log.isLoggable(Level.FINE))
                log.fine("Response contains textual entity body, converting then setting string on message");
            try {
                responseMessage.setBodyCharacters(bytes);
            } catch (UnsupportedEncodingException ex) {
                throw new RuntimeException("Unsupported character encoding: " + ex, ex);
            }

        } else if (bytes != null && bytes.length > 0) {

            if (log.isLoggable(Level.FINE))
                log.fine("Response contains binary entity body, setting bytes on message");
            responseMessage.setBody(UpnpMessage.BodyType.BYTES, bytes);

        } else {
            if (log.isLoggable(Level.FINE))
                log.fine("Response did not contain entity body");
        }

        if (log.isLoggable(Level.FINE))
            log.fine("Response message complete: " + responseMessage);
        return responseMessage;
    }

    protected Request applyRequestURLMethod(StreamRequestMessage requestMessage) {
        final UpnpRequest requestOperation = requestMessage.getOperation();
        if (log.isLoggable(Level.FINE))
            log.fine(
                    "Preparing HTTP request message with method '"
                            + requestOperation.getHttpMethodName()
                            + "': " + requestMessage
            );

        Request request = client.newRequest(requestOperation.getURI().toString());
        request.method(requestOperation.getHttpMethodName());
        return request;
    }

    protected Request applyRequestHeaders(StreamRequestMessage requestMessage, Request request) {
        // Headers
        UpnpHeaders headers = requestMessage.getHeaders();
        if (log.isLoggable(Level.FINE))
            log.fine("Writing headers on HttpContentExchange: " + headers.size());
        // TODO Always add the Host header
        // TODO: ? setRequestHeader(UpnpHeader.Type.HOST.getHttpName(), );
        // Add the default user agent if not already set on the message
//        if (!headers.containsKey(UpnpHeader.Type.USER_AGENT)) {
//            request.header(
//                    UpnpHeader.Type.USER_AGENT.getHttpName(),
//                    getConfiguration().getUserAgentValue(
//                            requestMessage.getUdaMajorVersion(),
//                            requestMessage.getUdaMinorVersion())
//            );
//        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String v : entry.getValue()) {
                String headerName = entry.getKey();
                if (log.isLoggable(Level.FINE))
                    log.fine("Setting header '" + headerName + "': " + v);
                request.header(headerName, v);
            }
        }

        return request;
    }

    protected Request applyRequestBody(StreamRequestMessage requestMessage, Request request) {

        // Body
        if (requestMessage.hasBody()) {
            if (requestMessage.getBodyType() == UpnpMessage.BodyType.STRING) {
                if (log.isLoggable(Level.FINE))
                    log.fine("Writing textual request body: " + requestMessage);

                MimeType contentType =
                        requestMessage.getContentTypeHeader() != null
                                ? requestMessage.getContentTypeHeader().getValue()
                                : ContentTypeHeader.DEFAULT_CONTENT_TYPE_UTF8;




                String charset =
                        requestMessage.getContentTypeCharset() != null
                                ? requestMessage.getContentTypeCharset()
                                : "UTF-8";

                if(charset.equals("utf-8")) charset = "UTF-8";
                ByteBufferContentProvider buffer;
                try {
                    buffer = new ByteBufferContentProvider(Charset.forName(charset).encode(requestMessage.getBodyString()));
                } catch (Exception ex) {
                    throw new RuntimeException("Unsupported character encoding: " + charset, ex);
                }
                request.header(HttpHeader.CONTENT_LENGTH, String.valueOf(buffer.getLength()));
                request.content(buffer, contentType.toString());

            } else {
                if (log.isLoggable(Level.FINE))
                    log.fine("Writing binary request body: " + requestMessage);

                if (requestMessage.getContentTypeHeader() == null)
                    throw new RuntimeException(
                            "Missing content type header in request message: " + requestMessage
                    );
                MimeType contentType = requestMessage.getContentTypeHeader().getValue();

                String charset =
                        requestMessage.getContentTypeCharset() != null
                                ? requestMessage.getContentTypeCharset()
                                : "UTF-8";

                if(charset.equals("utf-8")) charset = "UTF-8";

                ByteBufferContentProvider buffer;
                try {
                    buffer = new ByteBufferContentProvider(ByteBuffer.wrap(requestMessage.getBodyBytes()));
                } catch (Exception ex) {
                    throw new RuntimeException("Unsupported character encoding: " + charset, ex);
                }
                request.header(HttpHeader.CONTENT_LENGTH, String.valueOf(buffer.getLength()));
                request.content(buffer, contentType.toString());
            }
        }

        return request;
    }

    static public class MyContentExchange extends FutureResponseListener {


        public MyContentExchange(Request request) {
            super(request);
        }





    }

//    static public class HttpContentExchange extends ContentExchange {
//
//        final protected StreamClientConfigurationImpl configuration;
//        final protected HttpClient client;
//        final protected StreamRequestMessage requestMessage;
//
//        protected Throwable exception;
//
//        public HttpContentExchange(StreamClientConfigurationImpl configuration,
//                                   HttpClient client,
//                                   StreamRequestMessage requestMessage) {
//            super(true);
//            this.configuration = configuration;
//            this.client = client;
//            this.requestMessage = requestMessage;
//            applyRequestURLMethod();
//            applyRequestHeaders();
//            applyRequestBody();
//        }
//
//        @Override
//        protected void onConnectionFailed(Throwable t) {
//            log.log(Level.WARNING, "HTTP connection failed: " + requestMessage, Exceptions.unwrap(t));
//        }
//
//        @Override
//        protected void onException(Throwable t) {
//            log.log(Level.WARNING, "HTTP request failed: " + requestMessage, Exceptions.unwrap(t));
//        }
//
//        public StreamClientConfigurationImpl getConfiguration() {
//            return configuration;
//        }
//
//        public StreamRequestMessage getRequestMessage() {
//            return requestMessage;
//        }
//
//        protected void applyRequestURLMethod() {
//            final UpnpRequest requestOperation = getRequestMessage().getOperation();
//            if (log.isLoggable(Level.FINE))
//                log.fine(
//                    "Preparing HTTP request message with method '"
//                        + requestOperation.getHttpMethodName()
//                        + "': " + getRequestMessage()
//                );
//
//            setURL(requestOperation.getURI().toString());
//            setMethod(requestOperation.getHttpMethodName());
//        }
//
//        protected void applyRequestHeaders() {
//            // Headers
//            UpnpHeaders headers = getRequestMessage().getHeaders();
//            if (log.isLoggable(Level.FINE))
//                log.fine("Writing headers on HttpContentExchange: " + headers.size());
//            // TODO Always add the Host header
//            // TODO: ? setRequestHeader(UpnpHeader.Type.HOST.getHttpName(), );
//            // Add the default user agent if not already set on the message
//            if (!headers.containsKey(UpnpHeader.Type.USER_AGENT)) {
//                setRequestHeader(
//                    UpnpHeader.Type.USER_AGENT.getHttpName(),
//                    getConfiguration().getUserAgentValue(
//                        getRequestMessage().getUdaMajorVersion(),
//                        getRequestMessage().getUdaMinorVersion())
//                );
//            }
//            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
//                for (String v : entry.getValue()) {
//                    String headerName = entry.getKey();
//                    if (log.isLoggable(Level.FINE))
//                        log.fine("Setting header '" + headerName + "': " + v);
//                    addRequestHeader(headerName, v);
//                }
//            }
//        }
//
//        protected void applyRequestBody() {
//            // Body
//            if (getRequestMessage().hasBody()) {
//                if (getRequestMessage().getBodyType() == UpnpMessage.BodyType.STRING) {
//                    if (log.isLoggable(Level.FINE))
//                        log.fine("Writing textual request body: " + getRequestMessage());
//
//                    MimeType contentType =
//                        getRequestMessage().getContentTypeHeader() != null
//                            ? getRequestMessage().getContentTypeHeader().getValue()
//                            : ContentTypeHeader.DEFAULT_CONTENT_TYPE_UTF8;
//
//                    String charset =
//                        getRequestMessage().getContentTypeCharset() != null
//                            ? getRequestMessage().getContentTypeCharset()
//                            : "UTF-8";
//
//                    setRequestContentType(contentType.toString());
//                    ByteArrayBuffer buffer;
//                    try {
//                        buffer = new ByteArrayBuffer(getRequestMessage().getBodyString(), charset);
//                    } catch (UnsupportedEncodingException ex) {
//                        throw new RuntimeException("Unsupported character encoding: " + charset, ex);
//                    }
//                    setRequestHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(buffer.length()));
//                    setRequestContent(buffer);
//
//                } else {
//                    if (log.isLoggable(Level.FINE))
//                        log.fine("Writing binary request body: " + getRequestMessage());
//
//                    if (getRequestMessage().getContentTypeHeader() == null)
//                        throw new RuntimeException(
//                            "Missing content type header in request message: " + requestMessage
//                        );
//                    MimeType contentType = getRequestMessage().getContentTypeHeader().getValue();
//
//                    setRequestContentType(contentType.toString());
//                    ByteArrayBuffer buffer;
//                    buffer = new ByteArrayBuffer(getRequestMessage().getBodyBytes());
//                    setRequestHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(buffer.length()));
//                    setRequestContent(buffer);
//                }
//            }
//        }
//
//        protected StreamResponseMessage createResponse() {
//            // Status
//            UpnpResponse responseOperation =
//                new UpnpResponse(
//                    getResponseStatus(),
//                    UpnpResponse.Status.getByStatusCode(getResponseStatus()).getStatusMsg()
//                );
//
//            if (log.isLoggable(Level.FINE))
//                log.fine("Received response: " + responseOperation);
//
//            StreamResponseMessage responseMessage = new StreamResponseMessage(responseOperation);
//
//            // Headers
//            UpnpHeaders headers = new UpnpHeaders();
//            HttpFields responseFields = getResponseFields();
//            for (String name : responseFields.getFieldNamesCollection()) {
//                for (String value : responseFields.getValuesCollection(name)) {
//                    headers.add(name, value);
//                }
//            }
//            responseMessage.setHeaders(headers);
//
//            // Body
//            byte[] bytes = getResponseContentBytes();
//            if (bytes != null && bytes.length > 0 && responseMessage.isContentTypeMissingOrText()) {
//
//                if (log.isLoggable(Level.FINE))
//                    log.fine("Response contains textual entity body, converting then setting string on message");
//                try {
//                    responseMessage.setBodyCharacters(bytes);
//                } catch (UnsupportedEncodingException ex) {
//                    throw new RuntimeException("Unsupported character encoding: " + ex, ex);
//                }
//
//            } else if (bytes != null && bytes.length > 0) {
//
//                if (log.isLoggable(Level.FINE))
//                    log.fine("Response contains binary entity body, setting bytes on message");
//                responseMessage.setBody(UpnpMessage.BodyType.BYTES, bytes);
//
//            } else {
//                if (log.isLoggable(Level.FINE))
//                    log.fine("Response did not contain entity body");
//            }
//
//            if (log.isLoggable(Level.FINE))
//                log.fine("Response message complete: " + responseMessage);
//            return responseMessage;
//        }
//    }
}


