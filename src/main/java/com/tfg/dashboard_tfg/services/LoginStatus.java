package com.tfg.dashboard_tfg.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpRequest;

import static com.tfg.dashboard_tfg.viewmodel.LoginViewModel.API_BASE_URL;

public class LoginStatus {
    private String username;
    private String password;

    // Default constructor required for JSON serialization
    public LoginStatus() {
    }

    public LoginStatus(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
