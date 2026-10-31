---
title: Manual del backoffice
description: La intranet para editores, publicadores y administradores — contenidos, workflow, biblioteca de medios, secciones, audiencias, ver como, slots, newsletter, confirmaciones, directorio y visibilidad.
---

El backoffice es el menú **Editorial** de la propia intranet, en la consola de datos
(`https://ec1.mateu.io`, en local `http://ec1.localhost:8800` → *Editorial*). Solo lo ve quien tiene
algún **permiso editorial**; quien no, ni ve la entrada ni puede abrir sus pantallas tecleando la URL. Los
permisos se asignan con roles en *Usuarios* (consola de control) y deciden también qué entradas ve cada
uno: *Secciones*, *Audiencias*, *Slots*, *Newsletter* y *Visibilidad* son solo para `content:admin`.

| Rol | Permisos | Puede |
| :-- | :------- | :---- |
| Editor de contenido (`content-editor`) | `content:edit` | Crear y editar contenidos, subir medios, enviar a revisión |
| Publicador de contenido (`content-publisher`) | `content:edit`, `content:publish` | Además aprobar, publicar (con o sin revisión), rechazar y retirar |
| Administrador de contenido (`content-admin`) | los tres | Además secciones, audiencias con nombre, slots, newsletter y recalcular la visibilidad |

