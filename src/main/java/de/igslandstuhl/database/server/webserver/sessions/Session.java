package de.igslandstuhl.database.server.webserver.sessions;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import de.igslandstuhl.database.server.webserver.Cookie;
import de.igslandstuhl.database.server.webserver.requests.HttpRequest;

public class Session {
    private final UUID uuid = UUID.randomUUID();
    
    private final String ipAddress;
    private final String userAgent;
    private final Locale acceptLanguageLocale;
    private final Instant loginTime;
    private final boolean secureConnection;
    private final String deviceType; // e.g., "Desktop", "Mobile", "Tablet"

    public Session(String ipAddress, String userAgent, String acceptLanguage, boolean secureConnection) {
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.acceptLanguageLocale = acceptLanguage != null ? Locale.forLanguageTag(acceptLanguage) : null;
        this.loginTime = Instant.now();
        this.secureConnection = secureConnection;
        this.deviceType = detectDeviceType(userAgent);
    }
    public Session(HttpRequest request) {
        this(request.getIP(), request.getUserAgent(), request.getUserAgent(), request.isSecureConnection());
    }

    private String detectDeviceType(String userAgent) {
        if (userAgent == null) return "Unknown";
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile")) return "Mobile";
        if (ua.contains("tablet") || ua.contains("ipad")) return "Tablet";
        return "Desktop";
    }

    public UUID getUUID() {
        return uuid;
    }
    public String getIpAddress() {
        return ipAddress;
    }
    public String getUserAgent() {
        return userAgent;
    }
    public Locale getLocale() {
        return acceptLanguageLocale;
    }
    public Instant getLoginTime() {
        return loginTime;
    }
    public boolean isSecureConnection() {
        return secureConnection;
    }
    public String getDeviceType() {
        return deviceType;
    }

    public Cookie createSessionCookie() {
        return new Cookie("session", uuid.toString());
    }

    @Override
    public String toString() {
        return "Session{" +
                "uuid='" + uuid + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", userAgent='" + userAgent + '\'' +
                ", acceptLanguage='" + acceptLanguageLocale + '\'' +
                ", loginTime=" + loginTime +
                ", secureConnection=" + secureConnection +
                ", deviceType='" + deviceType + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Session)) return false;
        Session session = (Session) o;
        return Objects.equals(uuid, session.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}
