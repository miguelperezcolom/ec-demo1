package io.mateu.ecdemo1.frontoffice.application;

import io.mateu.ecdemo1.frontoffice.domain.guest.Guest;
import io.mateu.ecdemo1.frontoffice.domain.guest.GuestRepository;
import io.mateu.ecdemo1.frontoffice.domain.guest.KardexChange;
import io.mateu.ecdemo1.frontoffice.domain.registration.PaxRegistrationData;
import io.mateu.ecdemo1.integration.model.registration.RegistrationRuleChanged.Field;
import io.mateu.ecdemo1.frontoffice.domain.stay.StayRepository;
import io.mateu.ecdemo1.frontoffice.domain.stay.Companion;
import io.mateu.ecdemo1.frontoffice.domain.stay.WalkIns;
import io.mateu.ecdemo1.frontoffice.infra.mdm.Kardex;
import io.mateu.ecdemo1.frontoffice.infra.outbox.CommandOutbox;
import io.mateu.ecdemo1.frontoffice.infra.scanner.DemoDocuments;
import io.mateu.ecdemo1.frontoffice.infra.scanner.DemoScanner;
import io.mateu.ecdemo1.integration.model.command.CustomerCommand;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The kárdex of each pax of a stay, as the desk edits it: pax 1 is the stay's guest, the others its
 * companions. What the chain's master keeps of the guest — name, document, contact — is proposed to it
 * as a pending change; a document the desk scans goes to the chain's MDM as trusted data. Either one as
 * a command in the outbox, in the same transaction as the edit: the desk never waits for the MDM.
 */
@Service
public class KardexService {

  final StayRepository stays;
  final GuestRepository guests;
  final Kardex kardex;
  final DemoScanner scanner;
  final WalkIns walkIns;
  final CommandOutbox outbox;
  final String hotel;
  final TransactionTemplate transaction;
  final IncompleteCheckIns incomplete;
  final StayAudit audit;

  public KardexService(StayRepository stays, GuestRepository guests, Kardex kardex, DemoScanner scanner,
                       WalkIns walkIns, CommandOutbox outbox, @Value("${frontoffice.hotel:MRU01}") String hotel,
                       PlatformTransactionManager transactions, IncompleteCheckIns incomplete, StayAudit audit) {
    this.stays = stays;
    this.guests = guests;
    this.kardex = kardex;
    this.scanner = scanner;
    this.walkIns = walkIns;
    this.outbox = outbox;
    this.hotel = hotel;
    this.transaction = new TransactionTemplate(transactions);
    this.incomplete = incomplete;
    this.audit = audit;
  }

  /**
   * The desk's scanner read the pax's document: the identity is seen, with that document, and the
   * document — number, name, birth date, nationality — goes to the chain's MDM, with the reservation
   * and the pax it belongs to. Holder and companions alike.
   */
  public DemoDocuments.Scanned scanned(String stayId, int pax) {
    return audit.run("Document scanned", stayId, null, StayAudit.params("pax", pax), () -> scan(stayId, pax, false),
        d -> "Documento " + d.documentType() + " leído y enviado al maestro de clientes");
  }

  /**
   * As {@link #scanned}, the demo scanner reading a passport the chain has never seen of the same
   * person (same name and birth date, another number): the demo's returning customer with a new document.
   */
  public DemoDocuments.Scanned scannedNewPassport(String stayId, int pax) {
    return audit.run("Document scanned", stayId, null, StayAudit.params("pax", pax, "variant", "new passport"),
        () -> scan(stayId, pax, true),
        d -> "Documento " + d.documentType() + " leído y enviado al maestro de clientes");
  }

