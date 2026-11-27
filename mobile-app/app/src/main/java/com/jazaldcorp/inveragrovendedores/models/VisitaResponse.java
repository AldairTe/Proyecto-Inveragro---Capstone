package com.jazaldcorp.inveragrovendedores.models;

import com.google.gson.annotations.SerializedName;

public class VisitaResponse {

    @SerializedName("id")
    private int id; // El ID real que le asignó Odoo a la visita (ej: 2)

    @SerializedName("external_uuid")
    private String externalUuid; // El UUID con el que se mandó

    @SerializedName("duplicated")
    private boolean duplicated; // True si Odoo detectó que ya existía (idempotencia)

    @SerializedName("error")
    private String error; // Por si Odoo devuelve algún mensaje de error

    // Constructor vacío
    public VisitaResponse() {}

    // Getters
    public int getId() {
        return id;
    }

    public String getExternalUuid() {
        return externalUuid;
    }

    public boolean isDuplicated() {
        return duplicated;
    }

    public String getError() {
        return error;
    }
}