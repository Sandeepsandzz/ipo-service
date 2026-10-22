package com.ipo.tracker.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "telegram.bot")
public class TelegramBotProperties {

    private boolean enabled = true;
    private String token = "";
    private String username = "IpoTrackerBot";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean hasValidToken() {
        return token != null && !token.isBlank() && !token.equalsIgnoreCase("YOUR_TELEGRAM_BOT_TOKEN");
    }
}
