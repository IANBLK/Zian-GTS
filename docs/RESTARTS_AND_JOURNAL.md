# Reinicios y journal de Zian GTS V2

## Alcance real

Zian GTS V2 usa un journal append-only para conservar evidencia durable de las operaciones sensibles. No existe una transacción atómica compartida entre Minecraft, Cobblemon, AVECOINS y los archivos de Zian GTS. Ante un resultado externo incierto, el mercado falla de forma conservadora y puede requerir conciliación manual.

El estado V2 se guarda bajo el directorio del mundo:

- `ziangts-v2/market-v2.state`
- `ziangts-v2/history-v2.wal`
- `ziangts-v2/transactions-v2.wal`

`transactions-v2.wal` contiene frames encadenados mediante SHA-256. Cada evento incluye versión y secuencia. El journal actual usa formato **V2** y un máximo de **8 MiB por frame**. Cada append ejecuta `FileChannel.force(true)`.

El journal registra evidencia de la operación, su sujeto y las etapas alcanzadas. Los eventos actuales son `begin`, `stage`, `complete`, `abort`, `quarantine` y `resolve`. El journal V2 **no almacena snapshots NBT/SNBT completos** de Pokémon, wallets o del mercado.

## Reinicios programados

Para reinicios normales, enviar `stop` y permitir que el proceso termine antes de iniciarlo otra vez. No sustituir un cierre normal por un kill forzado en producción.

Al detenerse, el runtime deja de estar disponible y cierra sus recursos persistentes. Al arrancar, abre el mercado y el journal. Una operación que llegó durablemente a `RUNTIME_COMPLETE` puede reconciliarse automáticamente; cualquier otra evidencia pendiente mantiene el mercado bloqueado.

Los reinicios automáticos STOP -> espera -> START usados por el servidor son compatibles con este modelo siempre que el proceso anterior haya terminado realmente antes del nuevo START.

## Recovery después de un crash

Consultar el estado:

```
/gtsv2 status
/gtsv2 recovery
```

Estos comandos administrativos requieren actualmente nivel de permiso **2** mediante `CommandSourceStack.hasPermission(2)`. La versión V2 no depende de nodos LuckPerms para autorizar recovery.

`/gtsv2 recovery` muestra hasta diez operaciones no resueltas con operation ID, tipo, subject, etapa, estado de quarantine y motivo.

Antes de resolver una operación, comprobar manualmente:

1. estado del anuncio;
2. Pokémon del comprador/vendedor;
3. saldo/cartera AVECOINS;
4. ganancias pendientes;
5. historial relevante.

Para iniciar la resolución:

```
/gtsv2 recovery resolve <operation-uuid>
```

El comando muestra una advertencia y exige confirmación explícita:

```
/gtsv2 recovery resolve <operation-uuid> confirm
```

La resolución elimina únicamente esa evidencia pendiente del journal. No cobra, devuelve, entrega ni reconstruye automáticamente Pokémon o moneda. Cuando se resuelva la última operación, **reiniciar el servidor** antes de reabrir el mercado.

No borrar ni editar manualmente `transactions-v2.wal` para saltarse un bloqueo.

## Inspector offline

Con el servidor detenido, trabajar preferiblemente sobre una copia:

```
python tools/inspect_journal.py transactions-v2.wal
python tools/inspect_journal.py transactions-v2.wal --export inspeccion-gts
```

El inspector es de solo lectura. Valida formato V2, secuencia, tamaño máximo de 8 MiB y la cadena SHA-256. `--export` crea archivos JSON únicamente para el prefijo verificado. No repara, trunca ni reproduce operaciones.

## Límites y mantenimiento

El journal no se compacta automáticamente en Beta 1. El mercado y el historial también utilizan almacenamiento durable propio. Para servidores con gran volumen de operaciones conviene vigilar el crecimiento de estos archivos y conservar backups coherentes del mundo y de los datos externos relacionados.

Rotación/compactación, métricas de latencia y pruebas de carga a gran escala quedan como mejoras posteriores.

## Youer 1.21.1

Zian GTS V2 ha sido probado en el entorno objetivo Youer 1.21.1 con operaciones de mercado, persistencia y reinicios. Aun así, cada artefacto de release debe recibir un smoke test final en el servidor objetivo antes de considerarlo candidato publicado.

AVECOINS 2.3 es la economía soportada por esta Beta. La integración valida el contrato esperado y trata un resultado de guardado incierto como una operación que no debe reintentarse automáticamente.

## Verificación recomendada antes de una release

- ejecutar `./gradlew clean build` sobre el commit exacto;
- verificar el JAR y su SHA-256;
- probar ese JAR en Youer 1.21.1;
- publicar, comprar, retirar y reclamar ganancias;
- comprobar party/PC y wallet llena;
- probar reinicio normal;
- ejecutar recovery sobre un entorno de prueba con evidencia pendiente;
- ejecutar `tools/inspect_journal.py` sobre un journal V2 real;
- comprobar que no aparecen duplicaciones de Pokémon o moneda.
