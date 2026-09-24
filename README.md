# Zian GTS

Zian GTS es un mercado GTS para Cobblemon en Minecraft 1.21.1, desarrollado con NeoForge y pensado para servidores multijugador.

## Beta 1

La versión actual es **1.0.0 Beta 1 candidate**.

Funciones principales:

- Mercado gráfico con modelos de Pokémon.
- Publicación de Pokémon directamente desde el Party.
- Compra y retirada de ofertas.
- Ganancias pendientes y claim.
- Historial de transacciones.
- Filtros por Shiny, Alpha, legendarios y ofertas propias.
- Ordenación y paginación.
- Naturaleza, habilidad, movimientos e IVs.
- Radar visual de IVs.
- Integración con AVECOINS 2.3.
- Persistencia durable del mercado, historial y ganancias.
- Journal de transacciones y recuperación administrativa.
- Validaciones server-authoritative y protección frente a operaciones concurrentes.

## Requisitos

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.228 o superior
- Kotlin for Forge 5.10+
- Cobblemon 1.8+
- AVECOINS 2.3 para la economía integrada

Zian GTS también se prueba en Youer 1.21.1.

## Uso

Los jugadores pueden abrir el mercado con:

`/gtsv2`

o:

`/gtsv2 open`

La publicación, compra, retirada, claim, historial, filtros y navegación se realizan desde la interfaz.

## Administración y recuperación

Los operadores disponen de:

- `/gtsv2 status`
- `/gtsv2 recovery`
- `/gtsv2 recovery resolve <operation-uuid>`
- `/gtsv2 recovery resolve <operation-uuid> confirm`

Una operación incierta puede bloquear el mercado deliberadamente. Antes de resolver una incidencia deben comprobarse manualmente el estado de AVECOINS, el almacenamiento Pokémon y el estado del GTS. La resolución no entrega Pokémon ni devuelve monedas automáticamente.

## Seguridad

El servidor decide propiedad, precio, moneda, contenido del Pokémon y resultado de cada transacción. El cliente no proporciona valores autoritativos.

Las operaciones económicas y de Pokémon atraviesan sistemas externos distintos, por lo que Zian GTS conserva evidencia durable y falla de forma cerrada cuando no puede determinar con seguridad el resultado de una operación.

## Estado de pruebas

La Beta 1 candidate ha superado pruebas automatizadas de persistencia, concurrencia, journal, rollback, hard-kill y recuperación, además de pruebas multijugador del flujo de mercado.

Antes de publicar Beta 1 se realizará un último smoke test del artefacto candidato en Youer.
