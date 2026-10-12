# Interfaz adaptable

El panel se reduce uniformemente cuando el viewport lógico no alcanza, incluida la escala4. Renderizado y clic/hover/arrastre usan transformaciones inversas. Recortes se convierten a coordenadas reales de Minecraft1.21.1. Los tooltips se dibujan en el viewport real después de restaurar la matriz.

Las pantallas amplias conservan el tamaño anterior; el mercado mantiene seis anuncios por página y separa sus filas de la navegación. Los colores y tarjetas existentes permanecen. Las acciones, permisos y persistencia no cambian.

El HUD de Manager se reduce en viewports pequeños. Su editor usa el tamaño real del HUD para arrastrarlo. La posición continúa siendo una configuración global administrada mediante zianmanager.admin.

Comprobación: compilación de los cinco mods, pruebas de geometría a escalas2/3/4 y ventanas pequeñas; inspección visual final en el cliente a escala4 pendiente.
