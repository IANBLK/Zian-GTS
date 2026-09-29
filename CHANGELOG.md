# Changelog

## 1.0.0 Beta 2.2

- Los permisos documentados del GTS se comprueban en el servidor para comandos, consultas y acciones de la interfaz. Si LuckPerms está instalado pero no puede responder, la operación se deniega.
- La resolución del journal exige ejecutarse en el hilo del servidor.
- La documentación aclara el alcance real del journal y de los permisos administrativos.
- Las pruebas de cierre forzado usan archivos de argumentos de Java para ejecutarse con classpaths largos en Windows.
- AVECOINS 2.4 ya no bloquea el arranque del servidor: GTS comprueba la estructura de la cartera antes de abrir el mercado.
- Una futura versión de AVECOINS no verificada mantiene el servidor encendido y deja el mercado deshabilitado hasta revisar su contrato.
- Los botones y las pantallas secundarias comparten la paleta oscura, el borde dorado y el fondo sin blur duplicado de Zian Utilities.
- La prueba aislada de la cartera pasó con los JARs AVECOINS 2.3 y 2.4; falta validación dentro del servidor con 2.4.

## 1.0.0 Beta 2.1

Hotfix de permisos para servidores híbridos basados en Youer/Bukkit.

### Permisos
- `/gtsv2` y `/gtsv2 open` ahora pueden ser usados por jugadores normales sin OP.
- Se añade un puente de permisos compatible con Youer/Bukkit para registrar `minecraft.command.gtsv2` con acceso por defecto para jugadores.
- En NeoForge puro el puente no añade una dependencia obligatoria de Bukkit.
- Los comandos administrativos continúan protegidos:
  - `/gtsv2 status`
  - `/gtsv2 recovery`
  - `/gtsv2 recovery resolve ...`
- No se modificó la lógica de compra, venta, persistencia, economía ni recuperación.

### Compatibilidad
- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.228+
- Cobblemon 1.8+
- AVECOINS 2.3
- Youer 1.21.1

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
