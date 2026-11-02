---
title: Manual del backoffice
description: La intranet para editores, publicadores y administradores — contenidos, workflow, biblioteca de medios, secciones, audiencias, ver como, slots, newsletter, confirmaciones, directorio y visibilidad.
---

El backoffice tiene dos caras, en dos consolas:

- **Editorial**, el trabajo diario —contenidos, medios, contenido de slots, newsletter, ver como y
  confirmaciones—, es un menú de la propia intranet en la consola de datos (`https://ec1.mateu.io`, en
  local `http://ec1.localhost:8800` → *Editorial*). Solo lo ve quien tiene algún **permiso editorial**;
  quien no, ni ve la entrada ni puede abrir sus pantallas tecleando la URL.
- **Configuración** —secciones, menús, audiencias con nombre, slots, directorio y visibilidad: cómo funciona la
  intranet, no lo que publica— está en la consola de control (`https://console.ec1.mateu.io`, en local
  `http://console.localhost:8800` → *Intranet*), con el rol `ai-admin` y además `content:admin`.

Los permisos se asignan con roles en *Usuarios* (consola de control):

| Rol | Permisos | Puede |
| :-- | :------- | :---- |
| Editor de contenido (`content-editor`) | `content:edit` | Crear y editar contenidos, subir medios, enviar a revisión, componer newsletters |
| Publicador de contenido (`content-publisher`) | `content:edit`, `content:publish` | Además aprobar, publicar (con o sin revisión), rechazar, retirar, programar el envío de una newsletter y editar el contenido de los slots |
| Administrador de contenido (`content-admin`) | los tres | Además, en la consola de control, la configuración: secciones, menús, audiencias con nombre, slots, directorio y visibilidad |

