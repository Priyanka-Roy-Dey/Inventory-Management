"""Tests for the alert recorder Lambda (INV-18), whose code is inline in infra/alerts.yaml.

Run from the repository root: python3 -m unittest discover -s infra/tests
"""
import json
import pathlib
import textwrap
import types
import unittest

TEMPLATE = pathlib.Path(__file__).resolve().parents[1] / "alerts.yaml"


def load_handler():
    """Loads the code under `ZipFile: |` in the template, so the test runs exactly what is deployed."""
    lines = TEMPLATE.read_text().splitlines()
    start = next(i for i, line in enumerate(lines) if line.strip() == "ZipFile: |") + 1
    indent = len(lines[start]) - len(lines[start].lstrip())
    body = []
    for line in lines[start:]:
        if line.strip() and len(line) - len(line.lstrip()) < indent:
            break
        body.append(line)
    module = types.ModuleType("alert_recorder")
    exec(textwrap.dedent("\n".join(body)), module.__dict__)
    return module


class ConditionalCheckFailed(Exception):
    """Shaped like botocore's ClientError for a failed condition."""

    def __init__(self):
        super().__init__("The conditional request failed")
        self.response = {"Error": {"Code": "ConditionalCheckFailedException"}}


class Throttled(Exception):
    def __init__(self):
        super().__init__("Rate exceeded")
        self.response = {"Error": {"Code": "ProvisionedThroughputExceededException"}}


class FakeTable:
    """Keeps items by alertId and honours attribute_not_exists(alertId), like DynamoDB."""

    def __init__(self, fail_for=()):
        self.items = {}
        self.fail_for = set(fail_for)

    def put_item(self, Item, ConditionExpression):
        assert ConditionExpression == "attribute_not_exists(alertId)"
        if Item["alertId"] in self.fail_for:
            raise Throttled()
        if Item["alertId"] in self.items:
            raise ConditionalCheckFailed()
        self.items[Item["alertId"]] = Item


def message(message_id, event_id, sku="PAP-A4"):
    body = {"eventId": event_id, "itemId": 7, "sku": sku, "name": "A4 paper", "quantity": 9, "reorderLevel": 10,
            "cause": "STOCK_MOVEMENT", "occurredAt": "2026-11-13T05:00:00Z"}
    return {"messageId": message_id, "body": json.dumps(body)}


class AlertRecorderTests(unittest.TestCase):

    def setUp(self):
        self.recorder = load_handler()
        self.table = FakeTable()
        self.recorder.TABLE = self.table

    def test_records_each_alert_by_its_event_id(self):
        result = self.recorder.handler({"Records": [message("m1", "e1"), message("m2", "e2", "CBL-USB")]}, None)

        self.assertEqual(result, {"batchItemFailures": []})
        self.assertEqual(set(self.table.items), {"e1", "e2"})
        alert = self.table.items["e1"]
        self.assertEqual(alert["sku"], "PAP-A4")
        self.assertEqual(alert["itemId"], 7)
        self.assertEqual(alert["quantity"], 9)
        self.assertEqual(alert["reorderLevel"], 10)
        self.assertEqual(alert["occurredAt"], "2026-11-13T05:00:00Z")
        self.assertIn("recordedAt", alert)

    def test_a_duplicate_message_creates_no_second_record(self):
        self.recorder.handler({"Records": [message("m1", "e1")]}, None)
        first = dict(self.table.items["e1"])

        result = self.recorder.handler({"Records": [message("m9", "e1")]}, None)

        self.assertEqual(result, {"batchItemFailures": []})
        self.assertEqual(len(self.table.items), 1)
        self.assertEqual(self.table.items["e1"], first)

    def test_only_the_failed_message_is_returned_for_a_retry(self):
        self.table.fail_for = {"e2"}

        result = self.recorder.handler({"Records": [message("m1", "e1"), message("m2", "e2")]}, None)

        self.assertEqual(result, {"batchItemFailures": [{"itemIdentifier": "m2"}]})
        self.assertIn("e1", self.table.items)

    def test_a_malformed_message_is_returned_so_it_ends_in_the_dead_letter_queue(self):
        result = self.recorder.handler({"Records": [{"messageId": "m1", "body": "not json"},
                                                     {"messageId": "m2", "body": json.dumps({"sku": "X"})}]}, None)

        self.assertEqual(result, {"batchItemFailures": [{"itemIdentifier": "m1"}, {"itemIdentifier": "m2"}]})
        self.assertEqual(self.table.items, {})


if __name__ == "__main__":
    unittest.main()
