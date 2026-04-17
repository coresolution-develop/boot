package com.coresolution.pe.security;

public class SHA256Test {
    public static void main(String[] args) {
        SHA256PasswordEncoder encoder = new SHA256PasswordEncoder();
        String hashed = encoder.encode("123qwe");
        System.out.println("Hashed value: " + hashed);
    }
}