  DemoDocuments.Scanned scan(String stayId, int pax, boolean newPassport) {
    var stay = stay(stayId);
    var guest = pax <= 1 ? guestOf(stayId) : null;
    var companion = pax <= 1 ? null : stay.companionAt(pax);
    var name = pax <= 1 ? guest.name() : companion == null ? Companion.pending(pax).name() : companion.name();
    var current = pax <= 1 ? guest.document() : companion == null ? null : companion.document();
    var customerId = pax <= 1 ? guest.id() : companion == null ? null : companion.companionId();
    var locator = locatorOf(stayId);
    // The scanner reads before the transaction: it may ask the booking and the MDM, and nobody waits on a lock for it.
    var who = new DemoScanner.Pax(locator, pax, name, current, customerId, stay.checkIn());
    var document = newPassport ? scanner.scanNewPassport(who) : scanner.scan(who);
    transaction.executeWithoutResult(status -> {
      if (pax <= 1) {
        guests.save(guestOf(stayId).scanned(document.documentNumber()));
      } else {
        stays.save(stay(stayId).scanCompanion(pax, document.documentNumber()));
      }
      var commandId = "SCAN-" + UUID.randomUUID();
      var command = new CustomerCommand.RecordScannedIdentity(commandId, hotel, locator, stayId, pax,
          customerId != null && customerId.startsWith("C-") ? customerId : null, document.firstName(),
          document.lastName(), document.documentType(), document.documentNumber(), document.birthDate(),
          document.nationality(), "front office " + hotel + " · " + stayId + " pax " + pax,
          document.issuingCountry(), document.expiry(), null);
      outbox.append(CommandOutbox.CUSTOMER_COMMANDS, command.key(), command);
      // what the document says is registration data too: the rules may ask for it
      if (registrationData != null) {
        var read = new java.util.EnumMap<Field, String>(Field.class);
        read.put(Field.DOCUMENT_TYPE, document.documentType());
        read.put(Field.DOCUMENT_NUMBER, document.documentNumber());
        read.put(Field.NATIONALITY, document.nationality());
        read.put(Field.BIRTH_DATE, document.birthDate() == null ? null : document.birthDate().toString());
        read.put(Field.DOCUMENT_ISSUING_COUNTRY, document.issuingCountry());
        read.put(Field.DOCUMENT_EXPIRY, document.expiry() == null ? null : document.expiry().toString());
        read.values().removeIf(v -> v == null || v.isBlank());
        registrationData.put(stayId, pax, read);
      }
      // the document may be the last step a forced check-in owed
      incomplete.settle(stayId, null);
    });
    // who the pax is in the chain: asked of the MDM once the scan is saved — a read the check-in never
    // waits long for, and never fails by
    if (recognition != null) {
      recognition.afterScan(stayId, pax, document);
    }
    return document;
  }

  /** Who a scanned pax is in the chain; none in a test that does not wire it. */
  Recognition recognition;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  public void setRecognition(Recognition recognition) {
    this.recognition = recognition;
  }

  /** The CRS's locator of a stay: its id, or — for a walk-in — the one the CRS gave it (the stay's own until then). */
  String locatorOf(String stayId) {
    return walkIns.of(stayId).map(w -> w.locator() == null ? stayId : w.locator()).orElse(stayId);
  }

  /** The pax registered — or corrected — by hand: document, name and contact. */
  public void registered(String stayId, int pax, String document, String name, String email, String phone) {
    // what was edited, not the values: the audit trail says who changed a guest's data, the kárdex keeps it
    var fields = new java.util.ArrayList<String>();
    if (document != null && !document.isBlank()) fields.add("documento");
    if (name != null && !name.isBlank()) fields.add("nombre");
    if (email != null && !email.isBlank()) fields.add("email");
    if (phone != null && !phone.isBlank()) fields.add("teléfono");
    audit.run("Kardex edit", stayId, null, StayAudit.params("pax", pax, "fields", fields), () -> {
      register(stayId, pax, document, name, email, phone);
      return true;
    }, ok -> "Kárdex del pax " + pax + " guardado; el cambio va al maestro de clientes");
  }

  void register(String stayId, int pax, String document, String name, String email, String phone) {
    if (pax <= 1) {
      guestEdited(stayId, g -> g.registeredAtDesk(document, name, email, phone));
    } else {
      transaction.executeWithoutResult(status ->
          stays.save(stay(stayId).registerCompanionAtDesk(pax, document, name, email, phone)));
    }
    // the document may be the last step a forced check-in owed
    transaction.executeWithoutResult(status -> incomplete.settle(stayId, null));
  }

  /** Where each pax's registration data is kept; none in a test that does not wire it. */
  PaxRegistrationData registrationData;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  public void setRegistrationData(PaxRegistrationData registrationData) {
    this.registrationData = registrationData;
  }

  /**
   * The pax's registration data — nationality, birth date, address… — as the desk wrote it: what the
   * destination's registration rules ask for. Audited by the fields written, not their values.
   */
  public void registrationData(String stayId, int pax, java.util.Map<Field, String> values) {
    var fields = values.keySet().stream().map(Enum::name).sorted().toList();
    audit.run("Registration data", stayId, null, StayAudit.params("pax", pax, "fields", fields), () -> {
      transaction.executeWithoutResult(status -> {
        stay(stayId);
        if (registrationData != null) {
          registrationData.put(stayId, pax, values);
        }
        // the data may be the last step a forced check-in owed
        incomplete.settle(stayId, null);
      });
      return true;
    }, ok -> "Datos de registro del pax " + pax + " guardados");
  }

  /** Where each pax's desk kárdex is kept; none in a test that does not wire it. */
  io.mateu.ecdemo1.frontoffice.domain.guest.PaxKardexes paxKardexes;

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  public void setPaxKardexes(io.mateu.ecdemo1.frontoffice.domain.guest.PaxKardexes paxKardexes) {
    this.paxKardexes = paxKardexes;
  }

