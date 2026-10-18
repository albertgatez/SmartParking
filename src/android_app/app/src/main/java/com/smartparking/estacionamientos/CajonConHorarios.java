package com.smartparking.estacionamientos;

import java.util.List;

/**
 * Clase auxiliar que representa un cajón con sus horarios disponibles/ocupados
 */
public class CajonConHorarios {
    private Cajon cajon;
    private List<HorarioEstado> horarios;
    private boolean accesibleParaUsuario = true; // Por defecto accesible

    public CajonConHorarios(Cajon cajon, List<HorarioEstado> horarios) {
        this.cajon = cajon;
        this.horarios = horarios;
    }

    public Cajon getCajon() {
        return cajon;
    }

    public void setCajon(Cajon cajon) {
        this.cajon = cajon;
    }

    public List<HorarioEstado> getHorarios() {
        return horarios;
    }

    public void setHorarios(List<HorarioEstado> horarios) {
        this.horarios = horarios;
    }

    public boolean isAccesibleParaUsuario() {
        return accesibleParaUsuario;
    }

    public void setAccesibleParaUsuario(boolean accesibleParaUsuario) {
        this.accesibleParaUsuario = accesibleParaUsuario;
    }

    /**
     * Clase interna que representa el estado de cada hora
     */
    public static class HorarioEstado {
        private int hora;
        private EstadoHorario estado;
        private boolean seleccionado;

        public HorarioEstado(int hora, EstadoHorario estado) {
            this.hora = hora;
            this.estado = estado;
            this.seleccionado = false;
        }

        public int getHora() {
            return hora;
        }

        public void setHora(int hora) {
            this.hora = hora;
        }

        public EstadoHorario getEstado() {
            return estado;
        }

        public void setEstado(EstadoHorario estado) {
            this.estado = estado;
        }

        public boolean isSeleccionado() {
            return seleccionado;
        }

        public void setSeleccionado(boolean seleccionado) {
            this.seleccionado = seleccionado;
        }

        public String getHorarioFormateado() {
            return String.format("%02d:00", hora);
        }
    }

    /**
     * Enum que representa los diferentes estados de un horario
     */
    public enum EstadoHorario {
        DISPONIBLE,   // Azul claro - puede ser seleccionado
        OCUPADO,      // Gris claro - reservado por otro usuario
        PASADO,       // Gris oscuro - la hora ya pasó
        SELECCIONADO, // Azul oscuro - seleccionado por el usuario actual
        RESTRINGIDO   // Gris con candado - cajón para discapacitados (usuario no tiene acceso)
    }
}