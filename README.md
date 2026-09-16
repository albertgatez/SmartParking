# SmartParking

## Descripción

SmartParking es un sistema inteligente para la gestión de estacionamientos en centros comerciales. El proyecto busca facilitar la localización y reserva de espacios disponibles mediante sensores de ocupación, una aplicación móvil y servicios digitales para la gestión de reservas y pagos.

El sistema contempla la comunicación entre sensores, microcontroladores y un servidor central, permitiendo consultar el estado de los espacios y gestionar las reservas de manera centralizada.

## Objetivo general

Desarrollar un sistema inteligente que permita gestionar en tiempo real la disponibilidad de espacios de estacionamiento, facilitando su consulta, reserva y pago mediante una aplicación móvil, con una arquitectura escalable y adaptable a diferentes estacionamientos.

## Mentor

**Luis Ernesto Ayala Hernández**

## Project Leader

**Alberto de Jesús Carlos Muro**

## Integrantes del equipo

| Integrante | Rol |
|------------|-----|
| Alberto | Project Leader y encargado de bugs |
| Santiago | Desarrollador y encargado de hardware |
| Fernando | Desarrollador y encargado de bases de datos |

## Estado actual

**Etapa: Diseño – Design Baseline v1.0**

Actualmente el proyecto se encuentra en la etapa de **Diseño**. Se han definido los requerimientos, arquitectura del sistema, componentes principales, estructura de la base de datos, funcionamiento esperado, prototipos y planificación del proyecto.

La implementación oficial del código corresponde a la siguiente etapa, **Construcción**, por lo que el desarrollo de firmware, backend y aplicación móvil se considera pendiente dentro del flujo oficial del proyecto.

Los avances de código realizados previamente para pruebas internas no se consideran parte de la entrega oficial de esta etapa.

## Versión / Sprint actual

**Design Baseline v1.0**

Esta versión corresponde a la línea base del diseño y establece los elementos que serán utilizados como referencia durante la etapa de Construcción.

## Estructura general del repositorio

```text
/
├── README.md
│
├── design-baseline/
│   ├── README.md
│   ├── requisitos/
│   ├── arquitectura/
│   ├── componentes/
│   ├── prototipos/
│   ├── simulacion/
│   └── documentacion/
│
├── construction/
│   ├── firmware/
│   ├── backend/
│   ├── mobile-app/
│   └── database/
│
├── testing/
│
└── documentation/