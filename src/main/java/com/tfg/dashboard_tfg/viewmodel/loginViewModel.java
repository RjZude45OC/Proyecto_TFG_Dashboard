package com.tfg.dashboard_tfg.viewmodel;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class loginViewModel {

    // FXML Injected Login Components
    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label loginErrorLabel;


    private Controller mainController;

    public void setMainController(Controller mainController) {
        this.mainController = mainController;
    }

    // Login Handling Methods
    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        // Basic login validation (replace with actual authentication)
        if ("admin".equals(username) && "password".equals(password)) {
            loginErrorLabel.setVisible(false);
            // Redirect to dashboard after successful login
            mainController.showDashboardView();
        } else {
            loginErrorLabel.setText("Invalid username or password");
            loginErrorLabel.setVisible(true);
        }
    }

    @FXML
    private void cancelLogin() {
        mainController.showDashboardView(); // Return to dashboard
    }
}
