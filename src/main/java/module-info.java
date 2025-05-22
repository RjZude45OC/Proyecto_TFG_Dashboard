module com.tfg.dashboard_tfg {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    //icon pack
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;

    //graph pack
    requires eu.hansolo.tilesfx;
    requires java.prefs;
    requires org.json;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires jsch;

    opens com.tfg.dashboard_tfg to javafx.fxml;
    exports com.tfg.dashboard_tfg;
    exports com.tfg.dashboard_tfg.viewmodel;
    opens com.tfg.dashboard_tfg.viewmodel to javafx.fxml;
    exports com.tfg.dashboard_tfg.services to com.fasterxml.jackson.databind;

    opens com.tfg.dashboard_tfg.model to javafx.base;
}