  /** The desk's kárdex of a pax, if it was filled in; empty: a provisional kárdex. */
  public Optional<io.mateu.ecdemo1.frontoffice.domain.guest.PaxKardexes.PaxKardex> kardexOf(String stayId, int pax) {
    return paxKardexes == null ? Optional.empty() : paxKardexes.of(stayId, pax);
  }

  /**
   * The kárdex the desk filled in with the guest: kept, and — with the registration data the desk just
   * wrote (sex, address, birth place…) — sent to the chain's MDM as the guest's own declaration
   * ({@code RecordKardex}), in the same transaction. Audited by the pax, not the values.
   */
  public void kardexFilled(String stayId, int pax, io.mateu.ecdemo1.frontoffice.domain.guest.PaxKardexes.PaxKardex k,
                           String by) {
    audit.run("Kardex filled", stayId, null, StayAudit.params("pax", pax), () -> {
      transaction.executeWithoutResult(status -> {
        var stay = stay(stayId);
        var filled = new io.mateu.ecdemo1.frontoffice.domain.guest.PaxKardexes.PaxKardex(stayId, pax, k.firstName(),
            k.lastName(), k.riuClass(), k.documentIssueDate(), k.language(), k.province(), k.fax(),
            k.marketingConsent(), java.time.Instant.now(), by);
        if (paxKardexes != null) {
          paxKardexes.save(filled);
        }
        var data = registrationData == null ? java.util.Map.<Field, String>of() : registrationData.of(stayId, pax);
        var customerId = pax <= 1 ? stay.guestId()
            : java.util.Optional.ofNullable(stay.companionAt(pax)).map(Companion::companionId).orElse(null);
        var document = data.get(Field.DOCUMENT_NUMBER) != null ? data.get(Field.DOCUMENT_NUMBER)
            : pax <= 1 ? guests.findById(stay.guestId()).map(g -> g.document()).orElse(null)
            : java.util.Optional.ofNullable(stay.companionAt(pax)).map(Companion::document).orElse(null);
        var command = new CustomerCommand.RecordKardex("KARDEX-" + UUID.randomUUID(), hotel, locatorOf(stayId), stayId,
            pax, customerId != null && customerId.startsWith("C-") ? customerId : null, k.firstName(), k.lastName(),
            data.get(Field.SEX), date(data.get(Field.BIRTH_DATE)), data.get(Field.BIRTH_PLACE), data.get(Field.NATIONALITY),
            k.language(), data.get(Field.ADDRESS), data.get(Field.CITY), data.get(Field.POSTAL_CODE), k.province(),
            data.get(Field.COUNTRY_OF_RESIDENCE), k.fax(), data.get(Field.DOCUMENT_TYPE), document,
            k.documentIssueDate(), date(data.get(Field.DOCUMENT_EXPIRY)), k.riuClass(), k.marketingConsent(), pax > 1,
            "front office " + hotel + " · " + stayId + " pax " + pax);
        outbox.append(CommandOutbox.CUSTOMER_COMMANDS, command.key(), command);
      });
      return true;
    }, ok -> "Kárdex del pax " + pax + " completado; va al maestro de clientes");
  }

  static java.time.LocalDate date(String s) {
    try {
      return s == null || s.isBlank() ? null : java.time.LocalDate.parse(s.trim());
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** The pax's contact, as the desk took it down. */
  public void contactUpdated(String stayId, int pax, String email, String phone) {
    audit.run("Contact updated", stayId, null, StayAudit.params("pax", pax), () -> {
      updateContact(stayId, pax, email, phone);
      return true;
    }, ok -> "Contacto del pax " + pax + " actualizado");
  }

  void updateContact(String stayId, int pax, String email, String phone) {
    if (pax <= 1) {
      guestEdited(stayId, g -> g.updateContact(email, phone));
      return;
    }
    transaction.executeWithoutResult(status ->
        stays.save(stay(stayId).updateCompanionContact(pax, email, phone)));
  }

  /** The guest's last change to the master's data, if the desk ever made one. */
  public Optional<KardexChange> changeOf(String guestId) {
    return kardex.of(guestId);
  }

  void guestEdited(String stayId, UnaryOperator<Guest> edit) {
    transaction.executeWithoutResult(status -> {
      var before = guestOf(stayId);
      var after = guests.save(edit.apply(before));
      kardex.record(before, after);
    });
  }

  Guest guestOf(String stayId) {
    var stay = stay(stayId);
    return guests.findById(stay.guestId()).orElseThrow(() -> new NoSuchElementException("No guest " + stay.guestId()));
  }

  io.mateu.ecdemo1.frontoffice.domain.stay.Stay stay(String stayId) {
    return stays.findById(stayId).orElseThrow(() -> new NoSuchElementException("Reserva " + stayId + " no encontrada"));
  }
}
