---
title: Manual del backoffice
description: La intranet para editores, publicadores y administradores — contenidos, workflow, biblioteca de medios, secciones, audiencias, ver como, slots, newsletter, confirmaciones, directorio y visibilidad.
---

El backoffice tiene dos caras, en dos consolas:

- **Editorial**, el trabajo diario —contenidos, medios, contenido de slots, newsletter, ver como y
  confirmaciones—, es un menú de primer nivel, junto a *Intranet*, en la consola de datos (`https://ec1.mateu.io`, en
  local `http://ec1.localhost:8800` → *Editorial*). Solo lo ve quien tiene algún **permiso editorial**;
  quien no, ni ve la entrada ni puede abrir sus pantallas tecleando la URL.
- **Configuración** —secciones, menús, audiencias con nombre, slots, UIs externas, directorio y visibilidad: cómo funciona la
  intranet, no lo que publica— está en la consola de control (`https://console.ec1.mateu.io`, en local
  `http://console.localhost:8800` → *Intranet*), con el rol `ai-admin` y además `content:admin`.

Los permisos se asignan con roles en *Usuarios* (consola de control):

| Rol | Permisos | Puede |
| :-- | :------- | :---- |
| Editor de contenido (`content-editor`) | `content:edit` | Crear y editar contenidos, subir medios, enviar a revisión, componer newsletters |
| Publicador de contenido (`content-publisher`) | `content:edit`, `content:publish` | Además aprobar, publicar (con o sin revisión), rechazar, retirar, programar el envío de una newsletter y editar el contenido de los slots |
| Administrador de contenido (`content-admin`) | los tres | Además, en la consola de control, la configuración: secciones, menús, audiencias con nombre, slots, UIs externas, directorio y visibilidad |

