package com.luoyilin.focusguard.network;
import java.util.List;
public final class ProfileResponse {
    public Long userId;
    public String displayName, avatarKey, email;
    public boolean emailVerified;
    public String phone;
    public boolean phoneVerified;
    public Boolean hasPassword;
    public List<String> linkedMethods;
}
