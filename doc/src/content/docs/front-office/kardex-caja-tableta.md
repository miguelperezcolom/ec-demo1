---
title: Kárdex, caja y tableta
description: El kárdex completo del huésped y su viaje al MDM y a Salesforce, la caja de la estancia (cobros, anticipos, recibos, límite y crédito cancelado) y el check-in en la tableta del lobby, simulado.
---

Tres piezas que acercan el front office al día a día de una recepción con OPERA. Las tres reutilizan los
casos de uso que ya había: lo que se hace aquí llega al MDM, a la caja o a las operaciones del check-in
igual que si lo hiciera recepción a mano.

## El kárdex completo

En el paso **Identidad** del check-in (y en la ficha del huésped), el editor del documento recoge todo el
kárdex:

| Bloque | Campos |
| :-- | :-- |
| Identidad | Nombre y **apellidos** por separado, documento, tipo, país y **fecha de expedición**, caducidad, nacionalidad, fecha y lugar de nacimiento, sexo |
| Contacto | Email, teléfono, **fax** |
| Dirección | Dirección, código postal, población, **provincia**, país de residencia |
| Kárdex | **Idioma**, **nº Riu Class**, **acepta publicidad** |
| Solo lectura | **Titular o acompañante**, y si el kárdex es **provisional** (solo lo que trajeron la reserva y el escaneo) o **completado en recepción** |

Lo que exige la normativa del destino sigue en los datos de registro (`pax_registration_data`); lo demás,
en `pax_kardex`. Al guardar, además del cambio de contacto de siempre (`ProposeChange`, que decide
Salesforce), sale **`RecordKardex`** al MDM con todo lo que el huésped ha declarado.

**En el MDM** (`Kardexes`): el kárdex es la palabra del huésped en el mostrador, así que **lo último manda**
en sexo, idioma, dirección, lugar de nacimiento, fax, Riu Class y publicidad. La fecha de nacimiento y la
nacionalidad solo rellenan huecos (un documento escaneado es mejor prueba), y el documento gana su fecha
de expedición y su caducidad. El número Riu Class queda además como referencia cruzada `RIU_CLASS`. Viaja
en el `GoldenRecord` (`profile`) a los hoteles y a Salesforce: dirección postal estándar
(`MailingStreet`, `MailingCity`, `MailingPostalCode`, `MailingCountryCode`), `Fax`, `HasOptedOutOfEmail`
(lo contrario de «acepta publicidad») y los campos propios `Sexo__c`, `Idioma__c`,
`Lugar_Nacimiento__c`, `Provincia__c` y `Riu_Class__c`, en la sección *Kárdex (MDM)*. Solo se envía lo
que el MDM tiene: un cliente sin kárdex no vacía lo que un steward escribió en Salesforce.

:::note
La org tiene activados los picklists de país y provincia: el país va como código ISO-2
(`MailingCountryCode`, un `ESP` se convierte en `ES`) y la provincia en un campo propio, porque el
picklist de provincias rechazaría el texto libre.
:::

Opera no recibe el kárdex de recepción (tampoco antes): sus perfiles salen de la reserva del CRS.

## La caja de la estancia

Botón **Caja** en la reserva (por llegar y en casa): la cuenta de la estancia en la moneda del hotel
(`frontoffice.currency`, EUR por defecto).

- **Saldo pendiente** = cargos del folio − lo cobrado. Con el desglose: cargos, cobrado y anticipos.
- **Cobrar**, como *cobro* o como **anticipo**, con estas formas de pago:
  - **Efectivo**, **transferencia** o **manual**: queda cobrado al momento.
  - **Datáfono**: simulado. Aprueba con un número de autorización, salvo un importe acabado en **,99**,
    que es la tarjeta denegada de la demo.
  - **Link de pago por email**: queda *pendiente* hasta que el huésped paga en `/pagar/<token>`, una
    página sin login (en la demo, el botón «Pagar»; no hay pasarela).
- Cada cobro tiene su **recibo** en PDF (`/caja/recibo/…`), con numeración propia. Se puede **devolver**
  un cobro o **anular** un link.
- **Imprimir proforma del folio** en cualquier momento de la estancia: cargos, cobros y saldo
  (`/caja/proforma/…`). Los enlaces van firmados y caducan, como el de la factura.
- **Límite de crédito**: por defecto, la preautorización del check-in. Se puede fijar otro, y la caja
  avisa cuando se supera.
- **Crédito cancelado**: deja el límite a cero. Desde ese momento **no se carga nada a la habitación**
  («Añadir cargo» lo rechaza), hasta que se **restablece**.

En el **check-out** se cobra solo el **saldo pendiente**, ya descontados los anticipos y cobros de la
estancia, y queda como un cobro más con su recibo. Si el datáfono deniega, el check-out no sigue.

Los cobros **no se postean aún en Opera** durante la estancia: Opera liquida su folio en el check-out,
como antes. Postearlos en vivo pide un proceso nuevo en el motor (`registrar-cobro`) y su tarea en
`pms-integration-service`.

## Check-in en la tableta (Civitfun, simulado)

Botón **Check-in en tableta** en una reserva que llega. Como el escáner de documentos, **no hay tableta**:
la simulación hace lo que haría el huésped en ella, y un diálogo de progreso cuenta cada paso:

1. **Escanea su documento**, que va al MDM como un escaneo de recepción.
2. **Completa sus datos**: email, dirección de su país, idioma y publicidad. Es su kárdex, que va al MDM.
3. **Elige habitación**: uno de cada tres coge el **upgrade** a la suite 1401 si está libre; los demás se
   quedan con la suya.
4. **Garantiza la estancia**: preautoriza su tarjeta o, uno de cada cuatro, **la paga** como anticipo
   con su recibo.
5. **Firma el registro**.

Las respuestas salen de la reserva: la misma reserva da siempre las mismas. Al acabar, la reserva
muestra hechas la documentación, la firma y el cobro, y recepción solo entrega la llave.
