package dev.oluso;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class UserContext {
    private final String id;
    private final String email;
    private final String username;
    private final Map<String, Object> extra;

    private UserContext(Builder builder) {
        this.id = builder.id;
        this.email = builder.email;
        this.username = builder.username;
        this.extra = builder.extra;
    }

    public static UserContext of(String id) {
        return builder().id(id).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    @JsonAnyGetter
    public Map<String, Object> getExtra() {
        return extra;
    }

    public static final class Builder {
        private String id;
        private String email;
        private String username;
        private final Map<String, Object> extra = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder extra(String key, Object value) {
            this.extra.put(key, value);
            return this;
        }

        public UserContext build() {
            return new UserContext(this);
        }
    }
}
