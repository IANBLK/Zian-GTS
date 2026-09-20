# Permisos de Zian GTS

LuckPerms es opcional y solo se necesita en el servidor, en su versión compatible
con NeoForge. Zian GTS consulta su API, incluidos grupos, comodines y contextos.
No se incluye LuckPerms dentro del JAR.

| Nodo | Acción | Valor predeterminado |
|---|---|---|
| `ziangts.use` | Acceso general a comandos e interfaz | Todos |
| `ziangts.list` | Listado y filtros públicos | Todos |
| `ziangts.mine` | Anuncios propios y filtro Mis anuncios | Todos |
| `ziangts.sell` | Publicar Pokémon | Todos |
| `ziangts.buy` | Comprar, por comando o interfaz | Todos |
| `ziangts.cancel` | Retirar anuncios propios | Todos |
| `ziangts.claim` | Reclamar ganancias | Todos |
| `ziangts.admin.history` | Consultar historial | OP nivel 2 |
| `ziangts.admin.history.archive` | Archivar historial | OP nivel 2 |
| `ziangts.admin.recovery` | Consultar incidentes | OP nivel 2 |
| `ziangts.admin.recovery.resolve` | Marcar incidentes y operaciones del diario como resueltos | OP nivel 2 |

Todos requieren `ziangts.use`. Archivar también requiere acceso al historial y resolver incidentes requiere también `ziangts.admin.recovery`.
La interfaz requiere list o mine según el filtro, además del permiso específico
para cada acción. Los botones pueden seguir visibles: el servidor comprueba los
permisos al usarlos. Una denegación explícita también afecta a los operadores.
Si LuckPerms está instalado pero no está disponible, se deniega el acceso.
Sin LuckPerms se conservan los valores predeterminados de la tabla.

Ejemplos (los grupos deben existir):

```text
/lp group default permission set ziangts.sell false
/lp group vip permission set ziangts.sell true
/lp group moderador permission set ziangts.admin.history true
/lp group moderador permission set ziangts.admin.recovery true
/lp group moderador permission set ziangts.admin.history.archive false
```

Tras cambiar permisos, puede ser necesario reconectar para actualizar el
autocompletado de comandos. Las acciones consultan el permiso actual en servidor.
Estos permisos no habilitan retirar anuncios ajenos ni evitan las validaciones
de propiedad, saldo, almacenamiento o seguridad del mercado.
