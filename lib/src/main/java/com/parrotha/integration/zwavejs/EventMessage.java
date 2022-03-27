package com.parrotha.integration.zwavejs;

import java.util.Map;

public class EventMessage {
    public EventMessage(Map eventMap) {
        this.eventMap = eventMap;
    }

    private Map eventMap;

    public String getSource() {
        return (String) eventMap.get("source");
    }

    public String getEvent() {
        return (String) eventMap.get("event");
    }

    @Override
    public String toString() {
        return "EventMessage{" +
                "eventMap=" + eventMap +
                '}';
    }
}
