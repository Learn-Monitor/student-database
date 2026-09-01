package de.igslandstuhl.database.server.webserver.handlers;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
class LoginNextTest {
 private static String safe(String value) throws Exception { Method m=PostRequestHandler.class.getDeclaredMethod("safeLoginNext",String.class);m.setAccessible(true);return (String)m.invoke(null,value); }
 @Test void acceptsLocalPathAndDecodesFormValue() throws Exception { assertEquals("/attendance-checkin?token=x",safe("%2Fattendance-checkin%3Ftoken%3Dx")); assertEquals("/dashboard",safe("/dashboard")); }
 @Test void rejectsOpenRedirectsAndHeaderInjection() { for(String value:new String[]{"https://evil.example","//evil.example","/%2Fevil.example","/http://evil","/https://evil","/x\r\nLocation: https://evil"}) assertThrows(Exception.class,()->safe(value)); }
}
