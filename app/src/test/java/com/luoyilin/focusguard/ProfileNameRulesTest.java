package com.luoyilin.focusguard;

import com.google.gson.Gson;
import com.luoyilin.focusguard.network.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProfileNameRulesTest {
    @Test public void rejectsEmptyWhitespaceAndControlCharacters() {
        for (String invalid : new String[]{null, "", "   ", "\u00a0\u3000", "a\nb", "a\tb"})
            assertFalse(ProfileNameRules.valid(invalid));
    }
    @Test public void countsUnicodeCharactersInsteadOfUtf16Units() {
        assertTrue(ProfileNameRules.valid("小奶娃"));
        assertTrue(ProfileNameRules.valid("😀".repeat(24)));
        assertFalse(ProfileNameRules.valid("😀".repeat(25)));
        assertTrue(ProfileNameRules.valid(" 小奶娃 "));
    }
    @Test public void registrationOnlySendsEmailAndPassword() {
        String data = new Gson().toJson(new RegisterRequest("test@example.invalid", "test-password"));
        assertFalse(data.contains("username"));
        assertFalse(data.contains("avatar"));
        assertTrue(data.contains("test@example.invalid"));
    }
    @Test public void loginReadsNewProfileAndLegacyUsername() {
        Gson gson = new Gson();
        LoginResponse legacy = gson.fromJson("{\"username\":\"old-user\",\"userId\":42}", LoginResponse.class);
        assertEquals("old-user", legacy.getDisplayName());
        LoginResponse current = gson.fromJson("{\"username\":\"old-user\",\"displayName\":\"小奶娃\",\"avatarKey\":\"sad\",\"userId\":42}", LoginResponse.class);
        assertEquals("小奶娃", current.getDisplayName()); assertEquals("sad", current.getAvatarKey());
        assertEquals(legacy.getUserId(), current.getUserId());
    }
}
