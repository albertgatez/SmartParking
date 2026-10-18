package com.smartparking.login;

public class Usuario {
    private String id;
    private String nombre;
    private String correo;
    private String contrasena;
    private boolean discapacitado;

    public Usuario(String nombre, String correo, String contrasena, boolean discapacitado) {
        this.nombre = nombre;
        this.correo = correo;
        this.contrasena = contrasena;
        this.discapacitado = discapacitado;
    }

    // Getters
    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
    public String getContrasena() { return contrasena; }
    public boolean isDiscapacitado() { return discapacitado; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setCorreo(String correo) { this.correo = correo; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }
    public void setDiscapacitado(boolean discapacitado) { this.discapacitado = discapacitado; }
}