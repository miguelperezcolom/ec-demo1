---
title: Manual del portal
description: La intranet para el empleado — inicio, noticias, avisos y documentos, secciones, búsqueda, favoritos, confirmar lectura y preguntar al chat.
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

## Inicio

*Intranet → Inicio* te saluda por tu nombre y reúne:

- **⚠️ Pendientes de confirmar**, arriba y solo si los hay: los contenidos que debes confirmar haber leído.
- **📢 Avisos**: los cinco últimos avisos.
- **📰 Últimas noticias**: las ocho últimas noticias y páginas.

Cada título abre el contenido.

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

*Buscar* busca en todo lo que puedes ver: título, resumen y cuerpo. Las palabras valen **por su
principio**: «vacac» encuentra «vacaciones», «turn» encuentra «turnos». Sin texto, muestra lo último.

Los resultados, las páginas y el total solo cuentan lo que puedes ver: nunca verás «20 resultados» de los
que te faltan la mitad.

## Favoritos

En un contenido, **Favorito** (en la barra de herramientas) lo añade a **Mis favoritos**; pulsarlo otra vez
lo quita. Si un favorito deja de ser para ti (se retira, caduca o cambia su audiencia), desaparece de la
lista; si vuelve a serlo, reaparece.

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

Confirmar una lectura **no** se hace desde el chat: abre el enlace y confírmala en el portal. Un contenido
recién publicado tarda unos segundos en estar disponible para el chat.

## Si un editor te enseña "ver como"

Un editor que usa *Ver como* en el backoffice ve el portal como otro perfil, con una banda amarilla
«👁 Estás viendo la intranet como el perfil …». En ese modo no se registran lecturas ni se puede confirmar.
Ver [Manual del backoffice](/intranet/manual-backoffice/#ver-como).
