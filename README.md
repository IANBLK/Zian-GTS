# Zian GTS

**Beta 2.4:** the command is now `/ZianGTS`, including `open`, `status` and `recovery`.
The old command is not registered. Existing `ziangts.*` LuckPerms nodes and saved market data are preserved.
LuckPerms is supported as a NeoForge mod or a Bukkit plugin on Youer.

Zian GTS es un mercado GTS para Cobblemon en Minecraft 1.21.1, desarrollado con NeoForge y pensado para servidores multijugador.

## Beta 2.4

La versión actual es **1.0.0 Beta 2.4**.

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
- Integración comprobada con la cartera de AVECOINS 2.3 y 2.4.
- Persistencia durable del mercado, historial y ganancias.
- Journal de transacciones y recuperación administrativa.
- Validaciones en el servidor y reserva de ofertas para impedir una segunda compra simultánea en el mismo proceso.
- Permisos por acción con LuckPerms opcional; una denegación explícita se aplica también a operadores.

## Requisitos

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.228 o superior
- Kotlin for Forge 5.10+
- Cobblemon 1.8+
- AVECOINS 2.3 o 2.4 para la economía integrada

Zian GTS también se prueba en Youer 1.21.1.

## Uso

Los jugadores pueden abrir el mercado con:

`/ZianGTS`

o:

`/ZianGTS open`

La publicación, compra, retirada, claim, historial, filtros y navegación se realizan desde la interfaz.

## Administración y recuperación

Los operadores disponen de:

- `/ZianGTS status`
- `/ZianGTS recovery`
- `/ZianGTS recovery resolve <operation-uuid>`
- `/ZianGTS recovery resolve <operation-uuid> confirm`

Una operación incierta puede bloquear el mercado deliberadamente. Antes de resolver una incidencia deben comprobarse manualmente el estado de AVECOINS, el almacenamiento Pokémon y el estado del GTS. La resolución no entrega Pokémon ni devuelve monedas automáticamente.

## Seguridad

El servidor decide propiedad, precio, moneda, contenido del Pokémon y resultado de cada transacción. El cliente no proporciona valores autoritativos.

Las operaciones económicas y de Pokémon atraviesan sistemas externos distintos, por lo que Zian GTS conserva evidencia durable y falla de forma cerrada cuando no puede determinar con seguridad el resultado de una operación.
No hay una transacción atómica compartida entre esos sistemas: una incidencia puede requerir conciliación manual antes de reabrir el mercado. Consulte [recuperación](docs/RECOVERY.md) y [permisos](docs/PERMISSIONS.md).

## Estado de pruebas

El núcleo ha superado pruebas automatizadas de persistencia, concurrencia, journal, rollback, cierre forzado y recuperación, además de pruebas multijugador del flujo de mercado. Cada nueva versión requiere comprobar su JAR en un servidor con las dependencias reales antes de publicarlo.
