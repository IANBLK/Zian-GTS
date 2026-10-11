# GTS beta.2.5 — historial y mantenimiento

El historial conserva el formato `history-v2.wal`. En memoria mantiene posiciones
de archivo, checksums, IDs de operación y referencias por jugador en vez de todas
las transacciones deserializadas. La GUI lee únicamente las filas solicitadas,
verificando el contenido contra el índice. La detección de IDs duplicados usa
un conjunto, evitando búsquedas lineales durante cada append y la recarga.

Un historial cerrado/no disponible bloquea la compra antes de retirar monedas
o entregar el Pokémon. Las ofertas y los saldos pendientes mantienen su formato.

## Archivo de transacciones resueltas

Con el servidor detenido y sin reinicio automático:

```text
java -cp <gts.jar>:<gson.jar>:<kotlin-stdlib.jar> com.zianblk.ziangts.v2.persistence.TradeStorageMaintenance compact-closed-offline <mundo>/ziangts-v2/transactions-v2.wal
```

Java 21; usar `;` en Windows y entrecomillar rutas con espacios. Se rechaza el
mantenimiento si existe cualquier operación pendiente o en cuarentena. El WAL
completo se archiva como gzip y se verifica byte a byte por hash. Solo cuando no
hay pendientes se sustituye el activo por un WAL vacío válido para operaciones
nuevas. No se modifica el historial de compras, el mercado ni los saldos.

El archivo comprimido contiene la auditoría original, no una eliminación de las
transacciones. No borrar estos archivos por inactividad de usuarios. Conservar
los marcadores y backups tras un corte para revisión. Esquema de transacciones 2
y modelo de mercado intactos. Una escritura fallida bloquea el diario vivo.

El índice del historial todavía crece linealmente con el número de operaciones,
aunque es menor que conservar cada objeto completo. Monitorizar disco y RAM;
la mejora no constituye una política de borrado automático.
