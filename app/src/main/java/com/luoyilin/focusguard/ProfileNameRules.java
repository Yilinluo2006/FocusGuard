package com.luoyilin.focusguard;

final class ProfileNameRules {
    static boolean valid(String raw) {
        if (raw == null) return false;
        String name = raw.trim(); int length = name.codePointCount(0, name.length());
        return length >= 1 && length <= 24 && !name.codePoints().anyMatch(Character::isISOControl)
                && !name.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
    }
}
