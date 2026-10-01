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

## El gRPC de `users`

`users` sirve también `GetAuthInfo` por gRPC en el puerto 9191 (los roles y permisos de un usuario).
**No autentica nada** y nada del despliegue lo llama: está en su Service y en ningún ingress, y no debe
exponerse tal como está.
