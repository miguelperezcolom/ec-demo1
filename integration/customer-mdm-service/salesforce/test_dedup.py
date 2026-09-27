"""python3 -m unittest test_dedup — grouping and survivorship, no network."""
import unittest
from xml.etree import ElementTree

from dedup import group, merge_request, merge_result, normalize, plan, batches


def contact(i, first, last, created, **fields):
    return {"Id": f"003{i:015d}", "FirstName": first, "LastName": last, "Name": f"{first} {last}",
            "CreatedDate": created, **fields}


class Dedup(unittest.TestCase):
    def test_normalize(self):
        self.assertEqual(normalize("  Lucía   FERNÁNDEZ "), "lucia fernandez")
        self.assertEqual(normalize(None), "")

    def test_six_empty_marcos_ruiz_are_one_auto_group(self):
        marcos = [contact(i, "Marcos", "Ruiz", f"2026-09-0{i}T10:00:00.000+0000") for i in range(1, 7)]
        groups, shared = group(marcos)
        self.assertEqual([(g["key"], g["class"], len(g["contacts"])) for g in groups], [("marcos ruiz", "AUTO", 6)])
        p = plan(groups[0])
        self.assertEqual(p["master"], marcos[0]["Id"])  # tie on fields: the oldest
        self.assertEqual(len(p["absorbed"]), 5)
        self.assertEqual([len(b) for b in batches(p["absorbed"])], [2, 2, 1])
        self.assertEqual(p["fills"], {})
        self.assertEqual(shared, [])

    def test_accents_group_but_different_emails_need_review(self):
        lucias = [contact(1, "Lucia", "Fernandez", "2026-09-01T10:00:00.000+0000", Email="lucia.fernadez@mail.com"),
                  contact(2, "Lucía", "Fernández", "2026-09-02T10:00:00.000+0000", Email="lucia.fernandez@mail.com")]
        groups, _ = group(lucias)
        self.assertEqual(len(groups), 1)
        self.assertEqual(groups[0]["class"], "REVISAR")
        self.assertIn("Email", groups[0]["reasons"][0])
        self.assertEqual(group(lucias, include={"lucia fernandez"})[0][0]["class"], "AUTO")
        self.assertEqual(group(lucias, exclude={"lucia fernandez"})[0], [])

    def test_two_people_with_one_phone_are_informative_not_a_group(self):
        people = [contact(1, "Ana", "Gil", "2026-09-01T10:00:00.000+0000", Phone="+34 600 11 22 33"),
                  contact(2, "Pedro", "Sanz", "2026-09-02T10:00:00.000+0000", MobilePhone="600-112-233")]
        people[1]["MobilePhone"] = "+34600112233"
        groups, shared = group(people)
        self.assertEqual(groups, [])
        self.assertEqual([(s["field"], s["value"], len(s["contacts"])) for s in shared], [("Teléfono", "34600112233", 2)])

    def test_empties_do_not_conflict_and_fill_from_the_most_recent(self):
        cs = [contact(1, "Eva", "Mora", "2026-09-01T10:00:00.000+0000", Email="eva@x.com", Title="CEO"),
              contact(2, "Eva", "Mora", "2026-09-02T10:00:00.000+0000", Phone="611", MailingCity="Palma"),
              contact(3, "eva", "mora", "2026-09-03T10:00:00.000+0000", Phone="(611)", MailingCity="Madrid", Title="CTO")]
        groups, _ = group(cs)
        self.assertEqual(groups[0]["class"], "AUTO")
        p = plan(groups[0], {cs[2]["Id"]: {"Cases": 2}, cs[1]["Id"]: {"Tasks": 1}})
        self.assertEqual(p["master"], cs[2]["Id"])  # most fields informed
        self.assertEqual(p["fills"], {"Email": {"value": "eva@x.com", "from": cs[0]["Id"]}})  # Title kept: CTO
        self.assertEqual(p["children"], {"Tasks": 1})

    def test_different_accounts_need_review(self):
        cs = [contact(1, "Jon", "Paz", "2026-09-01T10:00:00.000+0000", AccountId="001A", Account={"Name": "Acme"}),
              contact(2, "Jon", "Paz", "2026-09-02T10:00:00.000+0000", AccountId="001B", Account={"Name": "Globex"})]
        self.assertEqual(group(cs)[0][0]["reasons"], ["AccountId: Acme / Globex"])

    def test_merge_xml(self):
        body = merge_request("003M", ["003A", "003B"], {"Email": "a&b@x.com"})
        self.assertIn("<sobj:Email>a&amp;b@x.com</sobj:Email>", body)
        self.assertEqual(body.count("<urn:recordToMergeIds>"), 2)
        ok = ElementTree.fromstring(
            '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns="urn:partner.soap.sforce.com">'
            '<soapenv:Body><mergeResponse><result><id>003M</id><mergedRecordIds>003A</mergedRecordIds>'
            '<success>true</success><updatedRelatedIds>500X</updatedRelatedIds></result></mergeResponse></soapenv:Body></soapenv:Envelope>')
        self.assertEqual(merge_result(ok), {"success": True, "mergedRecordIds": ["003A"], "updatedRelatedIds": ["500X"], "errors": []})


if __name__ == "__main__":
    unittest.main()
