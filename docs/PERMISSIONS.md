# Permisos de Zian GTS

LuckPerms es opcional y solo se necesita en el servidor, como mod de NeoForge
o plugin de Bukkit en Youer. Zian GTS consulta los permisos calculados por su API.
No se incluye LuckPerms dentro del JAR.

El comando es `/ZianGTS` (respeta las mayúsculas). El puente de Youer registra
`minecraft.command.ziangts` para permitir que se llegue a las comprobaciones
`ziangts.*`; no reemplaza un permiso de Bukkit ya configurado por el administrador.
Si utilizabas reglas para el comando anterior, actualiza esas reglas al nuevo nombre.

| Nodo | Acción | Valor predeterminado |
|---|---|---|
| `ziangts.use` | Acceso general a comandos e interfaz | Todos |
| `ziangts.list` | Listado y filtros públicos | Todos |
| `ziangts.mine` | Anuncios propios, ganancias pendientes e historial personal | Todos |
| `ziangts.sell` | Publicar Pokémon | Todos |
| `ziangts.buy` | Comprar desde la interfaz | Todos |
| `ziangts.cancel` | Retirar anuncios propios | Todos |
| `ziangts.claim` | Reclamar ganancias | Todos |
| `ziangts.admin.recovery` | Consultar estado e incidentes | OP nivel 2 |
| `ziangts.admin.recovery.resolve` | Marcar incidentes y operaciones del diario como resueltos | OP nivel 2 |

Todos requieren `ziangts.use`. Resolver incidentes requiere también `ziangts.admin.recovery`.
La interfaz requiere list o mine según el filtro, además del permiso específico
para cada acción. Los botones pueden seguir visibles: el servidor comprueba los
permisos al usarlos. Una denegación explícita también afecta a los operadores.
Si LuckPerms está instalado pero no está disponible, se deniega el acceso.
Sin LuckPerms se conservan los valores predeterminados de la tabla.

Ejemplos (los grupos deben existir):

```text
/lp group default permission set ziangts.sell false
/lp group vip permission set ziangts.sell true
/lp group moderador permission set ziangts.admin.recovery true
/lp group moderador permission set ziangts.admin.recovery.resolve false
```

Tras cambiar permisos, puede ser necesario reconectar para actualizar el
autocompletado de comandos. Las acciones consultan el permiso actual en servidor.
Estos permisos no habilitan retirar anuncios ajenos ni evitan las validaciones
de propiedad, saldo, almacenamiento o seguridad del mercado.
