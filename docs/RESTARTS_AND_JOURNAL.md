# Reinicios y diario de Zian GTS

## Alcance real

El diario conserva evidencia en disco antes de mover Pokémon o dinero. No es una
transacción atómica con los archivos de Minecraft, Cobblemon y AVECOINS. Un crash
puede requerir conciliación manual: no se promete devolución automática ni ausencia
absoluta de pérdida ante fallos del disco, eliminación de archivos o backups parciales.

El archivo del mundo `ziangts-journal/transactions.wal` contiene registros encadenados
con SHA-256, versión y secuencia. Cada escritura usa `FileChannel.force(true)`;
la creación fuerza también el directorio. Si el sistema de archivos no permite
estas operaciones, el mercado se bloquea. Solo un proceso puede abrir el diario
para escribir. No se recortan colas incompletas ni se sobrescribe evidencia dañada.

Venta, compra, retirada y cada moneda reclamada registran intención antes de la
primera mutación. Se conserva el snapshot NBT del Pokémon/anuncio o del pago,
el actor, moneda/proveedor, importe, transacción de compra y una copia previa del
almacenamiento GTS. Se registran las fronteras de cobro, entrega, devolución,
acreditación e historial. El fin de la operación guarda una copia posterior del GTS.
Estas copias son evidencia independiente, no se cargan automáticamente sobre datos vivos.

## Reinicios programados cada 6 horas

El programador externo debe enviar `stop` y esperar a que el proceso termine antes
de arrancarlo de nuevo. No usar kill forzado, reinicio del contenedor inmediato ni
un timeout menor que el tiempo de guardado real de los mods. Respaldar todo el mundo,
datos de jugadores, Cobblemon, AVECOINS y diario como un conjunto consistente.

Al recibir ServerStopping el mod deja de aceptar operaciones y solicita guardar sus
SavedData. Solo si también recibe ServerStopped y no hay operaciones incompletas,
incidentes o errores, escribe un marcador de cierre ordenado con un checkpoint.
Al iniciar compara el checkpoint de cierre con los SavedData GTS y bloquea si difieren.
Un cierre correcto permite reiniciar sin resolver manualmente cada compra.

El marcador demuestra la secuencia de eventos, no que cada mod haya confirmado fsync:
Cobblemon usa guardado asíncrono y su API pública de almacenamiento no proporciona
una confirmación transaccional común con AVECOINS. Un fallo silencioso de guardado
externo durante stop aún requiere revisión. Esta limitación debe validarse en el
servidor exacto antes de uso real.

## Tras un crash

Si falta el marcador de cierre, las operaciones desde el último cierre ordenado
quedan retenidas, incluso las que terminaron en memoria. El mercado se bloquea;
no se vuelve a cobrar, reembolsar ni entregar automáticamente. Esto evita asumir
que el guardado asíncrono terminó antes del crash.

- `/gts recovery`: incidentes existentes y resumen del diario.
- `/gts recovery journal`: estado y últimas diez operaciones retenidas.
- `/gts recovery journal resolve <uuid> confirm`: registra nombre/UUID del
  administrador y fecha; libera solo esa operación después de la conciliación manual.
  Requiere `ziangts.use`, `ziangts.admin.recovery` y `ziangts.admin.recovery.resolve`.
- Los incidentes de SavedData se resuelven por separado con el comando existente.
  Resolver el diario no borra esos incidentes ni inventa pagos.

Un diario corrupto o un checkpoint que no coincide no se desbloquean con resolve:
conservar los originales y reparar/restaurar un conjunto coherente antes de reiniciar.
No borrar el diario para evitar el bloqueo: se perdería la evidencia independiente.

El archivo crece y no se poda automáticamente en esta versión. Las copias completas
del GTS por operación favorecen la recuperación inicial pero tienen coste de disco
y latencia en el hilo del servidor. Hay límite de 32 MiB por registro y fallo cerrado.
Retención/compactación y pruebas de rendimiento siguen pendientes.

## Youer 1.21.1

Youer combina NeoForge con APIs Paper/Purpur. Se revisó su rama 1.21.1 (propiedades:
NeoForge 21.1.251, Java 21), no una instalación real ni la build que usará el servidor.
La compatibilidad sigue sin certificarse. No usar plugins que modifiquen los archivos
GTS o reemplacen el cierre ordenado mientras se valida esta integración.

La integración LuckPerms actual detecta el mod NeoForge. No se ha implementado un
puente para LuckPerms instalado exclusivamente como plugin Bukkit en Youer; en ese
caso los nodos no se consultan y se usan los valores predeterminados del mod.

Fuentes inspeccionadas:
- https://github.com/MohistMC/Youer/blob/1.21.1/gradle.properties
- https://github.com/MohistMC/Youer/blob/1.21.1/patches/net/minecraft/server/MinecraftServer.java.patch
- https://github.com/Cobblemon-Global/Cobblemon/blob/main/common/src/main/kotlin/com/cobblemon/mod/common/api/storage/factory/FileBackedPokemonStoreFactory.kt

## Verificación

Tests del diario: ciclos de cierre/reapertura; proceso hijo terminado con Runtime.halt
en las fronteras de compra; resolución persistente; múltiples pendientes; corrupción,
truncamiento y doble escritor. Estas pruebas no ejecutan Minecraft ni simulan fallos
de los discos o callbacks reales de los mods. Antes de multiplayer: arrancar la build
exacta de Youer, ensayar stop/reinicio con un mundo de prueba y comprobar saldos,
anuncios, Pokémon y reclamaciones antes/después. Luego repetir cierres forzados solo
sobre copias desechables y comprobar la conciliación.
