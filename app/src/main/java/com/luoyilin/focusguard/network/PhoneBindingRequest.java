package com.luoyilin.focusguard.network;
public final class PhoneBindingRequest {
    public final String phone,password,proofChallengeId,proofCode;
    public PhoneBindingRequest(String phone,String password,String id,String code) { this.phone=phone;this.password=password;proofChallengeId=id;proofCode=code; }
}
