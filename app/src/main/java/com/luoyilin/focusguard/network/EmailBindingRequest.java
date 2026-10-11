package com.luoyilin.focusguard.network;
public final class EmailBindingRequest {
    public final String email, password;
    public final String phoneChallengeId,phoneCode;
    public EmailBindingRequest(String email, String password) { this(email,password,null,null); }
    public EmailBindingRequest(String email,String password,String id,String code) {this.email=email;this.password=password;phoneChallengeId=id;phoneCode=code;}
}