En la demo: `editora` es editora, `publicador` es publicador y `demo` es administrador (ver
[Operación](/intranet/operacion/#datos-de-demo)).

## Personas

*Personas → Empleados*, un menú de primer nivel en la consola de datos, es donde RRHH mantiene a los empleados.
Lo ve quien tiene el permiso `people:edit` (rol **Administrador de personas**). También se llega desde la ficha
de una persona en el portal: **Editar esta persona**.

- **Alta**: usuario, nombre, email, puesto y responsable. Recibe el correo para fijar su contraseña.
- **Cambios**: lo que se guarda llega solo al directorio, al organigrama y a lo que ve esa persona en la intranet.
- **Responsable**: nadie puede ser su propio responsable ni depender de quien depende de él.
- **Baja**: no se admite si la persona tiene gente a cargo. Con **Reasignar personas a cargo** pasan todas a otro responsable.

Aquí no se dan permisos: los **roles y grupos** se gestionan en *Usuarios*, en la consola de control. Por eso, de
quien tiene algún rol o grupo, el email y la baja también se gestionan allí: cambiar su email permitiría entrar en su cuenta.

## Contenidos

*Editorial → Contenidos* lista todos los items, en cualquier estado, con su tipo, autor y última
modificación. **Nuevo** crea uno; un item **no se borra**: se retira.

### Crear o editar un contenido

| Sección del formulario | Qué se rellena |
| :--------------------- | :------------- |
| **Contenido** | **Tipo** (Artículo, Página, Aviso, Enlace, Documento, Pantalla externa; no se cambia después), **Sección** del árbol, **Idioma por defecto** |
| **Español / English / Deutsch** | Por idioma: título, resumen y cuerpo (texto enriquecido). Un idioma sin título no se publica; al lector sin traducción en su idioma se le muestra el idioma por defecto, que **debe** tener título |
| **Audiencia** | Quién lo verá (ver [El constructor de audiencias](#el-constructor-de-audiencias)) |
| **Publicación** | *Publicar a partir de* (vacío: en el momento de publicar), *Caduca el*, *URL* (obligatoria en enlaces y documentos; vale `media:id` para un fichero de la biblioteca), *Seguimiento de lectura*, *Confirmación obligatoria* y *Confirmar antes del*, *Al publicar, exigir reconfirmar*, *Comentarios cerrados* (se aplica al guardar, sin esperar a publicar: ver [Comentarios](#comentarios)), *Notificar a los lectores al publicar*, *Importante* |
| **Cifras** (al editar) | *Vistas*, *Lectores únicos*, *Confirmaciones* (de la versión que cuenta) y *Lectores notificados* |
| **Pantalla externa** (solo ese tipo) | *UI externa*, *Ruta* y *Atributos* (ver [Pantallas externas y componentes remotos](#pantallas-externas-y-componentes-remotos)) |
| **Bloques (solo páginas)** | Los bloques de una página (ver abajo) |
| **Avisos** | Lo que conviene revisar antes de publicar: una audiencia que no cumple nadie, un bloque que nadie vería |

**Traducir con IA** (en la barra del formulario) rellena los idiomas que aún no tienen título a partir
del idioma por defecto: título, resumen y cuerpo, con el formato intacto y las marcas `[[media:…]]` en su
sitio. En una **página**, también lo que a cada idioma le falta de sus **bloques** (título, contenido,
textos de los enlaces; ver [Bloques en varios idiomas](#bloques-en-varios-idiomas)). Solo cambia el
formulario: **revísalo y guarda**. Nada se publica sin pasar por el flujo de siempre.
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
| **Fila** | Pone lo que contiene en paralelo. Cada elemento ocupa de 1 a 12 columnas, con su *Ancho* en el panel; o elige un *Reparto* (8 + 4, 4 + 4 + 4…). En un móvil se apilan |
| **Columna** | Pone lo que contiene uno debajo de otro: sirve para tener varios bloques en una columna de una fila |
| **Componente remoto** | Una pantalla o un elemento de otra aplicación, de las registradas: *UI externa*, *Ruta* o *Atributos* (ver [Pantallas externas y componentes remotos](#pantallas-externas-y-componentes-remotos)) |

*Saludo*, *Pendientes de confirmar*, *Últimos avisos* y *Últimas noticias* sirven para montar **inicios** (ver [Inicios por audiencia](#inicios-por-audiencia)),
pero valen en cualquier página.

Al elegir el **tipo** solo se ven los campos que ese tipo usa (un bloque HTML, su título y su contenido;
un listado, su sección y cuántos), además de la audiencia.

Cada bloque puede restringirse a unos **departamentos, hoteles o puestos**. Esa audiencia **solo
estrecha** la de la página: lo ven quienes cumplen las dos. Si nadie cumple ambas, *Avisos* lo dice.

#### Bloques en varios idiomas

El texto de un bloque —**título**, **contenido** (HTML, Banner) y **textos de los enlaces**— se escribe
por idioma, como el del contenido: el formulario del bloque tiene *Título (Español)*, *Title (English)*,
*Titel (Deutsch)* y lo mismo para el contenido; cada enlace, su texto en cada idioma (la **URL** es la misma
en todos). Lo demás —tipo, item, sección, medio, UI externa, audiencia— es común.

El lector ve cada parte **en su idioma** si el bloque la tiene, y si no **en el idioma por defecto** de la
página, como un contenido sin traducción: en el portal, en la página de una newsletter (en el idioma de la
newsletter para él) y en un número, web y correo (en el idioma de cada grupo). Las páginas y maquetas
escritas antes tienen su texto en el idioma por defecto y se ven igual que antes.

Para rellenar lo que falta: **Traducir con IA** (en el formulario de la página o en el editor visual,
abajo), que traduce solo lo que a cada idioma le falta y deja el resultado en el formulario para revisarlo.

### Pantallas externas y componentes remotos

Otras aplicaciones de la casa pueden verse **dentro de la intranet**, con los permisos de cada lector en
ellas: la configuración las registra (ver [UIs externas](#uis-externas)) y el editor **elige** una de la
lista; nunca escribe dónde está.

- **Pantalla externa** (tipo de contenido): una página cuyo contenido es una pantalla de otra aplicación, en
  vivo, con su propia URL en el portal (se puede enlazar, añadir a favoritos o recargar) y el menú de la
  intranet alrededor. Se rellena *UI externa* y, para una UI de Mateu, la *Ruta* de la pantalla
  (`/loyalty/members`); para un web component, sus *Atributos* (`nombre=valor`, uno por línea). Lleva
  título, resumen, sección y audiencia como cualquier contenido, y pasa por el mismo workflow.
- **Componente remoto** (tipo de bloque): lo mismo, como un bloque más de una página. En el editor visual,
  cada UI registrada y activa sale con su nombre en el «+», en *Componentes*: al elegirla, el bloque ya apunta a
  ella. Registrar una nueva no requiere desplegar nada. Si hay muchas entradas, el menú tiene scroll y un filtro.

Quién lo ve: quien cumple la audiencia del contenido (y la del bloque) **y** la de la UI externa; además, al
abrirlo, debe tener los permisos que la UI exige. Si no, el bloque no se pinta y la pantalla dice «No puedes
usar esta aplicación». Dentro, la aplicación aplica sus propios permisos: la intranet nunca da más de lo que
la aplicación da.

En el **editor visual**, un componente remoto se ve como una tarjeta con la UI y la ruta (en vivo, solo en el
portal). En la **newsletter** no se puede ejecutar: sale un enlace «… — ver en la web».

### Editar una página sobre la propia página (editor visual)

Una **página** también se edita viéndola como la verán sus lectores. Se abre desde:

- el formulario de la página: botón **«Editar sobre la página»** (solo aparece en las páginas ya creadas);
- o **Editorial → Editor visual**, que lista las páginas: clic en una.

Lo que se ve es el **borrador** de la página, con sus bloques dibujados como en el portal.

- **Seleccionar un bloque**: al pasar el ratón se marca; un clic lo selecciona. Encima aparecen su tipo
  (y su restricción de audiencia, si tiene) y los botones ⠿ (arrastrar), ↑ ↓ (mover) y ✕ (eliminar,
  pide confirmación).
- **Quitar un medio** de un texto: pasa el ratón por encima y pulsa su ✕.
- **Ver el HTML** (panel de un bloque HTML o Banner): lo que hay detrás del texto, editable en los dos sentidos. Los
  medios son marcas `[[media:id]]`. Al guardar se quita lo que no es seguro, como los scripts.
- **Filas y columnas**: en el «+», *Diseño*. Al seleccionar una fila, el panel ofrece su **Reparto** (si faltan
  elementos, añade columnas vacías); al seleccionar lo que hay dentro, su **Ancho**. Dentro de una fila, ← →
  cambian el orden; ⠿ mueve un bloque también a otra fila o columna, y una fila o columna se mueve con lo que
  contiene. Caben hasta tres niveles. En el correo de una newsletter todo va uno debajo de otro; en un móvil, lo
  que no cabe baja a la línea siguiente. Si un lector no ve un elemento de la fila, los demás ocupan su hueco.
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
- **Idioma**: el desplegable de arriba cambia el idioma en que se escribe el texto de los bloques
  (Español, English, Deutsch; el de la página va marcado «por defecto»). En otro idioma, un bloque sin
  texto propio en él muestra el del idioma por defecto con la marca **«sin traducir · es»** (y arriba,
  cuántos hay); si no se toca, sigue sin traducir al guardar —sus lectores lo verán en el idioma por
  defecto—; al escribir en él, pasa a ser de ese idioma. Tipo, enlaces (sus URL), medios y audiencia son los
  mismos en todos los idiomas. Cambiar de idioma con cambios sin guardar pide confirmación (se pierden).
- **Traducir con IA** (en un idioma que no es el por defecto): rellena lo que a ese idioma le falta de los
  bloques a partir del idioma por defecto, con el formato y las marcas de medios intactos. No guarda:
  **revísalo y guarda**. Si la traducción pierde algo, no se aplica y sale un mensaje.
- **Guardar** guarda los bloques en el **borrador** (los textos, la audiencia y las fechas de la página
  no se tocan). Mientras hay cambios sin guardar se ve «● Cambios sin guardar»; salir por el menú, por
  otro botón o cerrando la pestaña pide confirmación. Si otra persona guardó la página mientras tanto,
  no se pisa: avisa y hay que recargar.
- **Revisión y publicación** siguen en el formulario: botón **«Volver al formulario»** → *Enviar a
  revisión* o *Publicar*.

Necesita un permiso editorial; guardar, `content:edit`.

### Plantillas de página

Una **plantilla** es de dónde sale una página nueva: un nombre, para qué sirve, los bloques que suele llevar
(con textos de ejemplo que se cambian) y, si quieres, la audiencia con la que empieza. La página que sale de
una plantilla es un borrador normal: se copia, no se enlaza (cambiar la plantilla luego no cambia ninguna
página hecha con ella).

- **Nueva página desde plantilla** (*Editorial → Nueva página desde plantilla*, `/editorial/nuevaPagina`,
  cualquier editor): elige el **sitio** (la intranet o una newsletter), la **plantilla** (las globales y las
  de ese sitio, descritas abajo con sus bloques y su audiencia; *Ver la plantilla elegida* la marca), el
  **título** y, si quieres, la **sección** del sitio. **Crear y abrir el formulario** abre el formulario del
  contenido; **Crear y editar sobre la página**, el editor visual.
- **Plantillas** (*Editorial → Plantillas*, `/editorial/plantillas`, solo **publicadores** y
  administradores, `content:publish`/`content:admin`): crear, editar y borrar plantillas. Las definen quienes
  deciden lo que se publica, como el contenido de los slots, porque son el punto de partida de todos los
  editores. *Sitio* vacío: la plantilla es **global** (sirve en la intranet y en cada newsletter); con sitio,
  solo en él (p. ej. una página especial de una newsletter con sus bloques de sección).
- **Guardar como plantilla** (botón del formulario de una página, publicadores): hace una plantilla con los
  bloques y la audiencia **guardados** de la página (guarda antes tus cambios), llamada «Plantilla: …», y la
  abre para que le pongas nombre y descripción. La de una página de una newsletter es de esa newsletter; la
  de una página de la intranet, global.

La demo trae tres: **Home de equipo o departamento** (saludo, banner, pendientes, enlaces, avisos y
noticias), **Página de bienvenida** (banner, texto, enlaces imprescindibles y últimas noticias) y **Landing de
campaña** (banner, qué es, un medio, cómo participar y novedades). Se crean al arrancar si faltan; una que
hayas cambiado no se toca.

### Enlaces de vista previa para revisores

Para que alguien que no es editor —el jefe del departamento al que va la página, quien tiene que dar el visto
bueno— revise un **borrador** antes de publicarlo, sin darle permisos editoriales:

1. En el formulario del contenido (de cualquier tipo), **Enlace de vista previa** abre sus enlaces
   (`/editorial/vistaPrevia/{id}`).
2. Elige cuántos **días** vale (7 por defecto, de 1 a 30) y, si quieres, **Enseñarlo como** un perfil del
   directorio: el revisor lo verá como lo verá ese perfil (como *Ver como*). **Crear enlace de vista previa**
   te da la URL (`…/intranet/vista-previa/{id}.{firma}`) para copiarla y mandarla.
3. La lista enseña cada enlace con su estado (*Activo*, *Caducado*, *Revocado*, *Sin efecto: ya se ha
   publicado*), quién lo creó, cuándo caduca, cuántas veces se ha abierto, cuántas personas y quiénes las
   últimas. También sale, de solo lectura, en el formulario, en *Enlaces de vista previa*.
4. **Revocar enlace** (elige cuál) lo anula. **Publicar** el contenido anula todos sus enlaces: lo que
   enseñaban ya está publicado o ha quedado viejo. Un contenido publicado sin cambios no tiene borrador que
   enseñar; edítalo y crea un enlace nuevo.

Quien abre el enlace tiene que **iniciar sesión** en la intranet (cualquier empleado; sin sesión no se enseña
nada) y ve el borrador **de solo lectura**, marcado «Borrador — vista previa». *Editorial → Enlaces de vista
previa* lista los contenidos con enlaces, cuántos siguen activos y cuántas vistas llevan. Crear y revocar
enlaces lo puede cualquiera con un permiso editorial. Funciona igual con las páginas y artículos de una
newsletter (son contenidos); los **números** de una newsletter no tienen todavía enlace de vista previa.

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

### Historial de versiones

**Historial**, en la barra del formulario de un contenido (o *Editorial → Historial de versiones* y elegir el
contenido), abre `/editorial/historial/{id}`:

- **El historial**: cada paso, el más reciente arriba —número, cuándo, quién y qué: *Creado*, *Editado*,
  *Enviado a revisión*, *Publicado* (con su número de versión), *Rechazado*, *Retirado* o *Restaurado* (y de
  dónde)—. Cada guardado que cambia algo deja una copia del borrador (un guardado sin cambios no cuenta). Las
  publicaciones anteriores a que existiera el historial salen igualmente, leídas de las versiones del contenido.
- **Comparar**: elige *Desde* y *Hasta* entre el *Borrador actual*, cada *Versión N* publicada y cada copia
  guardada (*#N · Editado…*), y pulsa **Comparar**. Al abrir, compara la última versión publicada con el borrador
  (lo que verían los lectores al publicar). Las diferencias salen por grupos: *General* (sección, URL), *Texto*
  por idioma (título, resumen y cuerpo, palabra a palabra: lo quitado tachado en rojo y lo añadido en verde; el
  cuerpo se compara por su texto, no por sus etiquetas, y si solo cambia el formato lo dice), *Bloques* (añadidos,
  quitados, movidos de posición o cambiados, y en qué), *Audiencia* y *Publicación* (fechas de publicación,
  caducidad y confirmación, seguimiento, notificación).
- **Restaurar esta versión** (`content:edit`): copia la versión elegida en *Restaurar* en el **borrador**. No
  publica nada: lo que ven los lectores sigue igual, y el borrador restaurado sigue el flujo de siempre (revisión,
  publicación). Queda en el historial como *Restaurado*. Si la versión tenía una fecha de *Publicar a partir de*
  ya pasada se quita (se publicará al publicarla); si su caducidad o su fecha límite de confirmación ya pasaron,
  avisa para que las cambies antes de publicar.

Ver el historial y comparar es para cualquier permiso editorial. Vale para los contenidos de cualquier sitio
(la intranet o una newsletter).

### Calendario editorial

*Editorial → Calendario editorial* (`/editorial/calendario`) muestra, por mes, semana o lista, qué pasa cada día:

| Entrada | Color | Cuándo |
| :------ | :---- | :----- |
| **Publica · título** | verde | Su *Publicar a partir de* |
| **Caduca · título** | rojo | Su *Caduca el* |
| **Confirmar · título** | ámbar | Su *Confirmar antes del*, si exige confirmación |
| **Revisión · título** | azul | El día en que se envió a revisión, mientras espera a un publicador |
| **Envío · newsletter nº N** | morado | Cuándo sale un número programado (abre el número) |
| **Enviado · newsletter nº N** | gris | Cuándo salió un número enviado |

De un contenido publicado salen las fechas de lo que se ve (su última versión). De un borrador nunca publicado,
las que planea, marcadas *(previsto)*; de uno publicado con cambios sin publicar, solo las fechas que cambia,
marcadas *(cambios sin publicar)*. Un borrador sin *Publicar a partir de* no tiene fecha: se publica al publicarlo.

Encima del calendario, tres filas de botones filtran por **tipo**, **sección** (con sus subsecciones) y
**estado**; sin estado elegido no salen los retirados. Las flechas y *Hoy* mueven el periodo; **Mes**, **Semana**
y **Lista** cambian la vista (en semana y lista, con la hora). **Pulsar una entrada abre el contenido.**

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
y el submenú lleva «▸». Columna **Sitio**: `intranet` o el de cada newsletter (cada una tiene su menú). Al arrancar
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

## UIs externas

*Consola de control → Intranet → UIs externas*: el **registro** de las interfaces de otras aplicaciones que
los editores pueden poner en la intranet como pantalla externa o como componente remoto (y los menús, como
entrada). Cada una tiene:

| Campo | Qué es |
| :---- | :----- |
| **Id** | Minúsculas, cifras y guiones (`riu-class`). Lo guardan las páginas, bloques y menús que la usan: no se cambia |
| **Nombre** | Lo que ven los editores en la lista |
| **Tipo** | **UI de Mateu (federada)**, la preferida: se pinta con el mismo renderer de la consola (Vaadin o Redwood), como los menús de los shells. **Web component**: un elemento propio de otra aplicación. **Iframe**: el último recurso, para una web que no es ninguna de las dos |
| **Dónde está** | UI de Mateu: su prefijo en el gateway (`/_loyalty`, `/_users`). Web component: la ruta de su script en el gateway (`/_inbox/push/push.js`). Iframe: una URL `https://` de un origen que el despliegue permite (`UI_SOURCES_IFRAME_ORIGINS`). Nada más: ni otros hosts, ni `javascript:`, ni `..` |
| **Etiqueta del web component** | El elemento que define el script (`ec-push-toggle`) |
| **Audiencia** | El constructor de siempre: quién puede usarla. Se suma (AND) a la de cada contenido o bloque que la use |
| **Permisos que exige** | Los permisos (`X-User-Scopes`) que el lector debe tener todos. Vacío: ninguno |
| **Activa** | Desactivarla oculta al momento todo lo que la usa, sin tocar los contenidos |

La ficha dice **dónde se usa**. **Borrar** una deja lo que la usaba **visible para nadie** hasta que un editor
lo cambie, como una audiencia con nombre borrada.

El prefijo de una UI de Mateu tiene que estar **enrutado por el gateway en el host donde se lee la
intranet**: en la consola de datos (`ws.…`) lo están los de sus menús (`/_loyalty`, `/_notices`,
`/_customers`…); en el host propio de la intranet (`intranet.…`), por ahora solo `/_portal` y `/_inbox`.

La demo registra **Riu Class** (`/_loyalty`) y **Notificaciones en este navegador** (el `<ec-push-toggle>`
del buzón), y los usa en la pantalla externa «Riu Class · socios» y en la página «Herramientas» (sección
*La empresa*).

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

## Newsletters

Una newsletter es **un sitio más**, como la intranet pero suyo: tiene su identidad (nombre, logo,
colores, cabecera y pie), su audiencia («quién lo verá», con el recuento de empleados), los idiomas en que
se escribe, **sus secciones** («Editorial», «Noticias de la cadena», «Personas»…), **sus páginas**
(«Quiénes somos», «Contacto»), y sus **números**. En el portal se lee en `/intranet/n/{id}`; por correo
llega cada número.

*Editorial → Newsletters* lista las newsletters (audiencia, periodicidad, números, último enviado). Al
abrir una:

- **Newsletter** e **Identidad**: nombre, descripción, idiomas (un lector de otro idioma la recibe en el
  primero), activa, logo (una imagen de la biblioteca de medios), color principal y de los enlaces
  (`#RRGGBB`), cabecera y pie (texto con formato).
- **Audiencia**: la de siempre (audiencia con nombre o atributos). Solo la ven y la reciben quienes la
  cumplen.
- **Secciones**: las del sitio de la newsletter. Cada una puede **tomar lo publicado en secciones de la
  intranet** (p. ej. «Noticias de la cadena» ← Noticias y RRHH); además, lo que se escriba en la propia
  sección (un artículo del sitio de la newsletter) va a ella.
- **Maqueta de cada número**: los bloques con los que empieza un número nuevo. Son los **bloques de
  siempre** (HTML, banner, destacado, enlaces, medio, listados…, cada uno con su audiencia que solo
  estrecha), más **«Sección de la newsletter»**, que pone los items del número en esa sección. Vacía: una
  introducción y un bloque por sección.
- **Periodicidad**: sin periodicidad (los números se crean a mano), semanal (día y hora) o mensual (día del
  mes y hora; un mes más corto usa su último día), hora de Madrid. Cuando llega el momento aparece **solo
  el borrador del número siguiente**, ya relleno; **«Enviar cada número sin revisión»** (pide
  `content:publish`) lo programa además en el acto. Un mismo momento nunca crea dos números, y si el
  servicio estuvo parado varias semanas se crea uno, no varios. **Próximo número** dice cuándo.
- **Números y páginas**: sus números (número, fecha, asunto, estado, aperturas, clics), cada uno con su
  enlace, y sus páginas.

**Su menú** (el de su sitio en el portal) empieza con su inicio, sus secciones, sus números anteriores y sus
páginas; una página creada con «Nueva página» se añade al final. Para reordenarlo, quitar o añadir entradas
(otra página, un número concreto, un enlace…): *Configuración → Menús* (consola de control,
`content:admin`), eligiendo el sitio de la newsletter.

Acciones: **Nuevo número** (lo abre ya relleno: la maqueta y lo publicado desde el número anterior en cada
sección que vea alguien de su audiencia, lo más reciente primero), **Nueva página** (una página del sitio
de la newsletter, que se escribe, revisa y publica como cualquier contenido —también *Editar sobre la
página*—; publicada, sale en su menú) y **Ver en el portal**.

### Números

*Editorial → Números de newsletter* lista todos los números (newsletter, nº, fecha, asunto, estado,
aperturas, clics); **Nuevo** pide la newsletter y abre su número siguiente. Un número tiene:

- **Asunto**, **fecha** y **Enviar el** (vacío: al programar).
- **Bloques**: como una página, también en varios idiomas (su idioma por defecto es el primero de la
  newsletter). Se editan aquí o **sobre el número** (*Editar sobre el número*: el mismo editor visual de las
  páginas, con su selector de idioma, «Traducir con IA» y vista previa como cualquier perfil).
- **Items de cada sección**, en orden. **Añadir lo nuevo** suma lo publicado desde que se abrió.
- **Destinatarios**: los grupos (perfil, idioma, secciones que quieren) con cuántos son y cuántos items ve
  cada uno. **Cada grupo recibe solo lo que puede ver**, con los bloques cuya audiencia cumple; un grupo
  que no ve nada no recibe nada. Quien se dio de baja de esta newsletter no está en ninguno.
- **Aperturas, clics y vista previa**: totales por hotel y departamento (por grupo, nunca por persona: el
  correo de un grupo es un solo mensaje) y el correo tal como sale para el primer grupo.

Estados: **Borrador → Programado → Enviado**, o **Cancelado**. **Enviar prueba** te lo manda a ti (tu
perfil, todas las secciones; en la demo, al buzón de redirección) sin registrar nada. **Programar**,
**Volver a borrador** y **Cancelar** piden `content:publish`. Si un grupo falla se reintenta solo ese; un
número enviado o cancelado ya no cambia.

El correo es la misma maqueta en HTML apto para correo: cabecera con la identidad, los bloques (un medio
es un enlace a la versión web), y al pie **«Ver en el navegador»** (el enlace permanente del número) y
**«Darte de baja de esta newsletter»** (su página en el portal). En la demo, todo correo va a un buzón
único (`NEWSLETTER_REDIRECT_TO`) como un solo mensaje con una parte por grupo.

**«Boletín de la intranet»** es la newsletter de la demo: semanal (lunes a las 9), «Lo más destacado» toma
lo publicado en todas las secciones de la intranet. Las ediciones de antes de que hubiera newsletters son
sus primeros números (ver *Arquitectura*).

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

## Salud del contenido

*Salud del contenido*, en el menú *Editorial* (`/editorial/salud`), lista lo que conviene arreglar en **todos
los sitios** (la intranet y cada newsletter). La ve quien tiene cualquier permiso editorial; actuar pide
`content:edit`, y borrar un medio, `content:admin`. Se comprueba **cada noche** (a las 3:15) y cuando alguien
pulsa **Comprobar ahora** (en segundo plano: los enlaces externos tardan; vuelve a buscar al rato).

El subtítulo da **cuántos avisos pendientes hay de cada comprobación** y cuándo fue la última. Los filtros:
comprobación, estado (*Pendientes*, por defecto, o *Revisados*) y sitio. Cada fila dice el problema, dónde
(contenido, medio, slot, inicios, menú o número de newsletter), el sitio, la comprobación y desde cuándo está.

| Comprobación | Qué mira | Cómo se arregla |
| :-- | :-- | :-- |
| **Enlaces rotos** | En lo publicado (cuerpos, bloques, enlaces rápidos, URL de enlaces y documentos), slots, inicios, entradas de menú, números de newsletter aún sin enviar y plantillas de página: enlaces a contenidos que no existen, sin publicar o retirados; secciones, personas, encuestas, rutas del portal o números que no existen; `[[media:id]]` y bloques de medio sin medio; URL firmadas de medios pegadas a mano (caducan); UIs externas borradas o desactivadas (bloques «Componente remoto», pantallas externas, menús); y los enlaces externos que fallan | *Abrir el contenido* (o *Abrir el número*, *Abrir la plantilla*); slots, inicios y menús se corrigen en la consola de control |
| **Medios huérfanos** | Medios que nada usa (ni borradores —lo que enseña un enlace de vista previa—, ni versiones antiguas, ni slots, ni números o maquetas de newsletter, ni plantillas), con su tamaño. Uno subido hace menos de un día no cuenta todavía | *Borrar el medio*: solo `content:admin`, con confirmación, y solo si en ese momento sigue sin usarse. **Nunca se borra solo** |
| **Imágenes sin texto alternativo** | Imágenes de la biblioteca sin texto alternativo (hasta ahora se leía su título) e `<img>` sin `alt` en cuerpos, bloques, slots, números y plantillas | En una imagen de la biblioteca: escribe el texto, o **Sugerir texto alternativo con IA**, revísalo y **Aplicar el texto alternativo**. La sugerencia nunca se aplica sola |
| **Caducidad** | Contenidos publicados que caducan en los próximos 14 días, y contenidos ya caducados a los que aún enlazan páginas, inicios o menús | Amplía la fecha, o quita el enlace |
| **Contenido desactualizado** | Contenidos publicados sin republicar en más de 12 meses (en las secciones configuradas, o en todas), y páginas que nadie ha abierto en los últimos 90 días (con al menos dos semanas de analítica; un inicio no cuenta) | Revisa si sigue siendo cierto y vuelve a publicar, o retíralo |

**Marcar como revisado**: en un aviso, durante los días que digas (30 por defecto) y con una nota; desde la lista,
los seleccionados, 30 días. Sale de las cuentas hasta esa fecha y vuelve si sigue ahí; *Volver a pendiente* lo
deshace. Un aviso que desaparece (porque se arregló) se lleva su marca.

**Sugerir texto alternativo con IA** manda la imagen (hasta 5 MB) a la IA del plano de control con tu token,
junto a su título y dónde se usa, y pone en el campo una frase corta en español. Si la IA no está disponible, o
no puede ver la imagen, lo dice y no cambia nada.

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
