# Zian GTS — CurseForge release copy

## Release name

Zian GTS 1.0.0 Beta 1

## Release type

Beta

## Game version

Minecraft 1.21.1

## Mod loader

NeoForge

## Short description

Mercado GTS seguro y persistente para Cobblemon con interfaz nativa, AVECOINS, historial y recuperación de transacciones.

## Description

Zian GTS añade un mercado GTS para servidores de Cobblemon. Los jugadores pueden publicar Pokémon desde su Party, explorar ofertas, consultar sus detalles y comprar o retirar publicaciones desde una interfaz integrada.

### Funciones

- Mercado gráfico con modelos de Pokémon.
- Publicación desde el Party.
- Compra y retirada de ofertas.
- Ganancias pendientes y claim.
- Historial de transacciones.
- Filtros para Shiny, Alpha, legendarios y ofertas propias.
- Ordenación y paginación.
- Información de naturaleza, habilidad, movimientos e IVs.
- Radar visual de IVs.
- Integración con AVECOINS 2.3.
- Persistencia del mercado, historial y ganancias.
- Journal de transacciones y recuperación administrativa.
- Validaciones server-authoritative.
- Protección frente a compras concurrentes y operaciones inciertas.

### Requisitos

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.228 o superior
- Kotlin for Forge 5.10 o superior
- Cobblemon 1.8 o superior
- AVECOINS 2.3 para la economía integrada

### Comando

`/gtsv2`

También puede utilizarse:

`/gtsv2 open`

### Estado de Beta

Esta es la primera beta pública. El flujo principal de mercado ha sido probado en multijugador, incluyendo publicación, compra, retirada, claim, historial, filtros, paginación, reinicios y pruebas de concurrencia.

Zian GTS conserva evidencia de operaciones sensibles mediante un journal durable. Si el resultado de una operación externa no puede determinarse con seguridad, el mercado puede bloquear nuevas operaciones para permitir una revisión administrativa en lugar de reintentar a ciegas.

## Changelog — 1.0.0 Beta 1

- Primera Beta pública de Zian GTS V2.
- Mercado gráfico completo.
- Publicación de Pokémon desde Party.
- Compra, retirada, ganancias y claim.
- Historial persistente.
- Filtros, ordenación y paginación.
- Vista detallada y radar de IVs.
- Integración con AVECOINS 2.3.
- Persistencia durable.
- Journal y recuperación administrativa.
- Protección contra doble compra y operaciones concurrentes.
- Corrección de respuestas antiguas del mercado mediante requestId.
- Pruebas multijugador, reinicios, hard-kill y escenarios anti-dupe realizadas.

## Archivo para subir

`zian-gts-1.0.0-beta.1.jar`

No subir el sources JAR como archivo principal.
