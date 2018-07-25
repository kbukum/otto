package com.squareup.otto;

public class EventKey {
    private final String name;
    private final Class<?> type;

    public EventKey(String name, Class<?> type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public Class<?> getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EventKey eventKey = (EventKey) o;

        if (name != null ? !name.equals(eventKey.name) : eventKey.name != null) return false;
        return type != null ? type.equals(eventKey.type) : eventKey.type == null;
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }
}