En la demo: `editora` es editora, `publicador` es publicador y `demo` es administrador (ver
[Operación](/intranet/operacion/#datos-de-demo)).

## Contenidos

*Intranet → Contenidos* lista todos los items, en cualquier estado, con su tipo, autor y última
modificación. **Nuevo** crea uno; un item **no se borra**: se retira.

### Crear o editar un contenido

| Sección del formulario | Qué se rellena |
| :--------------------- | :------------- |
| **Contenido** | **Tipo** (Artículo, Página, Aviso, Enlace, Documento; no se cambia después), **Sección** del árbol, **Idioma por defecto** |
| **Español / English / Deutsch** | Por idioma: título, resumen y cuerpo (texto enriquecido). Un idioma sin título no se publica; al lector sin traducción en su idioma se le muestra el idioma por defecto, que **debe** tener título |
| **Audiencia** | Quién lo verá (ver [El constructor de audiencias](#el-constructor-de-audiencias)) |
| **Publicación** | *Publicar a partir de* (vacío: en el momento de publicar), *Caduca el*, *URL* (obligatoria en enlaces y documentos; vale `media:id` para un fichero de la biblioteca), *Seguimiento de lectura*, *Confirmación obligatoria* y *Confirmar antes del*, *Al publicar, exigir reconfirmar* |
| **Bloques (solo páginas)** | Los bloques de una página (ver abajo) |
| **Avisos** | Lo que conviene revisar antes de publicar: una audiencia que no cumple nadie, un bloque que nadie vería |

Arriba del formulario, **Estado** y **Aplicado a los lectores**: tras publicar, «Aplicando la versión
N…» hasta que la visibilidad está recalculada (unos segundos) y luego «Sí — versión N aplicada el …».

### El constructor de audiencias

El mismo en items, audiencias con nombre y newsletter:

- **Audiencia con nombre**: una ya definida («Recepción Canarias»).
- **Empresa, País, Región, Hotel, Departamento, Puesto**: los valores reales del catálogo.
- Dentro de un mismo campo, los valores se suman (**o**); entre campos distintos, y con la audiencia con
  nombre, se exigen todos (**y**). Hotel `TFS01`, `LPA01` + Departamento `RECEPCION` = la recepción de
  los dos hoteles.
- Todo vacío = **todos los empleados**.
- **Quién lo verá** lo dice en palabras y cuenta los empleados del directorio que la cumplen; **0** sale
  con un aviso ⚠️.

### Bloques de una página

| Tipo | Qué muestra |
| :--- | :---------- |
| **HTML** | Un título y HTML libre (admite `[[media:id]]`) |
| **Banner** | Un título y un texto destacado |
| **Destacado** | Un item publicado, elegido de la lista, en grande |
| **Listado automático** | Los últimos items visibles de una sección (o de todas); *Cuántos* |
| **Enlaces rápidos** | Una línea por enlace: `Texto \| URL` |
| **Medio** | Un fichero o vídeo de la biblioteca, por su id |

Cada bloque puede restringirse a unos **departamentos, hoteles o puestos**. Esa audiencia **solo
estrecha** la de la página: lo ven quienes cumplen las dos. Si nadie cumple ambas, *Avisos* lo dice.

### El workflow

| Botón | Quién | Qué hace |
| :---- | :---- | :------- |
| **Enviar a revisión** | Editor | Borrador → *En revisión*. En un item ya publicado, envía los cambios del borrador (*cambios en revisión*) sin tocar lo que se ve |
| **Rechazar** | Publicador | Devuelve al editor |
| **Publicar** | Publicador | Congela el borrador en una versión nueva. Un publicador puede publicar sin revisión previa; un editor no puede publicar |
| **Retirar** | Publicador | Deja de verse en todo: portal, menús, búsqueda, slots, newsletter y chat |

- **Programar** es publicar con *Publicar a partir de* en el futuro: el item queda publicado pero no se ve
  hasta esa hora. **Caducar** es poner *Caduca el*. Ninguno de los dos necesita intervención.
- **Editar un item publicado** no cambia lo que se ve hasta la siguiente publicación (el listado lo
  marca *cambios sin publicar*).
- **Reconfirmar**: en un item con confirmación obligatoria, marca *Al publicar, exigir reconfirmar* antes
  de publicar una versión nueva si las confirmaciones anteriores no deben valer. Sin marcar, siguen
  valiendo.

## Biblioteca de medios

*Intranet → Medios*. Cada medio tiene un id (`m…`) y la marca para incrustarlo (`[[media:id]]`).

**Nuevo**, una de dos:

- **Subir un fichero** de **hasta 20 MB**: imágenes (no SVG), PDF, audio o vídeo. Se guarda en la intranet
  y se sirve solo a quien puede ver un contenido publicado que lo usa.
- **…o indicar dónde está**: una URL o el **código de inserción** del proveedor (el `<iframe…>` que da
  YouTube, Vimeo o Drive al pulsar *Compartir → Insertar*). Solo se guardan el proveedor y el id del
  vídeo o fichero; el reproductor oficial se construye a partir de ellos. Otra URL cualquiera queda como
  enlace. Para lo que pase de 20 MB, súbelo a YouTube, Vimeo o Drive y pon su URL.

Para usarlo en un contenido:

- pega **`[[media:id]]`** en el cuerpo (o en un bloque HTML, o en el HTML de una variante de slot), donde
  quieras que aparezca; o
- añade un bloque **Medio** con su id; o
- en un documento, pon `media:id` como URL.

Las imágenes, el audio y el vídeo propios se ven directamente; los PDF y los vídeos externos salen como un
marcador que el portal convierte en visor al cargar (ver
[Problemas conocidos y límites](/intranet/operacion/#problemas-conocidos-y-límites)). Un medio no se
modifica: se sube uno nuevo. La ficha del medio tiene una vista previa.

## Secciones

*Intranet → Secciones* (administrador): el árbol de navegación del portal. Cada sección tiene un **id**
(va en la URL), **Dentro de** (su madre, vacío = raíz), su **posición** entre hermanas (0 = primera) y su
nombre en español, inglés y alemán. Las secciones no tienen audiencia: el portal muestra una sección si
ella o una descendiente contiene algo visible para el lector. No se puede borrar una sección con
subsecciones.

## Audiencias con nombre

*Intranet → Audiencias* (administrador): audiencias reutilizables, con un id, un nombre y el mismo
constructor. La ficha enseña **los perfiles que la cumplen** y cuántos empleados tiene cada uno.

**Redefinir** una cambia lo que ve todo lo que la nombra —items, bloques, variantes de slot—, en cuanto se
recalcula la visibilidad (segundos). **Borrar** una deja lo que la nombraba **visible para nadie** hasta
que se edite: nunca se abre el contenido por accidente.

## Ver como

*Intranet → Ver como* enseña la intranet exactamente como la vería un perfil, con la misma función de
visibilidad que usa el portal (no es una simulación). Elige uno:

- **Un empleado** del directorio;
- **…o un perfil del directorio** (cada combinación de atributos, con su número de empleados);
- **…o una audiencia con nombre**: se toma su perfil más numeroso.

**Previsualizar** dibuja en la página el perfil (atributos, idioma y hash), su **menú** de secciones y el
**contenido visible**. **Ver la intranet así** hace que el propio portal (`/intranet`) se te muestre como
ese perfil —inicio, listados, secciones, búsqueda—, con una banda amarilla que lo recuerda, hasta que
pulses **Salir de ver como** (o pase una hora sin usarlo). Mientras tanto no se registran tus lecturas ni
puedes confirmar.

## Slots

*Intranet → Slots* (administrador): los huecos de contenido editorial en pantallas de otras aplicaciones.
Cada slot tiene una clave `aplicacion.pantalla.nombre`, una descripción, un **fallback** (lo que pinta la
aplicación cuando no hay variante visible) y un estado: *Vacío*, *N variante(s)* o *Huérfano* (ya no
aparece en los descriptores de su aplicación; no se borra, por si vuelve).

Cada **variante** tiene prioridad (la mayor gana; dos variantes no pueden tener la misma), título, HTML
(admite `[[media:id]]`), enlace y audiencia (con nombre, regiones, hoteles, departamentos). El lector ve
la variante visible de mayor prioridad. Guardar el slot publica sus variantes.

El de la demo es `front-office.bienvenida.destacado`: una variante general para todos y una de prioridad
10 para «Recepción Canarias».

## Newsletter

*Intranet → Newsletter* (administrador). Una edición tiene título, introducción, **items en orden**,
audiencia y fecha de envío.

- **Grupos de destinatarios**: los empleados que la recibirían, agrupados por perfil e idioma, con los
  items que ve cada grupo. **Cada grupo recibe solo lo que puede ver**, en su idioma; un grupo que no ve
  ninguno de los items no recibe nada.
- **Vista previa (primer grupo)**: el correo tal como saldría.
- **Programar**: se envía en la fecha indicada (vacía: ya), un correo por grupo, en menos de un minuto.
  Si un grupo falla se reintenta solo ese. Una edición enviada ya no se modifica.

En la demo, todo correo va a un buzón único (`NEWSLETTER_REDIRECT_TO`) con el grupo y el idioma en el
asunto.

## Confirmaciones

Para los items con **confirmación obligatoria**:

- **Confirmaciones** — resumen por item, hotel y departamento: cuántos deben confirmar (los empleados que
  lo ven), cuántos han confirmado en la versión que cuenta, y el porcentaje.
- **Pendientes de confirmar** — quién falta, con su email, hotel y departamento: la lista para reclamar.

Las dos se **exportan** a CSV o Excel desde el propio listado.

## Directorio y visibilidad

- **Directorio** (solo lectura): los empleados tal como los manda el servicio de usuarios, con hotel,
  región, departamento, puesto, idioma y **perfil (hash)**. Los atributos se cambian en *Usuarios*, no
  aquí; el cambio llega en segundos.
- **Visibilidad** (administrador): empleados en el directorio, perfiles materializados, sujetos con
  audiencia (items, bloques, variantes) y entradas en caché. Dos acciones:
  - **Recalcular** — evalúa cada audiencia para cada perfil desde cero. Debe dar exactamente lo que ya
    había («idéntica a la que había»); si algo cambia, lo dice.
  - **Pedir copia del directorio** — pide al servicio de usuarios todos los empleados de nuevo (llega en
    segundos). Útil si el directorio se ve vacío o desfasado.
