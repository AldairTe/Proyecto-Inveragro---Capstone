package com.jazaldcorp.inveragrovendedores.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "visita_multimedia")
public class VisitaMultimedia {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @NonNull
    public String visitaUuid; // Relación con el external_uuid de la visita

    @NonNull
    public String fileUri;    // La Uri de la imagen convertida a String

    public double latitude;   // Coordenada capturada al momento de registrar la visita
    public double longitude;  // (se aplica la misma ubicación de la visita a todos sus adjuntos)

    public VisitaMultimedia() {}

    public VisitaMultimedia(@NonNull String visitaUuid, @NonNull String fileUri) {
        this.visitaUuid = visitaUuid;
        this.fileUri = fileUri;
    }

    public VisitaMultimedia(@NonNull String visitaUuid, @NonNull String fileUri, double latitude, double longitude) {
        this.visitaUuid = visitaUuid;
        this.fileUri = fileUri;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}