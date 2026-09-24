# Zian GTS

Sistema GTS para Cobblemon en Minecraft 1.21.1 / NeoForge.

## Objetivo técnico
- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.228+
- Cobblemon 1.8+
- Interfaz principal en español (España)
- Integración con AVECOINS 2.3
- Historial persistente de transacciones para moderación
- Persistencia durable y journal de recuperación
- Arquitectura V2 server-authoritative

El desarrollo activo se realiza en la rama `rewrite/independent-core-v2`.
La implementación de producción actual pertenece al árbol V2; el runtime V1
histórico fue retirado del código compilable.

## Estado de desarrollo

La V2 incluye mercado, publicación desde Party, compra, retirada, ganancias,
claim, historial, filtros, ordenación, paginación y recuperación ante
transacciones pendientes. Las mutaciones económicas y de Pokémon se protegen
mediante journal y recuperación fail-closed.

Consulta `docs/DEVELOPMENT_STATUS.md`, `docs/RECOVERY.md`,
`docs/RESTARTS_AND_JOURNAL.md` y `docs/V2_INDEPENDENT_SPEC.md`.

La integración con AVECOINS se documenta en `docs/AVECOINS.md`.
