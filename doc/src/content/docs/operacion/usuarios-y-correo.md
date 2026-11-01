---
title: Usuarios, Keycloak y correo
description: Cómo llega a Keycloak un usuario creado en la consola, y cómo sale el correo de Keycloak por el relay postfix.
---

## De `users` a Keycloak

`users` es la fuente de verdad de quién es cada persona; Keycloak tiene la copia que autentica. Crear,
editar o borrar un usuario en la consola (*Usuarios*) tiene que llegar allí, o las dos se separan: un
usuario borrado aquí pero no allí podría seguir entrando. Así que cada alta, cambio y baja se propaga.

- **Solo la identidad**: el usuario —el id del propio servicio—, el email, el nombre y si la cuenta está
  activa. Los roles y los permisos **no** se mandan: este servicio sigue siendo el único sitio donde se
  deciden.
- **Por outbox, no con una llamada directa.** Guardar el usuario y llamar a Keycloak no pueden confirmarse
  juntos, así que el caso de uso no llama a Keycloak: escribe el cambio en el outbox compartido
  (`outbox_message`, destino `identity`, de `supporting/messaging`) **en la misma transacción** que el
  usuario. La tabla propia de antes, `identity_outbox`, solo guarda su historial. Un relay vacía el outbox
  cada pocos segundos, entrega cada cambio y lo marca;
  los fallos se reintentan con un backoff creciente y se abandonan a los diez intentos, para que un
  cambio malo no bloquee la cola. Lo entregado se guarda una semana como registro y se purga.
- **Al menos una vez, así que idempotente**: en Keycloak es un *upsert* por nombre de usuario (`POST` si
  no está, `PUT` si está) y borrar uno que ya no está es un éxito.
- **La credencial es amplia, de momento**: el relay entra en la Admin API como el administrador del
  realm, reutilizando el Secret `keycloak-admin`. Solo necesita `manage-users`; la versión limpia es un
  cliente confidencial propio con ese rol, que se dejó fuera porque metería un secreto de cliente en el
  realm versionado.

Un usuario creado así no tiene contraseña: se marca *debe fijar contraseña* y Keycloak le manda el
enlace por correo. Es de mejor esfuerzo: un fallo del correo se registra y nunca falla el alta.

El runbook para comprobarlo paso a paso —crear, editar y borrar un usuario viendo la tabla del outbox,
Keycloak y el log del relay, y un simulacro de fallo— es
[`deploy/RUNBOOK-identity-sync.md`](https://github.com/miguelperezcolom/ec-demo1/blob/master/deploy/RUNBOOK-identity-sync.md).

## El correo

Keycloak (y `communication-service`) mandan correo por un **relay postfix**: un pod pequeño, solo de
envío, que lo recoge y lo entrega a Gmail.

- **Reenvía, no entrega.** Keycloak habla SMTP plano con `postfix:25` dentro del clúster —sin TLS ni
  autenticación, porque ese salto no sale de la red de pods— y postfix lo reenvía a `smtp.gmail.com:587`
  cifrado y autenticado. El único secreto, la *App Password* de Gmail, vive solo en postfix (Secret
  `postfix-relay`), así que la configuración del realm que apunta a él no lleva credenciales y se versiona.
- **La contraseña se pega, no se genera**: `deploy.sh` deja una línea `POSTFIX_RELAY_PASSWORD=`
  comentada en `deploy/.secrets/credentials.env`. Sin ella postfix arranca pero Gmail rechaza el reenvío
  y el correo se encola; el alta y la baja de usuarios no se ven afectadas.
- **Lo que evita el spam es el DNS** del dominio: SPF, DKIM y DMARC de Google para `mateu.io`. El README
  del repositorio tiene los registros.

## Atributos de empleado y directorio

Para la [intranet](/intranet/arquitectura/), cada usuario tiene además **atributos de empleado**: empresa,
país, región, hotel, departamento, puesto e idioma, todos opcionales. Son lo que deciden las audiencias de
la intranet (el idioma solo elige la traducción).

- **Valores de un catálogo cerrado.** *Usuarios → Valores de atributo* es el CRUD del catálogo: cada valor
  tiene un **código** (lo que se guarda en el usuario y con lo que se escriben las audiencias; no cambia
  una vez creado) y una etiqueta. Un hotel dice además su país y región. El formulario del usuario elige
  cada atributo de una lista sobre ese catálogo, así que `region IN (CANARIAS)` no puede fallar por cómo
  se tecleó.
- **País y región salen del hotel.** Si el usuario tiene hotel, se le ponen el país y la región del hotel
  en el catálogo (`EmployeeAttributesResolver`), diga lo que diga el formulario. Sin hotel (servicios
  centrales), se guardan los que se elijan.
- **No van en el token.** Keycloak no los recibe: la propagación a Keycloak sigue siendo solo la identidad.
- **Se publican en Kafka.** Cada alta, cambio o baja escribe también en el outbox un mensaje para el topic
  **`employee-directory`** (`EmployeeUpserted` con el empleado entero, o `EmployeeRemoved`), en la misma
  transacción. El relay elige el transporte por destino (`RoutingOutboxTransport`): `identity` a Keycloak,
  `employee-directory` a Kafka; la clave del directorio va aparte, así que una caída de Keycloak no lo
  retiene. content-hub construye con él su directorio.
- **Copia completa a petición.** Un mensaje en **`employee-directory-requests`** (lo manda content-hub al
  arrancar con el directorio vacío, o desde *Intranet → Visibilidad*) hace que `users` republique a todos
  los usuarios (`EmployeeDirectoryRequestsListener`), una vez por `requestId`.
- **Semilla de demo** (`DemoDirectorySeeder`, `USERS_DEMO_SEED=true`): el catálogo, los permisos
  `content:edit`, `content:publish`, `content:admin`, los roles `content-editor`, `content-publisher` y
  `content-admin`, y unos 30 empleados. Se crean **sin contraseña ni correo** (emails de `example.com`):
  para entrar como uno hay que fijarle contraseña en Keycloak y rellenar su apellido, que el perfil de
  usuario de Keycloak exige. Ver [Operación de la intranet](/intranet/operacion/#datos-de-demo).

## El gRPC de `users`

`users` sirve también `GetAuthInfo` por gRPC en el puerto 9191 (los roles y permisos de un usuario). Lo
llama el **gateway** en cada petición con token (cacheado 5 minutos por token) para poner las cabeceras
`X-User-Roles` y `X-User-Scopes` que leen los backends —la intranet saca de ahí los permisos editoriales—.
**No autentica nada**: está en su Service y en ningún ingress, y no debe exponerse tal como está.
