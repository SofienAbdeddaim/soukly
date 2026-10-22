package com.soukly.user.password;

record ForgotPasswordRequest(String email) {

    @Override
    public String toString() {
        return "ForgotPasswordRequest[]";
    }
}
