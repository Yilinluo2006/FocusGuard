package com.luoyilin.focusguard;
public final class PhoneNumberRules {
    private PhoneNumberRules() { }
    public static boolean valid(String phone) { return phone!=null && phone.matches("1[3-9][0-9]{9}"); }
    public static String mask(String phone) { return valid(phone)?phone.substring(0,3)+"****"+phone.substring(7):"未绑定"; }
}
