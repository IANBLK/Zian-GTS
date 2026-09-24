# Changelog

## 1.0.0 Beta 1

Primera beta pública candidata de Zian GTS V2.

### Mercado
- Interfaz GTS nativa con modelos de Pokémon.
- Publicación desde el Party.
- Compra y retirada de ofertas.
- Ganancias y claim.
- Historial de transacciones.
- Filtros, ordenación y paginación.
- Vista detallada con género, naturaleza, habilidad, movimientos, IVs, Alpha, Shiny y legendario.
- Radar visual de IVs.

### Economía
- Integración con AVECOINS 2.3.
- Validación de saldo y operaciones en el servidor.
- Ganancias persistentes para vendedores.

### Seguridad y persistencia
- Arquitectura server-authoritative.
- Protección frente a doble compra y operaciones concurrentes.
- Mercado, historial y journal persistentes.
- Journal durable para operaciones sensibles.
- Bloqueo fail-closed cuando una operación externa queda en estado incierto.
- Recuperación administrativa con reconciliación manual.
- Protección mediante requestId frente a respuestas antiguas del mercado.

### Pruebas
- Tests automatizados de persistencia, concurrencia, rollback, journal y hard-kill.
- Pruebas multijugador de publicación, compra, retirada, claim, historial, filtros y paginación.
- Pruebas de reinicio y persistencia.
- Pruebas de abuso y duplicación sin duplicaciones observadas.

### Compatibilidad
- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.228+
- Cobblemon 1.8+
- AVECOINS 2.3
- Youer 1.21.1 como plataforma objetivo de servidor híbrido
