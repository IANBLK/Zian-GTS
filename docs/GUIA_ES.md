# Zian GTS 1.0.3 — Guía actual

La interfaz y los diálogos son principalmente españoles; la traducción inglesa está incompleta. [Guía completa en inglés](GUIDE.md) · [Instalación y recuperación](UPDATING.md) · [Resumen y requisitos](../README.md)

Abre /ZianGTS o /gts cuando esté disponible. Mercado permite buscar, filtrar y comprar con confirmación. Publicar toma un Pokémon del equipo. Mis anuncios permite retirar; Ganancias reclama ventas y el Historial consulta operaciones. Los anuncios caducados desaparecen del mercado público pero su dueño puede retirarlos.

## Instalar y recuperar

Minecraft 1.21.1, NeoForge y Java 21. Usa el mismo JAR y dependencias en cliente y servidor. Detén y respalda mundo/configuración/mods antes de actualizar; sustituye el JAR anterior. No borres diarios ni recibos para desbloquear premios. Una operación incierta requiere revisar inventario/PC/cartera y su referencia. Para retroceder restaura una copia completa compatible.

### Archivos de recuperación de Pokémon

Si el diario falla al comprar o retirar, GTS repone la oferta cuando no se cobró nada. Si ya se cobró (etapas `PAYMENT_APPLIED` o `BEFORE_POKEMON_DELIVERY`) o no se puede reponer la oferta, el Pokémon no se entrega y se guarda en `<mundo>/ziangts-v2/recovery/<ticket>.json` (ticket en cuarentena y `ERROR` en el log). Contiene ticket, etapa, id de oferta, UUID de comprador y vendedor, precio, fecha, error y el Pokémon completo (`pokemon.serialized` es el NBT guardado). Si el archivo no se puede escribir, el mismo JSON sale en el log. Restauración manual: detén el servidor, comprueba que la oferta no siga en el mercado, revisa cartera y PC del comprador, y devuelve el Pokémon desde `pokemon.serialized` (al comprador solo si `paymentApplied` es `true` y el cobro consta en su cartera; si es `false` no hubo cobro y el Pokémon vuelve al vendedor) o reembolsa; nunca ambas cosas. Luego resuelve el ticket con el flujo de revisión de administrador y conserva el archivo.
