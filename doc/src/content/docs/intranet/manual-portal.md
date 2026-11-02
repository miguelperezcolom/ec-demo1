---
title: Manual del portal
description: La intranet para el empleado — inicio, noticias, avisos y documentos, comentarios y «Me gusta», encuestas, secciones, búsqueda, personas y organigrama, solicitudes, favoritos, confirmar lectura, preferencias de la newsletter y de los avisos, y preguntar al chat.
---

La intranet está en la consola del plano de datos, en el menú **Intranet** (`https://ec1.mateu.io`, en
local `http://ec1.localhost:8800` → *Intranet* → `/intranet/inicio`). Entra cualquier usuario con sesión.

:::note[Solo ves lo que es para ti]
Cada contenido tiene una audiencia: una empresa, un país o región, unos hoteles, unos departamentos, unos
puestos… Lo que no es para ti **no existe** en ninguna parte del portal: ni en el inicio, ni en los
listados, ni en el menú de secciones, ni en la búsqueda, ni en tus favoritos, ni en lo que te responde el
chat. Tus atributos los mantiene RRHH en el servicio de usuarios; si cambian (otro hotel, otro puesto), lo
que ves cambia en unos segundos.
:::

El menú de la izquierda lo configura el equipo de la intranet (ver
[Menús](/intranet/manual-backoffice/#menús)): puede que tengas entradas que otro no tiene, o submenús. Las
direcciones de cada pantalla (`/intranet/noticias`, `/intranet/buscar`…) son siempre las mismas.

## Inicio

*Intranet → Inicio* te saluda por tu nombre y reúne:

- **⚠️ Pendientes de confirmar**, arriba y solo si los hay: los contenidos que debes confirmar haber leído.
- **Encuestas por responder**, solo si las hay: las encuestas abiertas para ti que aún no has respondido.
- **📢 Avisos**: los cinco últimos avisos.
- **📰 Últimas noticias**: las ocho últimas noticias y páginas.

Cada título abre el contenido.

Ese es el inicio **por defecto**. Tu empresa puede haber preparado un inicio para tu grupo (por ejemplo,
para recepción en Canarias, con tus turnos y enlaces a mano): si es así, al entrar verás ese en su lugar.

## Noticias, avisos y documentos

| Menú | Qué lista |
| :--- | :-------- |
| **Noticias** | Los artículos, el más reciente primero |
| **Avisos** | Los avisos: comunicaciones cortas, a menudo con confirmación |
| **Documentos** | Documentos y enlaces (un calendario laboral, el portal del empleado…) |

Abrir un contenido muestra su título, fecha, sección y cuerpo **en tu idioma** (español, inglés o alemán);
si no está traducido, en el idioma original. Puede llevar imágenes, audio, vídeos (propios o de YouTube,
Vimeo, Drive) y PDF incrustados; los vídeos externos y los PDF se convierten en reproductor al cargar la
página.

Una **página** es un contenido compuesto de bloques: un banner, un destacado, un listado automático de una
sección, enlaces rápidos, un medio… Un bloque puede ir solo para algunos (por ejemplo, «Solo para
recepción»): si no es para ti, no lo ves, aunque veas la página.

Los contenidos programados aparecen solos a su hora, y los caducados desaparecen solos.

## Secciones

*Secciones* es el árbol de la intranet (*La empresa → Noticias*, *Personas → RRHH y nóminas*,
*Operaciones → Recepción*…). **Una sección sin nada que puedas ver no aparece**: el árbol de un
recepcionista de Tenerife no es el de un chef de Cancún. Abrir una sección lista su contenido visible, el
más reciente primero.

## Buscar

*Buscar* busca en todo lo que puedes ver, de dos formas a la vez:

- **por palabras**, en título, resumen y cuerpo. Valen **por su principio**: «vacac» encuentra «vacaciones»,
  «turn» encuentra «turnos»;
- **por significado**: «días libres» puede encontrar la política de vacaciones aunque no use esas palabras.

Salen los 20 mejores, con el trozo de texto que coincide bajo cada título. Todo lo que aparece es algo que
puedes abrir. Si el buscador por significado no responde, lo dice y busca solo por palabras.

**Respuesta de la IA.** Si lo que escribes parece una pregunta («¿cuántos días de vacaciones tengo?»), arriba
de los resultados aparece una respuesta redactada por la IA **solo con contenidos que puedes leer**, y debajo
los contenidos en los que se apoya, como enlaces. Tarda unos segundos más; compruébala en las fuentes. Si
escribes solo unas palabras, pulsa **Preguntar a la IA** para pedirla. Si la IA no está disponible, se dice en
una línea y los resultados siguen ahí.

**Personas.** Si lo que escribes coincide con alguien del directorio, sale aparte, en el grupo *Personas*,
con un enlace a su ficha.

## Documentos de Drive

Un contenido puede enlazar un fichero de Google Drive, Docs, Sheets o Slides. La intranet guarda solo la
referencia, nunca una copia, así que **siempre ves la versión que hay en Drive**. Al abrir el contenido:

- verás la **vista previa** de Google, si tu cuenta de Google tiene acceso al fichero. Si te pide iniciar sesión
  aunque ya la hayas iniciado, tu navegador no deja usar tu sesión de Google dentro de la intranet: usa **Ver en
  otra ventana**;
- **Abrir en Drive** lo abre en Google, y **Editar en Drive** en su editor (Docs, Sheets y Slides; un PDF u
  otro fichero solo se abre);
- si el despliegue está conectado a Drive, verás también **«Modificado por X el …»** y el nombre actual.

*Documentos de Drive*, en el menú, lista los ficheros de los contenidos que puedes ver; cada fila abre su
contenido. Quién puede abrir el fichero lo decide Google, con los permisos del propio fichero: que aparezca
aquí no te da acceso.

## Personas y organigrama

**Personas** es el directorio de la empresa: todos los empleados en activo, con su puesto, departamento,
hotel, teléfono y email. Escribe en la barra de búsqueda palabras de su nombre, puesto, departamento, hotel
o región; tienen que estar **todas** y los acentos no importan: «recepcion tenerife» da el equipo de
recepción de Tenerife, «jefe recepcion» los jefes de recepción de todos los hoteles.

Una fila abre la **ficha** de la persona:

- su puesto, departamento, hotel y región, con su email y su teléfono (un clic los abre en el correo o el
  teléfono);
- **Reporta a**: su responsable y el responsable de su responsable, hasta la dirección general;
- **Su equipo directo**: quién le reporta, si es responsable de alguien;
- **Mismo equipo**: quién está en su departamento y su hotel.

Cada persona de la ficha enlaza a su propia ficha.

**Organigrama** muestra la empresa entera: arriba quien la dirige y quien le reporta; debajo, **por
estructura**, cada empresa con sus regiones, hotel por hotel (o los servicios centrales), y en cada
departamento sus personas, el responsable primero.

El directorio no depende de las audiencias: todos ven a todos. Solo salen datos de trabajo, y los usuarios
desactivados no aparecen. Quién reporta a quién y el teléfono los mantiene RRHH en el servicio de usuarios
(*Usuarios → Directorio*, campos **Responsable** y **Teléfono**).

## Favoritos

En un contenido, **Favorito** (en la barra de herramientas) lo añade a **Mis favoritos**; pulsarlo otra vez
lo quita. Si un favorito deja de ser para ti (se retira, caduca o cambia su audiencia), desaparece de la
lista; si vuelve a serlo, reaparece.

## Solicitudes

**Nueva solicitud** pide algo a la empresa: **vacaciones** (con fechas), **formación** o **material**, con el
detalle que quieras. Al enviarla:

1. La decide **tu responsable** (el del organigrama). Le aparece en su **Por aprobar**. Si en **dos días**
   no la ha decidido, pasa al responsable de tu responsable.
2. Si la aprueba, la revisa **RRHH**.
3. Te llega un **correo** cuando está decidida, aprobada o rechazada, con el comentario de quien la decidió.

**Mis solicitudes** las lista con su estado (*Pendiente de tu responsable*, *Pendiente de RRHH*,
*Aprobada*, *Rechazada*). Cada una abre su **historial**: quién hizo qué, cuándo y con qué comentario.

**Por aprobar** es lo que te toca decidir: las solicitudes de tu equipo, como responsable, o las que
llegan a RRHH, si eres de RRHH. Abre una, escribe un comentario para el empleado y pulsa **Aprobar** o
**Rechazar** (rechazar pide confirmación).

Sin responsable en el directorio no se puede hacer una solicitud: RRHH tiene que asignártelo.

## Newsletters

*Newsletters* lista las newsletters que son para ti (las de tu audiencia), si las recibes y su último
número. Cada una es un pequeño sitio, con su logo y sus colores y **su propio menú**: *Inicio* (el último
número y los anteriores), sus **secciones** (lo que han traído los números en esa sección y sus propios
artículos), *Números anteriores* (el archivo) y **sus páginas** («Quiénes somos»…). Cada número tiene su
página permanente: es el mismo número que te llegó por correo («Ver en el navegador» lleva ahí), con lo que
puedes ver tú, y con sus vídeos y medios en la página.

En la página de una newsletter:

- **Darme de baja** / **Suscribirme**: solo para esa newsletter; las demás siguen igual. También el enlace
  «Darte de baja de esta newsletter» del correo lleva aquí.
- **Qué secciones recibo**: marca las que te interesan; ninguna marcada, todas.

Nunca te llegará nada que no puedas ver en la intranet, elijas lo que elijas.

## Mis preferencias

*Mis preferencias* lista **las newsletters que recibes** (cada una lleva a su página, donde te das de
baja o eliges secciones) y decide cómo se te avisa.

### Notificaciones

También decides **cómo se te avisa** de lo que se publica para ti:

- **Avisarme en la aplicación**: la campana de la cabecera y las notificaciones del navegador, si las has
  activado. Llegan los avisos nuevos que puedes ver, lo que un editor haya marcado para notificar y el
  recordatorio de lo que debes confirmar, unos dos días antes de la fecha límite.
- **Avisarme por correo**: solo lo que se publica como *importante*.

Un aviso se cierra solo cuando abres el contenido, y el recordatorio cuando lo confirmas. Solo se te avisa de lo
que puedes ver.

## Comentarios y «Me gusta»

Debajo de cada contenido están sus **comentarios**. Escribe en *Tu comentario* y pulsa **Comentar**: lo verá
cualquiera que pueda ver ese contenido, con tu nombre y la hora. Un comentario no se edita ni lo borra quien lo
escribe, y tiene un máximo de 2.000 caracteres.

**Me gusta**, arriba junto a *Añadir a favoritos*, muestra cuántos van. Solo cuenta uno por persona; pulsarlo
otra vez lo retira.

Los editores pueden **cerrar los comentarios** de un contenido (los que hay se siguen viendo, pero no se añaden
más) y **ocultar** un comentario concreto, que deja de verse.

## Encuestas

*Intranet → Encuestas* lista las encuestas abiertas ahora que van dirigidas a ti, con su estado (*Por responder*
o *Respondida*), cuándo cierran y si son anónimas. Abre una, contesta y pulsa **Enviar**: las preguntas
obligatorias hay que responderlas.

- **Se responde una sola vez, y no se puede cambiar después.** Revisa antes de enviar.
- **Anónima**: se anota que has respondido, para que no se responda dos veces, pero tus respuestas no llevan
  nada que te identifique. La página lo dice antes de que respondas.
- **Con tu nombre**: los responsables ven quién ha respondido qué. La página también lo dice.

Fuera de sus fechas, o si no es para ti, la encuesta no aparece y su enlace dice que no está disponible.

## Confirmar lectura

Algunos contenidos (una política de protección de datos, un procedimiento) exigen **confirmar que los has
leído**, a veces antes de una fecha:

1. Aparecen en **⚠️ Pendientes de confirmar** del inicio y en **Pendientes**.
2. Ábrelo y léelo.
3. Pulsa **Confirmar lectura** y acepta. Queda registrado con tu nombre y la versión que has leído.

Si se publica una versión nueva que exige reconfirmar, vuelve a tus pendientes. Si la versión nueva no lo
exige, tu confirmación sigue valiendo.

Algunos contenidos registran además **que los has abierto** (seguimiento de lectura); los editores ven
solo el resumen por hotel y departamento.

## Preguntar al chat

El chat de la consola (el agente `console-agent`) puede consultar la intranet **con tu identidad**: solo
sabe lo que tú puedes leer.

- «¿Qué dice la política de protección de datos sobre las brechas?» — busca los fragmentos más relevantes
  y responde citando el contenido con su enlace.
- «Busca avisos sobre turnos» — busca por palabras.
- «¿Tengo algo pendiente de confirmar?» — lista tus pendientes.
- «¿Quién es el jefe de recepción de Tenerife?» o «¿a quién reporta Ana Pérez?» — busca en el directorio
  y responde con el enlace a la ficha.

Confirmar una lectura **no** se hace desde el chat: abre el enlace y confírmala en el portal. Un contenido
recién publicado tarda unos segundos en estar disponible para el chat.

## Si te mandan un enlace de vista previa

Un editor te puede pedir que revises algo antes de publicarlo mandándote un enlace
`…/intranet/vista-previa/…`. Ábrelo con tu sesión de la intranet (sin sesión no se ve nada): verás el
**borrador** tal como lo verán sus lectores, con la marca **«Borrador — vista previa»**, para quién es y hasta
cuándo vale el enlace. Si el editor lo hizo «como» un perfil (p. ej. recepción de un hotel), verás lo que vería
ese perfil, y te avisa si ese perfil no lo vería en absoluto; si no, verás todos los bloques, y los que solo ve
una parte de la audiencia dicen para quién son.

Es de **solo lectura**: no hay «Me gusta», comentarios, favoritos ni confirmación, y no cuenta como lectura.
El editor sí ve que lo has abierto (quién y cuándo). El enlace deja de valer cuando caduca (7 días por
defecto), cuando el editor lo revoca o cuando el contenido se publica: entonces te dice por qué y, si ya está
publicado, te lleva a él.

## Si un editor te enseña "ver como"

Un editor que usa *Ver como* en el backoffice ve el portal como otro perfil, con una banda amarilla
«👁 Estás viendo la intranet como el perfil …». En ese modo no se registran lecturas ni se puede confirmar.
Ver [Manual del backoffice](/intranet/manual-backoffice/#ver-como).
