#!/usr/bin/env python3
"""
Regression test for scripts/ci/access-control-check.py (#1155).

Runs the check against a stub HTTP server and a throwaway spec, so it proves the decisions the
deploy gate relies on without touching production:
  - a non-public operation answering 401/403 passes, answering 200 or 415 fails;
  - an allowlisted operation is never probed;
  - a stale allowlist entry fails the run;
  - a write without path parameter and without body is never sent;
  - writes carry a text/plain body and non-existent ids, required query/header params are filled.
Usage: python3 scripts/ci/test-access-control-check.py
"""
import contextlib
import http.server
import importlib.util
import io
import os
import tempfile
import threading
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('acl', os.path.join(HERE, 'access-control-check.py'))
acl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(acl)

SPEC = """
openapi: 3.0.3
info: {title: stub, version: '1'}
paths:
  /things:
    get: {responses: {'200': {description: ok}}}
    post:
      requestBody: {content: {application/json: {schema: {type: object}}}}
      responses: {'201': {description: ok}}
  /things/{id}:
    parameters:
      - {name: id, in: path, required: true, schema: {type: string, format: uuid}}
    delete: {responses: {'204': {description: ok}}}
  /things/report:
    get:
      parameters:
        - {name: scope, in: query, required: true, schema: {type: string, enum: [all, mine]}}
        - {name: X-Attendee-Username, in: header, required: true, schema: {type: string}}
      responses: {'200': {description: ok}}
  /admin/trigger:
    post: {responses: {'202': {description: ok}}}
  /public/info:
    get: {responses: {'200': {description: ok}}}
"""


class Stub(http.server.BaseHTTPRequestHandler):
    statuses = {}
    seen = []

    def _answer(self):
        length = int(self.headers.get('Content-Length') or 0)
        body = self.rfile.read(length) if length else b''
        Stub.seen.append((self.command, self.path, dict(self.headers), body))
        self.send_response(Stub.statuses.get((self.command, self.path.split('?')[0]), 401))
        self.end_headers()

    do_GET = do_POST = do_DELETE = do_PUT = do_PATCH = _answer

    def log_message(self, *args):
        pass


class AccessControlCheckTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.server = http.server.HTTPServer(('127.0.0.1', 0), Stub)
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()
        cls.base = f'http://127.0.0.1:{cls.server.server_port}'
        cls.tmp = tempfile.TemporaryDirectory()
        with open(os.path.join(cls.tmp.name, 'stub.openapi.yml'), 'w') as f:
            f.write(SPEC)

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.tmp.cleanup()

    def setUp(self):
        Stub.statuses, Stub.seen = {}, []

    def run_check(self, allowlist):
        path = os.path.join(self.tmp.name, 'allow.txt')
        with open(path, 'w') as f:
            f.write(allowlist)
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = acl.main(['--base-url', self.base, '--spec-dir', self.tmp.name,
                             '--allowlist', path, '--delay', '0'])
        return code, out.getvalue()

    def test_passes_when_every_non_public_operation_rejects_anonymous(self):
        Stub.statuses[('DELETE', f'/things/{acl.PROBE_UUID}')] = 403
        code, out = self.run_check('GET /public/info  # public\n')
        self.assertEqual(code, 0, out)

    def test_fails_when_non_public_operation_answers_anonymous(self):
        Stub.statuses[('GET', '/things')] = 200
        code, out = self.run_check('GET /public/info\n')
        self.assertEqual(code, 1)
        self.assertIn('FINDING: anonymous GET /things -> 200', out)

    def test_fails_when_write_passes_security_layer(self):
        # 415 means the request got past authentication: the endpoint is open.
        Stub.statuses[('POST', '/things')] = 415
        code, out = self.run_check('GET /public/info\n')
        self.assertEqual(code, 1)
        self.assertIn('FINDING: anonymous POST /things -> 415', out)

    def test_never_probes_allowlisted_operation(self):
        self.run_check('GET /public/info\n')
        self.assertNotIn('/public/info', [p for _, p, _, _ in Stub.seen])

    def test_fails_on_stale_allowlist_entry(self):
        code, out = self.run_check('GET /public/info\nGET /gone\n')
        self.assertEqual(code, 1)
        self.assertIn('STALE allowlist entry, line 2: GET /gone', out)

    def test_offline_allowlist_check_sends_nothing(self):
        path = os.path.join(self.tmp.name, 'allow.txt')
        with open(path, 'w') as f:
            f.write('GET /public/info\nGET /gone\n')
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = acl.main(['--base-url', self.base, '--spec-dir', self.tmp.name,
                             '--allowlist', path, '--check-allowlist'])
        self.assertEqual(code, 1)
        self.assertEqual(Stub.seen, [])

    def test_never_sends_write_without_id_or_body(self):
        code, out = self.run_check('GET /public/info\n')
        self.assertNotIn(('POST', '/admin/trigger'), [(m, p) for m, p, _, _ in Stub.seen])
        self.assertIn('not probed: POST /admin/trigger', out)

    def test_writes_are_inert_and_required_params_filled(self):
        self.run_check('GET /public/info\n')
        by_path = {(m, p.split('?')[0]): (p, h, b) for m, p, h, b in Stub.seen}
        _, headers, body = by_path[('POST', '/things')]
        self.assertEqual(headers.get('Content-Type'), 'text/plain')
        self.assertEqual(body, b'access-control-probe')
        self.assertIn(('DELETE', f'/things/{acl.PROBE_UUID}'), by_path)
        report_path, report_headers, _ = by_path[('GET', '/things/report')]
        self.assertIn('scope=all', report_path)
        self.assertEqual(report_headers.get('X-Attendee-Username'), acl.PROBE_ID)
        self.assertNotIn('Authorization', report_headers)


if __name__ == '__main__':
    unittest.main(verbosity=2)