En la demo: `editora` es editora, `publicador` es publicador y `demo` es administrador (ver
[Operación](/intranet/operacion/#datos-de-demo)).

## Contenidos

*Editorial → Contenidos* lista todos los items, en cualquier estado, con su tipo, autor y última
modificación. **Nuevo** crea uno; un item **no se borra**: se retira.

### Crear o editar un contenido

| Sección del formulario | Qué se rellena |
| :--------------------- | :------------- |
| **Contenido** | **Tipo** (Artículo, Página, Aviso, Enlace, Documento; no se cambia después), **Sección** del árbol, **Idioma por defecto** |
| **Español / English / Deutsch** | Por idioma: título, resumen y cuerpo (texto enriquecido). Un idioma sin título no se publica; al lector sin traducción en su idioma se le muestra el idioma por defecto, que **debe** tener título |
| **Audiencia** | Quién lo verá (ver [El constructor de audiencias](#el-constructor-de-audiencias)) |
| **Publicación** | *Publicar a partir de* (vacío: en el momento de publicar), *Caduca el*, *URL* (obligatoria en enlaces y documentos; vale `media:id` para un fichero de la biblioteca), *Seguimiento de lectura*, *Confirmación obligatoria* y *Confirmar antes del*, *Al publicar, exigir reconfirmar*, *Comentarios cerrados* (se aplica al guardar, sin esperar a publicar: ver [Comentarios](#comentarios)), *Notificar a los lectores al publicar*, *Importante* |
| **Cifras** (al editar) | *Vistas*, *Lectores únicos*, *Confirmaciones* (de la versión que cuenta) y *Lectores notificados* |
| **Bloques (solo páginas)** | Los bloques de una página (ver abajo) |
| **Avisos** | Lo que conviene revisar antes de publicar: una audiencia que no cumple nadie, un bloque que nadie vería |

**Traducir con IA** (en la barra del formulario) rellena los idiomas que aún no tienen título a partir
del idioma por defecto: título, resumen y cuerpo, con el formato intacto y las marcas `[[media:…]]` en su
sitio. Solo cambia el formulario: **revísalo y guarda**. Nada se publica sin pasar por el flujo de siempre.
Para retraducir un idioma, vacía su título. Si una traducción pierde una marca de medio, el formato o un
enlace del cuerpo, no se aplica. La traduce el agente `intranet-translator` del plano de control de IA, en tu
nombre y con tu presupuesto; si no está disponible, o su respuesta no sirve, sale un mensaje breve y **el
formulario no cambia**.

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
| **HTML** | Un título y un contenido con formato, escrito en el editor (títulos, negrita, listas, enlaces…; admite `[[media:id]]`) |
| **Banner** | Un título y un texto destacado, también en el editor |
| **Destacado** | Un item publicado, elegido de la lista, en grande |
| **Listado automático** | Los últimos items visibles de una sección (o de todas); *Cuántos* |
| **Enlaces rápidos** | Una línea por enlace: `Texto \| URL` |
| **Medio** | Un fichero o vídeo de la biblioteca, elegido de una lista con su título y tipo |
| **Saludo** | «Hola, …» con el nombre de quien lo lee |
| **Pendientes de confirmar** | Lo que el lector debe confirmar y no ha confirmado (nada, si no hay) |
| **Últimos avisos** | Los últimos avisos que el lector puede ver; *Cuántos* |
| **Últimas noticias** | Las últimas noticias y páginas que el lector puede ver, de una sección o de todas; *Cuántos* |

Los cuatro últimos sirven para montar **inicios** (ver [Inicios por audiencia](#inicios-por-audiencia)),
pero valen en cualquier página.

Al elegir el **tipo** solo se ven los campos que ese tipo usa (un bloque HTML, su título y su contenido;
un listado, su sección y cuántos), además de la audiencia.

Cada bloque puede restringirse a unos **departamentos, hoteles o puestos**. Esa audiencia **solo
estrecha** la de la página: lo ven quienes cumplen las dos. Si nadie cumple ambas, *Avisos* lo dice.

### Editar una página sobre la propia página (editor visual)

Una **página** también se edita viéndola como la verán sus lectores. Se abre desde:

- el formulario de la página: botón **«Editar sobre la página»** (solo aparece en las páginas ya creadas);
- o **Editorial → Editor visual**, que lista las páginas: clic en una.

Lo que se ve es el **borrador** de la página, con sus bloques dibujados como en el portal.

- **Seleccionar un bloque**: al pasar el ratón se marca; un clic lo selecciona. Encima aparecen su tipo
  (y su restricción de audiencia, si tiene) y los botones ⠿ (arrastrar), ↑ ↓ (mover) y ✕ (eliminar,
  pide confirmación).
- **Texto en el sitio**: en los bloques **HTML** y **Banner** el título y el texto se escriben
  directamente. La barra del bloque seleccionado tiene negrita, cursiva, enlace, listas, título (H2),
  subtítulo (H3), párrafo, quitar formato e **🖼 Medio**, que abre la biblioteca de medios con
  miniaturas y lo inserta donde está el cursor. Los medios se ven dibujados; se guardan como
  `[[media:id]]`. Al **pegar** desde Word o una web solo se conservan párrafos, listas, títulos,
  negrita/cursiva y enlaces.
- **Panel lateral** del bloque seleccionado: su **tipo**, su **título** y los campos de ese tipo (los
  mismos del formulario: el item de un destacado, la sección y cuántos de un listado, los enlaces de
  unos enlaces rápidos, el medio de un bloque de medio…). Los bloques que no son de texto se vuelven a
  dibujar al cambiarlos.
- **Quién lo ve**: el panel dice a quién va la página («La página la ven: …, N empleados») y deja
  marcar **departamentos, hoteles y puestos** para que el bloque, *además*, solo lo vean esos. Debajo,
  cuántos empleados lo verán (la audiencia de la página **y** la del bloque); si nadie, lo avisa.
- **Añadir**: el **«+»** entre dos bloques (o «Añadir el primer bloque») ofrece los tipos.
- **Ver como…**: el desplegable de arriba dibuja la página como la vería un perfil del directorio (lo
  no guardado incluido): los bloques que ese perfil no ve aparecen tachados como «No lo ve este perfil».
  «Volver a editar» regresa.
- **Guardar** guarda los bloques en el **borrador** (los textos, la audiencia y las fechas de la página
  no se tocan). Mientras hay cambios sin guardar se ve «● Cambios sin guardar»; salir por el menú, por
  otro botón o cerrando la pestaña pide confirmación. Si otra persona guardó la página mientras tanto,
  no se pisa: avisa y hay que recargar.
- **Revisión y publicación** siguen en el formulario: botón **«Volver al formulario»** → *Enviar a
  revisión* o *Publicar*.

Necesita un permiso editorial; guardar, `content:edit`.

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

### Notificar a los lectores

Cuando un contenido se hace efectivo —al publicarlo, o al llegar su *Publicar a partir de*— se avisa a quienes
pueden verlo, en unos segundos, en la campana y en su navegador:

- **Notificar a los lectores al publicar**: *Según el tipo* (por defecto: los avisos sí, lo demás no), *Sí* o
  *No*. Cada versión avisa una vez: si publicas otra versión de algo que notifica, se vuelve a avisar. Para
  corregir una errata, pon *No* antes de publicar.
- **Importante**: además, por correo. En la demo los correos van a la dirección de redirección.

Quien lo ha desactivado en *Mis preferencias* no recibe ese canal. Si el contenido exige confirmación y tiene
fecha límite, quien no haya confirmado recibe **un** recordatorio unos dos días antes, aunque el contenido no
notificara al publicarse. *Lectores notificados*, en *Cifras*, dice a cuántos se ha avisado.

## Biblioteca de medios

*Intranet → Medios*. Cada medio tiene un id (`m…`) y la marca para incrustarlo (`[[media:id]]`).

**Nuevo**, una de dos:

- **Subir un fichero** de **hasta 20 MB**: imágenes (no SVG), PDF, audio o vídeo. Se guarda en la intranet
  y se sirve solo a quien puede ver un contenido publicado que lo usa.
- **…o indicar dónde está**: una URL o el **código de inserción** del proveedor (el `<iframe…>` que da
  YouTube, Vimeo o Drive al pulsar *Compartir → Insertar*). Solo se guardan el proveedor y el id del
  vídeo o fichero; el reproductor oficial se construye a partir de ellos. Otra URL cualquiera queda como
  enlace. Para lo que pase de 20 MB, súbelo a YouTube, Vimeo o Drive y pon su URL.

### Un fichero de Google Drive

Pega la URL de un fichero de Drive, Docs, Sheets o Slides, tal como la copias del navegador o de
*Compartir*, en cualquiera de estos dos sitios:

- en **Medios → Nuevo**, en *URL o código de inserción*; se usa como cualquier medio;
- en el campo **URL** de un contenido de tipo documento o enlace.

La intranet guarda **solo la referencia**: nunca copia el fichero, así que hay una sola versión, la de Drive.
El lector ve la vista previa de Google y los enlaces *Abrir en Drive* y *Editar en Drive*. Comparte el fichero
en Drive con quien deba verlo: **los permisos los pone Google**, no la audiencia del contenido. Una carpeta o
un documento «publicado en la web» no es un fichero: en Medios se rechaza diciéndolo; en el campo URL de un
contenido queda como enlace normal.

Si el despliegue está conectado a Drive, en los detalles del medio ves el nombre en Drive, quién lo modificó
y, si Drive no da acceso a ese fichero, el motivo. No es un error para el lector: pierde solo el
«Modificado por…».

Para usarlo en un contenido:

- pega **`[[media:id]]`** en el cuerpo (o en un bloque HTML, o en el HTML de una variante de slot), donde
  quieras que aparezca; o
- añade un bloque **Medio** y elígelo de la lista; o
- en un documento, pon `media:id` como URL.

Las imágenes, el audio y el vídeo propios se ven directamente; los PDF y los vídeos externos salen como un
marcador que el portal convierte en visor al cargar (ver
[Problemas conocidos y límites](/intranet/operacion/#problemas-conocidos-y-límites)). Un medio no se
modifica: se sube uno nuevo. La ficha del medio tiene una vista previa.

## Secciones

*Consola de control → Intranet → Secciones*: el árbol de navegación del portal. Cada sección tiene un **id**
(va en la URL), **Dentro de** (su madre, vacío = raíz), su **posición** entre hermanas (0 = primera) y su
nombre en español, inglés y alemán. Las secciones no tienen audiencia: el portal muestra una sección si
ella o una descendiente contiene algo visible para el lector. No se puede borrar una sección con
subsecciones.

## Menús

*Consola de control → Intranet → Menús*: **el menú del portal** —qué entradas tiene, en qué orden, en qué
submenús, con qué texto y para quién—. Es configuración, no código: un cambio se ve en el portal en cuanto
el lector vuelve a cargar la página (como mucho unos 30 s en las consolas, que guardan el menú de cada
usuario ese tiempo). Lo que no cambia son las **rutas**: `/intranet/noticias`, `/intranet/noticias/{id}`…
siguen funcionando aunque la entrada se mueva, se renombre o se quite del menú.

El listado tiene una fila por entrada, en el orden del menú; lo que cuelga de un submenú va sangrado («— »)
y el submenú lleva «▸». Columna **Sitio**: hoy solo `intranet`; cada newsletter tendrá el suyo. Al arrancar
por primera vez, el menú de la intranet se rellena con **exactamente las entradas de siempre** (Inicio,
Noticias, Avisos… Mis preferencias), así que nada cambia hasta que alguien lo toque.

La ficha de una entrada:

| Campo | Qué es |
| :---- | :----- |
| **Menú del sitio** | De qué sitio es el menú (al crear) |
| **Dentro de** | El submenú que la contiene; vacío = primer nivel |
| **Posición** | Entre sus hermanas, 0 = primera (al crear, por defecto, al final) |
| **Activa** | Una entrada inactiva se conserva pero no la ve nadie, ni lo que cuelga de ella |
| **Texto (es/en/de)** | El español es obligatorio; quien lea en un idioma sin texto ve el español |
| **Destino** | Una **función del portal** (Noticias, Buscar, Por aprobar…), una **página** publicada, una **sección** u **otra interfaz** (de las registradas en la plataforma). «Ninguno» = la entrada es un **submenú** |
| **…o un enlace** | Una ruta del portal (`/intranet/personas?searchText=recepcion`) o una URL `http(s)`; una URL de fuera se abre en una pestaña nueva (el menú lleva a una página con el enlace) |
| **Ruta dentro de la otra interfaz** | Opcional, con «otra interfaz»: a qué pantalla de ella ir |
| **Quién lo verá** | El mismo constructor de audiencias que los contenidos; vacío = todos |

En la barra de la ficha, **Subir**, **Bajar**, **Meter en el submenú de encima** y **Sacar del submenú**
mueven una entrada guardada un paso. **Borrar** un submenú borra lo que contiene.

Quién ve cada entrada: la ve quien cumple su audiencia **y** puede abrir su destino —una página que no puede
ver, una sección sin nada visible para él, una interfaz para la que no tiene permiso, no le salen—. Un
submenú que se queda sin nada visible tampoco sale.

*Intranet → Menús: vista previa* dibuja el menú de un sitio **como lo vería un empleado o un perfil** (con la
misma función que el portal) y dice adónde lleva cada entrada; **Volver al menú inicial** deshace todo lo
configurado para ese sitio (en la intranet, vuelve el menú de siempre). *Editorial → Ver como* también enseña
el menú configurado.

## Audiencias con nombre

*Consola de control → Intranet → Audiencias*: audiencias reutilizables, con un id, un nombre y el mismo
constructor. La ficha enseña **los perfiles que la cumplen** y cuántos empleados tiene cada uno.

**Redefinir** una cambia lo que ve todo lo que la nombra —items, bloques, variantes de slot—, en cuanto se
recalcula la visibilidad (segundos). **Borrar** una deja lo que la nombraba **visible para nadie** hasta
que se edite: nunca se abre el contenido por accidente.

## Ver como

*Editorial → Ver como* enseña la intranet exactamente como la vería un perfil, con la misma función de
visibilidad que usa el portal (no es una simulación). Elige uno:

- **Un empleado** del directorio;
- **…o un perfil del directorio** (cada combinación de atributos, con su número de empleados);
- **…o una audiencia con nombre**: se toma su perfil más numeroso.

**Previsualizar** dibuja en la página el perfil (atributos, idioma y hash), su **menú** (el configurado en
*Configuración → Menús*, y las secciones) y el
**contenido visible**. **Ver la intranet así** hace que el propio portal (`/intranet`) se te muestre como
ese perfil —inicio, listados, secciones, búsqueda—, con una banda amarilla que lo recuerda, hasta que
pulses **Salir de ver como** (o pase una hora sin usarlo). Mientras tanto no se registran tus lecturas ni
puedes confirmar.

## Inicios por audiencia

Un inicio es **una página más**: se escribe con bloques, se revisa y se publica como cualquier contenido,
con su audiencia de siempre. En la consola de control, *Intranet → Inicio* guarda la **lista ordenada** de
páginas que hacen de inicio: a cada lector le toca **la primera de la lista que puede ver**; si no ve
ninguna, el inicio por defecto (saludo, pendientes, avisos y noticias). Pon primero las más concretas
(«Recepción Canarias») y después las generales. Solo admite páginas publicadas.

*Ver como* dice qué inicio le toca a cada perfil. La demo trae «Inicio · Recepción Canarias» (audiencia:
recepción y Canarias), primera de la lista: Ana Pérez (recepción, Tenerife) la ve; un chef de Cancún
sigue con el inicio por defecto.

## Slots

Los huecos de contenido editorial en pantallas de otras aplicaciones. Están partidos en dos:

- **El slot** —el hueco— es configuración: *Consola de control → Intranet → Slots*. Cada slot tiene una
  clave `aplicacion.pantalla.nombre`, una descripción, un **fallback** (lo que pinta la aplicación cuando
  no hay variante visible) y un estado: *Vacío*, *N variante(s)* o *Huérfano* (ya no aparece en los
  descriptores de su aplicación; no se borra, por si vuelve).
- **Lo que lo llena** —sus variantes— es trabajo editorial: *Editorial → Contenido de slots*
  (publicador), que lista los slots vivos. Cada **variante** tiene prioridad (la mayor gana; dos
  variantes no pueden tener la misma), título, HTML (admite `[[media:id]]`), enlace y audiencia (con
  nombre, regiones, hoteles, departamentos). El lector ve la variante visible de mayor prioridad. Guardar
  publica las variantes: por eso hace falta `content:publish`.

El de la demo es `front-office.bienvenida.destacado`: una variante general para todos y una de prioridad
10 para «Recepción Canarias».

## Newsletter

*Editorial → Newsletter*. Una edición la compone un editor; **programarla** (enviarla) es publicar y
pide `content:publish`. Una edición tiene título, introducción (escrita en el editor, con formato),
**items en orden**, audiencia y fecha de envío.

**El boletín de la semana se prepara solo**: cada lunes a las 9 (hora de Madrid) aparece un borrador
`w-AAAA-Wnn` con lo publicado desde el boletín anterior (noticias, avisos y páginas, lo más reciente
primero, sin las páginas de inicio), para todos. Revísalo, quita o reordena items y prográmalo. Sin
programar no sale. **Preparar boletín de la semana** (en el listado) lo crea en el momento, sin esperar
al lunes. Se configura con `content-hub.newsletter.weekly.*`: `cron`, `enabled` y `send` (enviar sin
revisión).

- **Grupos de destinatarios**: los empleados que la recibirían, agrupados por perfil e idioma, con los
  items que ve cada grupo. **Cada grupo recibe solo lo que puede ver**, en su idioma; un grupo que no ve
  ninguno de los items no recibe nada.
- **Vista previa (primer grupo)**: el correo tal como saldría.
- **Aperturas y clics** (una edición enviada): cuántas veces se abrió y cuántos clics tuvo cada item, por
  grupo y sumado por **hotel** y por **departamento**. Se cuentan **por grupo**, nunca por persona: el
  correo de un grupo es un solo mensaje para todos. La apertura depende de que el cliente de correo cargue
  imágenes, así que es una cifra mínima.
- Los grupos respetan las **preferencias** de cada empleado (*Mis preferencias* en el portal): quien se ha
  dado de baja no está en ninguno, y quien eligió secciones forma grupo aparte y solo recibe los items de
  esas secciones.
- **Programar**: se envía en la fecha indicada (vacía: ya), un correo por grupo, en menos de un minuto.
  Si un grupo falla se reintenta solo ese. Una edición enviada ya no se modifica.

En la demo, todo correo va a un buzón único (`NEWSLETTER_REDIRECT_TO`) con el grupo y el idioma en el
asunto.

## Confirmaciones

Para los items con **confirmación obligatoria**:

- **Confirmaciones** — resumen por item, hotel y departamento: cuántos deben confirmar (los empleados que
  lo ven), cuántos han confirmado en la versión que cuenta, y el porcentaje.
- **Pendientes de confirmar** — quién falta, con su email, hotel y departamento: la lista para reclamar.

Las dos se **exportan** a CSV, que Excel abre, desde el propio listado.

## Comentarios

*Editorial → Comentarios* lista todos los comentarios de todos los contenidos, también los ocultos, el más
reciente primero, y se puede buscar por texto, contenido o autor. Hace falta `content:edit`.

- **Ocultar**: al abrir un comentario, lo quita del portal. No se borra: queda quién lo ocultó y cuándo, y
  **Mostrar** lo devuelve.
- **Cerrar los comentarios** de un contenido: *Comentarios cerrados*, en la sección *Publicación* de su formulario.
  Actúa al guardar, sin crear una versión nueva. Los comentarios que hay se siguen viendo.

## Encuestas

*Editorial → Encuestas* lista todas, con su estado y cuántas respuestas tienen. Las encuestas **no se borran**: se
cierran, porque sus respuestas se perderían con ellas.

| Sección del formulario | Qué se rellena |
| :--------------------- | :------------- |
| **Audiencia** | Quién la verá, con el mismo [constructor](#el-constructor-de-audiencias) que los contenidos |
| **Encuesta** | *Título*, *Descripción*, *Abre el* (vacío: al publicarla), *Cierra el* (vacío: hasta cerrarla a mano), *Anónima* |
| **Preguntas** | Enunciado, *Tipo* (opción única, opción múltiple, valoración de 1 a 5, texto libre), *Obligatoria* y, si son de opciones, una por línea (al menos dos) |
| **Resultados** | Al verla una vez publicada: lo que se ha respondido |

**Publicar** (`content:publish`) la abre a su audiencia en cuanto llegue su fecha. Desde ese momento **sus
preguntas y si es anónima ya no se pueden cambiar**, para que las respuestas sigan significando lo mismo; su
título, descripción, audiencia y fechas sí. **Cerrar** (`content:publish`) la termina antes de tiempo; una
encuesta cerrada ya no se modifica. Llegar a la fecha de cierre deja de admitir respuestas sin hacer nada más.

**Resultados**, pregunta a pregunta: una tarta para la opción única, barras para la múltiple y la valoración
(con su media), con el recuento y el porcentaje sobre quienes respondieron esa pregunta, y los textos libres,
por orden alfabético.

*Editorial → Respuestas de encuestas* da cada respuesta en una fila, para buscar y **exportar a CSV**, que Excel abre. Las
de una encuesta anónima no dicen quién ni cuándo, y las filas van ordenadas por respuesta para que no se puedan
reconstruir las respuestas de una misma persona.

## Analítica

*Analítica*, en el menú *Editorial*, resume los últimos 30 días; lo que viene de los agregados tarda hasta 15
minutos en aparecer:

- **Indicadores**: lectores activos en 7 y 30 días (quien ha abierto un contenido o el inicio), vistas, búsquedas y
  porcentaje de búsquedas sin resultados.
- **Gráficos**: vistas por día, contenidos más vistos, lo más buscado, búsquedas sin resultados (lo que la gente
  busca y la intranet no tiene: lo primero que conviene mirar), adopción por hotel y por departamento (qué parte de
  sus empleados ha leído algo) y aperturas y clics de las últimas ediciones de la newsletter.

Las búsquedas se guardan sin saber quién las hizo, y lo que se mira en «ver como» no cuenta. Las cifras de un
contenido concreto están en su formulario, en *Cifras*.

## Directorio y visibilidad

En la consola de control, *Intranet*:

- **Directorio** (solo lectura): los empleados tal como los manda el servicio de usuarios, con hotel,
  región, departamento, puesto, idioma y **perfil (hash)**. Los atributos se cambian en *Usuarios*, no
  aquí; el cambio llega en segundos.
- **Visibilidad**: empleados en el directorio, perfiles materializados, sujetos con
  audiencia (items, bloques, variantes) y entradas en caché. Dos acciones:
  - **Recalcular** — evalúa cada audiencia para cada perfil desde cero. Debe dar exactamente lo que ya
    había («idéntica a la que había»); si algo cambia, lo dice.
  - **Pedir copia del directorio** — pide al servicio de usuarios todos los empleados de nuevo (llega en
    segundos). Útil si el directorio se ve vacío o desfasado.
