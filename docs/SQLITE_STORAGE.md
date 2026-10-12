# Almacenamiento SQLite y mantenimiento seguro

Los cuatro mods pueden guardar sus datos en cuatro bases independientes dentro de `world/zian-storage`: `manager.db`, `rct.db`, `utilities.db` y `gts.db`. Las configuraciones generales del servidor conservan su ubicación habitual. Los datos de Minecraft, Cobblemon, AVECoins y otros plugins no se trasladan a estas bases.

## Activación y respaldo

SQLite es optativo. Con el servidor detenido, ejecute `StorageMaintenance migrate-offline <world>` para cada mod, usando el JAR y Java 21. La importación verifica cada documento byte por byte y publica `storage.properties` al final. Los originales permanecen intactos hasta validar dos arranques. Una base ausente, incompleta o corrupta bloquea el acceso: nunca se sustituye por una base vacía ni se vuelve a una copia antigua.

Antes de importar o limpiar, conserve un respaldo completo con el servidor detenido. En funcionamiento pueden existir archivos `.db-wal`, `.db-shm` y `.db.guard`: forman parte del funcionamiento normal y no deben borrarse. Para restaurar, detenga el servidor y restaure conjuntamente la carpeta de almacenamiento y los datos externos de economía/Pokémon del mismo respaldo.

## Retención

Valores iniciales: historial de 90 días y jugadores inactivos durante 30 días. La ausencia de una fecha de actividad protege al jugador. Una partida guardada recientemente también lo protege, aunque haya ocurrido un cierre inesperado.

El administrador puede consultar `/zianstorage <mod> status` y ajustar `/zianstorage <mod> retention <diasHistorial> <diasInactivo>`. Cada mod conserva sus permisos administrativos. La limpieza se ejecuta fuera de línea; el ajuste de política no elimina datos mientras hay jugadores conectados.

- Manager/RCT: `RewardRetention preview|apply <world>` retira únicamente el detalle de premios completamente entregados, antiguos y de jugadores conocidos como inactivos. Conserva permanentemente ID, origen, ciclo, espera y combate, con comprobación de integridad. La sustitución del documento y sus recibos ocurre en una sola transacción.
- GTS: `HistoryRetention preview|apply <world>` depura detalles antiguos cuando ambos participantes están inactivos, no hay transacciones pendientes y el vendedor no tiene cobros pendientes. Conserva permanentemente los ID de operación para impedir repeticiones.
- Todos: `StorageMaintenance cleanup-preview|cleanup-offline <world>` comprime páginas antiguas sin pérdida y recupera espacio fuera de línea. Los temporales se conservan porque pueden contener evidencia de una entrega interrumpida.

El detalle retirado se archiva y se comprueba antes de reemplazarlo. Los archivos de auditoría antiguos solo se eliminan cuando sus recibos permanentes coinciden. No se borra por inactividad el saldo, propiedad, progreso, misiones pendientes, genética o premios en revisión. Un jugador que vuelve conserva su información indispensable.

## Compatibilidad

El controlador SQLite está empaquetado como recurso privado, fuera del descubrimiento de módulos de NeoForge. En Youer se reutiliza un controlador compatible disponible; en NeoForge se carga el incluido. Esto evita añadir un segundo módulo `org.xerial.sqlitejdbc`, el problema descrito en Youer #508. Las bases son locales; no requieren un servicio de base de datos externo.

La retención tiene límites deliberados: los recibos permanentes y el progreso esencial crecen con las operaciones. Reducir el número de archivos no elimina la necesidad de respaldos y supervisión del espacio. Este cambio conserva los formatos de dominio existentes dentro de documentos y páginas transaccionales; no convierte todos los datos en tablas de negocio nuevas.
