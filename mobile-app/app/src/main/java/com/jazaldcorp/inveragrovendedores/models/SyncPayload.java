package com.jazaldcorp.inveragrovendedores.models;

import java.util.List;

public class SyncPayload {
    public List<Asistencia> asistencias;
    public List<Visita> visitas;

    public SyncPayload(List<Asistencia> asistencias, List<Visita> visitas) {
        this.asistencias = asistencias;
        this.visitas = visitas;
    }
}