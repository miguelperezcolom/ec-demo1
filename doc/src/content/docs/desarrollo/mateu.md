---
title: Mateu
description: Cómo se usa Mateu en ec-demo1 — pantallas federadas, dos renderers, subir de versión y los comportamientos que hay que conocer.
---

[Mateu](https://mateu.io) es el framework de interfaces de todas las pantallas de ec-demo1: cada servicio
describe sus pantallas en Java (view models con anotaciones) y el renderer las dibuja. La versión es la
misma en todos los módulos (`mateu.version`, hoy `3.0-alpha.373`).

## Cómo se usa aquí

- **Cada servicio declara un `@UI`** en su ruta (`/_mapping`, `/_integrations`…) con sus menús, y la
  consola lo monta con un `RemoteMenu`. La etiqueta del `RemoteMenu` tiene que ser la de la sección del
  pod remoto (su único `@Menu` de arriba, en mayúscula): una sección por servicio.
- **Dos renderers, el mismo servidor.** La consola se compila con `io.mateu:vaadin-lit` (por defecto) o
  con `io.mateu:redwood` (`-Predwood`). Un `RemoteMenu` transporta UIDL, así que las pantallas de los
  servicios no saben quién las pinta.
- **`ui-commons`** tiene lo que comparten: el paginado, el widget del usuario y la bandeja de la
  cabecera, las tablas de «otros sistemas», la base de los CRUD de catálogo y los KPI de consumo.
- **Keycloak** va en la anotación `@KeycloakSecured` de la consola, compilada en la página de arranque.

## Subir de versión

1. Mirar la última publicada en Maven Central
   (`https://repo1.maven.org/maven2/io/mateu/core/maven-metadata.xml`, `<latest>`; el índice de
   search.maven.org va con meses de retraso).
2. Cambiar `mateu.version` en **todos** los módulos a la vez.
3. Reconstruir todas las imágenes con `clean` (el procesador de anotaciones no se vuelve a ejecutar si
   solo cambió la versión) y desplegar todo.
4. Repasar en un navegador los formularios: los comportamientos de abajo cambian entre versiones.

## Lo que hay que saber

- Una `@Action` en un CRUD necesita `@Toolbar` para pintarse, y debe devolver
  `List.of(new Message(...), new State(this))`, no un `String`. Una acción de `@Toolbar` también aparece
  en el modo vista, donde los campos de edición están vacíos.
- Las casillas (`@Stereotype(checkbox)`) leen las opciones de `OptionsSupplier`, no de `@Lookup`; un
  `@Lookup` normal (combo) funciona tal cual.
- Un formulario en un `Dialog` va como `new EmbeddedView(form)`, con `@Getter` en sus campos de estado,
  título con `TitleSupplier` y botones con `ButtonsSupplier`.
- `VisibilitySupplier` decide qué acciones y campos se ven según el estado (confirmar solo una reserva
  pendiente, p. ej.).
- Un `Listing` que no es `Navigable` necesita `OnLoadTrigger("search")`.
- Las validaciones se emiten para todos los campos, también los ocultos.

La lista completa, con los síntomas, está en
[Problemas conocidos](/operacion/problemas-conocidos/#mateu).
