---
title: Entorno local
description: Todo el camino CRS → Opera en una máquina, contra el orquestador real y el doble de OHIP — e2e/poc-acl-local.
---

`e2e/poc-acl-local` levanta el camino entero en una máquina, contra el **orquestador real de
EventConductor** y el **doble de Opera** (`opera-mock`). Nada llega a un tenant real de Opera.

```sh
# los contratos primero (instalados) y luego los servicios, con clean
(cd contracts && mvn -q install)
for m in systems/crs/booking systems/erp integration/crs-integration-service integration/mapping-service \
         integration/pms-integration-service systems/pms/opera-mock control-plane/communication-service \
         control-plane/integrations-service integration/customer-mdm-service control-plane/audit-service \
         systems/front-office; do
  (cd $m && mvn -q clean package -DskipTests); done

./e2e/poc-acl-local/infra.sh     # Postgres (una base de datos por servicio), Redpanda, mailpit, el orquestador
./e2e/poc-acl-local/apps.sh      # los servicios; logs en e2e/poc-acl-local/logs/
python3 e2e/poc-acl-local/scenario.py
python3 e2e/poc-acl-local/salesforce.py   # opcional: la ida y vuelta del MDM por Salesforce
```

`infra.sh` importa las definiciones de un clon local de ec-definitions junto a este repositorio
(`EC_DEFINITIONS` para otra ruta, `EC_DEFINITIONS_BRANCH`, `master` por defecto). Las bases de datos y
el broker van en tmpfs: cada ejecución empieza de cero.

## Qué recorre `scenario.py`

1. Los interlocutores sembrados esperan a que se mapee su tipo de perfil.
2. Una reserva de un hotel sin integración se retiene —en `INTEGRATION_INACTIVE:PMI01` y en sus códigos
   sin mapear— y nada llega a Opera.
3. Se registra la integración del hotel y se da de alta puerta a puerta, hasta que la activación libera
   lo retenido; la reserva está en Opera con los códigos traducidos, tarifas fijas por noche, el régimen
   como paquete, el perfil del interlocutor y la versión del CRS en un UDF.
4. Una modificación se escribe encima, en orden, sin aplicar dos veces el depósito.
5. Opera contesta 503 un rato: la escritura se reintenta hasta que entra, con un aviso mientras tanto.
6. Opera rechaza (no queda habitación): el proceso espera en una causa `PMS_REJECTED`.
7. Cancelar libera la habitación; resolver la causa deja pasar la reserva rechazada.
8. La cancelación de una reserva que Opera aún no tiene espera a que llegue.
9. Se avisó a las personas: causas, reintentos, rechazos y altas que necesitan a alguien.
10. Los pasajeros son clientes en el MDM.

`salesforce.py`: dos reservas del mismo huésped escrito distinto son dos clientes provisionales; los dos
se hacen contactos y Salesforce los empareja como posibles duplicados; se fusionan; la fusión vuelve como
`ClienteConsolidado__e` y el perfil de Opera del absorbido toma el código del superviviente.

## Salesforce en local: cuidado con el cupo

El MDM local limpia en una org real de Salesforce si `~/.config/ec-demo1/salesforce.env` (o `SF_ENV`)
tiene las credenciales de su app (`SF_DOMAIN`, `SF_CLIENT_ID`, `SF_CLIENT_SECRET`). Sin el fichero sigue
resolviendo identidades y no manda nada a Salesforce.

:::caution[La org es la misma que la de ec1]
El entorno local y ec1 **comparten la org** y su cupo de 15.000 llamadas al día. Un MDM local olvidado
arrancado agotó el cupo entero. No dejar el entorno local corriendo con Salesforce activo, y usar el
intervalo de sondeo por defecto (15 min; el MDM avisa al arrancar si es de menos de 5).
:::

## El doble de Opera

`opera-mock` implementa las rutas y formatos de las Property APIs que usa el conector, con estado en
memoria, validación, disponibilidad y **fallos inyectables** (503, rechazos, falta de disponibilidad),
que contra un tenant real no se pueden provocar. Sigue las diferencias del tenant real que ha ido
encontrando el conector. Ya no se despliega en ec1: solo existe para esta batería.
