package com.parrotha.integration.zwavejs;

import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;
import org.apache.groovy.util.Maps;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ZWaveJSWSClient extends WebSocketClient {
    private JsonSlurper jsonSlurper;
    private ZWaveJSIntegration zWaveJSIntegration;

    public ZWaveJSWSClient(URI serverUri) {
        super(serverUri);
        jsonSlurper = new JsonSlurper();
    }

    private final List<EventListener> eventListeners = new ArrayList<EventListener>();

    public void addEventListener(EventListener listener) {
        synchronized (eventListeners) {
            if (eventListeners.contains(listener)) {
                return;
            }
            eventListeners.add(listener);
        }
    }

    public void removeEventListener(EventListener listener) {
        synchronized (eventListeners) {
            eventListeners.remove(listener);
        }
    }

    private final List<ResultListener> resultListeners = new ArrayList<ResultListener>();

    public void addResultListener(ResultListener listener) {
        synchronized (resultListeners) {
            if (resultListeners.contains(listener)) {
                return;
            }

            resultListeners.add(listener);
        }
    }

    public void removeResultListener(ResultListener listener) {
        synchronized (resultListeners) {
            resultListeners.remove(listener);
        }
    }

    public interface EventListener {
        void onEvent(EventMessage eventtMessage);
    }

    private class ResultListener {
        private final CompletableFuture<ResultMessage> completableFuture;
        private final String messageId;

        public ResultListener(CompletableFuture<ResultMessage> completableFuture, String messageId) {
            this.completableFuture = completableFuture;
            this.messageId = messageId;
        }

        public void onMessage(ResultMessage resultMessage) {
            if (resultMessage != null && resultMessage.getMessageId() != null && resultMessage.getMessageId().equals(messageId)) {
                completableFuture.complete(resultMessage);
            }
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("zwave js onOpen");
    }

    @Override
    public void onMessage(String message) {
        System.out.println("zwave js onMessage: " + message);

        // TODO: do this in a thread
        Object parsedText = jsonSlurper.parseText(message);
        if (parsedText instanceof Map) {
            String type = (String) ((Map) parsedText).get("type");
            if ("version".equals(type)) {
                // we got a version message, sent a start listening message and disable statistics
                send(new JsonBuilder(Maps.of("messageId", UUID.randomUUID().toString(), "command", "driver.disable_statistics")).toString());
                send(new JsonBuilder(Maps.of("messageId", UUID.randomUUID().toString(), "command", "start_listening")).toString());
            } else if ("result".equals(type)) {
                final ResultMessage resultMessage = new ResultMessage((boolean) ((Map) parsedText).get("success"),
                        (String) ((Map) parsedText).get("messageId"), (Map) ((Map) parsedText).get("result"));
                notifyResultListeners(resultMessage);
            } else if ("event".equals(type)) {
                final EventMessage eventMessage = new EventMessage((Map) ((Map) parsedText).get("event"));
                notifyEventListeners(eventMessage);
            }
        }
    }

    private void notifyEventListeners(EventMessage eventMessage) {
        synchronized (eventListeners) {
            for (EventListener listener : eventListeners) {
                listener.onEvent(eventMessage);
            }
        }
    }

    private void notifyResultListeners(ResultMessage resultMessage) {
        synchronized (resultListeners) {
            for (ResultListener listener : resultListeners) {
                listener.onMessage(resultMessage);
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("zwave js onClose code: " + code + " " + " reason " + reason);
    }

    @Override
    public void onError(Exception ex) {
        System.out.println("zwave js onError");
        ex.printStackTrace();
    }

    public boolean startInclusion() {
        return sendControllerCommand("begin_inclusion", Maps.of("strategy", "Security_S0"));
    }

    public boolean stopInclusion() {
        return sendControllerCommand("stop_inclusion", null);
    }

    public boolean startExclusion() {
        return sendControllerCommand("begin_exclusion", null);
    }

    public boolean stopExclusion() {
        return sendControllerCommand("stop_exclusion", null);
    }

    private boolean sendControllerCommand(String command, Map options) {
        try {
            ResultMessage resultMessage = sendMessageSync("controller." + command, options);
            return resultMessage.isSuccess() && (boolean) resultMessage.getResult().get("success");
        } catch (ExecutionException | InterruptedException e) {
            return false;
        }
    }

    public void setValue(int node, int commandClass, String property, int value) {
        Map msgMap = Maps.of("messageId", UUID.randomUUID().toString(),
                "command", "node.set_value",
                "nodeId", node,
                "valueId", Maps.of("commandClass", commandClass, "property", property),
                "value", value
        );
        send(new JsonBuilder(msgMap).toString());
    }

    private ResultMessage sendMessageSync(String command, Map options) throws ExecutionException, InterruptedException {
        String messageId = UUID.randomUUID().toString();
        CompletableFuture<ResultMessage> completableFuture = new CompletableFuture<>();
        ResultListener resultListener = new ResultListener(completableFuture, messageId);
        addResultListener(resultListener);

        Map msgMap;
        if (options != null) {
            msgMap = Maps.of("messageId", messageId, "command", command, "options", options);
        } else {
            msgMap = Maps.of("messageId", messageId, "command", command);
        }
        send(new JsonBuilder(msgMap).toString());

        ResultMessage resultMessage = completableFuture.get();

        removeResultListener(resultListener);

        return resultMessage;
    }


}
