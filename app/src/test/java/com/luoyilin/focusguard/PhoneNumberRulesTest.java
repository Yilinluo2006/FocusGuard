package com.luoyilin.focusguard;
import org.junit.Test;
import com.google.gson.Gson;
import com.luoyilin.focusguard.network.LoginResponse;
import static org.junit.Assert.*;
public class PhoneNumberRulesTest {
    @Test public void rejectInternationalAndMalformedNumbers(){assertFalse(PhoneNumberRules.valid(null));assertFalse(PhoneNumberRules.valid("+8613800000000"));assertFalse(PhoneNumberRules.valid("12800000000"));assertFalse(PhoneNumberRules.valid("1380000000"));assertTrue(PhoneNumberRules.valid("13800000000"));}
    @Test public void maskPhoneWithoutLeakingMiddleDigits(){assertEquals("138****0000",PhoneNumberRules.mask("13800000000"));assertEquals("未绑定",PhoneNumberRules.mask(null));}
    @Test public void legacyEmailAndPhoneOnlySessionsHaveCorrectPasswordCapability(){Gson gson=new Gson();assertTrue(gson.fromJson("{\"email\":\"test@example.invalid\"}",LoginResponse.class).hasPassword());LoginResponse phone=gson.fromJson("{\"phone\":\"13800000000\",\"hasPassword\":false}",LoginResponse.class);assertFalse(phone.hasPassword());assertEquals("13800000000",phone.getPhone());}
}
