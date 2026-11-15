"""Fake AWS for the pre-deploy gate (P5 spike): SES and a stand-in Cognito user pool.

SES: the part of the v1 Query API that EmailService uses (SendEmail, SendRawEmail); every mail is
kept in memory, nothing is forwarded anywhere.
Cognito: the admin operations CUMS calls (JSON 1.1, X-Amz-Target), on an in-memory pool keyed by
e-mail, so Pattern N (adminCreateUserSilently) works without the production user pool.
Standard library only.

  POST /                 SES API (form-encoded, Action=SendEmail | SendRawEmail)
                         or Cognito API (X-Amz-Target: AWSCognitoIdentityProviderService.<Op>)
  GET  /_gate/users      the stand-in pool's users, as JSON
  GET  /_gate/messages   every mail received, as JSON (to, cc, bcc, from, subject, text, html)
  GET  /_gate/requests   every SES API request received (action, host header), as JSON
  DELETE /_gate/messages clear mails, requests and the stand-in pool
  GET  /_gate/health     200
"""
import base64
import email
import email.policy
import json
import sys
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs

NS = "http://ses.amazonaws.com/doc/2010-12-01/"
LOCK = threading.Lock()
MESSAGES = []
REQUESTS = []
USERS = {}  # e-mail (lower case) -> {"sub", "attributes": {name: value}, "enabled", "status", "created"}


def members(form, prefix):
    out, i = [], 1
    while f"{prefix}.member.{i}" in form:
        out.append(form[f"{prefix}.member.{i}"])
        i += 1
    return out


def parse_send_email(form):
    return {
        "action": "SendEmail",
        "from": form.get("Source"),
        "to": members(form, "Destination.ToAddresses"),
        "cc": members(form, "Destination.CcAddresses"),
        "bcc": members(form, "Destination.BccAddresses"),
        "replyTo": members(form, "ReplyToAddresses"),
        "subject": form.get("Message.Subject.Data"),
        "text": form.get("Message.Body.Text.Data"),
        "html": form.get("Message.Body.Html.Data"),
        "configurationSet": form.get("ConfigurationSetName"),
    }


def parse_send_raw_email(form):
    raw = base64.b64decode(form.get("RawMessage.Data", ""))
    msg = email.message_from_bytes(raw, policy=email.policy.default)
    text = html = None
    attachments = []
    for part in msg.walk():
        if part.is_multipart():
            continue
        if part.get_content_disposition() == "attachment":
            attachments.append(part.get_filename())
        elif part.get_content_type() == "text/plain" and text is None:
            text = part.get_content()
        elif part.get_content_type() == "text/html" and html is None:
            html = part.get_content()
    destinations = members(form, "Destinations")
    return {
        "action": "SendRawEmail",
        "from": form.get("Source") or msg.get("From"),
        "to": [a.strip() for a in (msg.get("To") or "").split(",") if a.strip()],
        "cc": [a.strip() for a in (msg.get("Cc") or "").split(",") if a.strip()],
        "destinations": destinations,
        "subject": str(msg.get("Subject")),
        "text": text,
        "html": html,
        "attachments": attachments,
        "configurationSet": form.get("ConfigurationSetName"),
        "rawBytes": len(raw),
    }


class Handler(BaseHTTPRequestHandler):
    def _json(self, status, payload):
        body = json.dumps(payload, indent=2, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _xml(self, status, body):
        data = body.encode()
        self.send_response(status)
        self.send_header("Content-Type", "text/xml")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        with LOCK:
            if self.path == "/_gate/messages":
                return self._json(200, MESSAGES)
            if self.path == "/_gate/requests":
                return self._json(200, REQUESTS)
            if self.path == "/_gate/users":
                return self._json(200, USERS)
        if self.path == "/_gate/health":
            return self._json(200, {"status": "UP"})
        self._json(404, {"error": "not found"})

    def do_DELETE(self):
        if self.path == "/_gate/messages":
            with LOCK:
                MESSAGES.clear()
                REQUESTS.clear()
                USERS.clear()
            return self._json(200, {"cleared": True})
        self._json(404, {"error": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length).decode()
        target = self.headers.get("X-Amz-Target") or ""
        if target.startswith("AWSCognitoIdentityProviderService."):
            return self._cognito(target.split(".", 1)[1], json.loads(body or "{}"))
        form = {k: v[0] for k, v in parse_qs(body, keep_blank_values=True).items()}
        action = form.get("Action")
        request_id = str(uuid.uuid4())
        with LOCK:
            REQUESTS.append({"action": action, "host": self.headers.get("Host")})
        if action in ("SendEmail", "SendRawEmail"):
            mail = parse_send_email(form) if action == "SendEmail" else parse_send_raw_email(form)
            mail["messageId"] = f"gate-{uuid.uuid4()}"
            with LOCK:
                MESSAGES.append(mail)
            print(f"[fake-aws] ses {action} to={mail.get('to')} subject={mail.get('subject')!r}", flush=True)
            return self._xml(200, (
                f'<{action}Response xmlns="{NS}"><{action}Result><MessageId>{mail["messageId"]}</MessageId>'
                f'</{action}Result><ResponseMetadata><RequestId>{request_id}</RequestId>'
                f'</ResponseMetadata></{action}Response>'))
        print(f"[fake-aws] unsupported action {action!r}", flush=True)
        return self._xml(400, (
            f'<ErrorResponse xmlns="{NS}"><Error><Type>Sender</Type><Code>InvalidAction</Code>'
            f'<Message>fake SES does not implement {action}</Message></Error>'
            f'<RequestId>{request_id}</RequestId></ErrorResponse>'))

    # --- Cognito stand-in -------------------------------------------------------------------
    def _cognito_reply(self, status, payload):
        data = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/x-amz-json-1.1")
        self.send_header("Content-Length", str(len(data)))
        if status >= 400:
            self.send_header("x-amzn-ErrorType", payload["__type"])
        self.end_headers()
        self.wfile.write(data)

    @staticmethod
    def _user_type(user, attrs_key="Attributes"):
        return {
            "Username": user["sub"],
            attrs_key: [{"Name": k, "Value": v} for k, v in user["attributes"].items()],
            "UserStatus": user["status"],
            "Enabled": user["enabled"],
            "UserCreateDate": user["created"],
            "UserLastModifiedDate": user["created"],
        }

    @staticmethod
    def _find(username):
        key = (username or "").lower()
        if key in USERS:
            return USERS[key]
        return next((u for u in USERS.values() if u["sub"] == username), None)

    def _cognito(self, op, req):
        with LOCK:
            REQUESTS.append({"action": f"cognito:{op}", "host": self.headers.get("Host")})
        print(f"[fake-aws] cognito {op} username={req.get('Username')!r}", flush=True)
        not_found = {"__type": "UserNotFoundException", "message": "User does not exist."}
        with LOCK:
            if op == "AdminCreateUser":
                attrs = {a["Name"]: a["Value"] for a in req.get("UserAttributes", [])}
                email = (attrs.get("email") or req.get("Username") or "").lower()
                if email in USERS:
                    return self._cognito_reply(400, {"__type": "UsernameExistsException",
                                                     "message": "An account with the given email already exists."})
                sub = str(uuid.uuid4())
                attrs["sub"] = sub
                USERS[email] = {"sub": sub, "attributes": attrs, "enabled": True,
                                "status": "FORCE_CHANGE_PASSWORD", "created": time.time()}
                return self._cognito_reply(200, {"User": self._user_type(USERS[email])})
            if op == "ListUsers":
                users = list(USERS.values())
                flt = req.get("Filter") or ""
                if flt.startswith("email"):
                    wanted = flt.split("=", 1)[1].strip().strip('"').lower()
                    users = [u for u in users if u["attributes"].get("email", "").lower() == wanted]
                return self._cognito_reply(200, {"Users": [self._user_type(u) for u in users]})
            user = self._find(req.get("Username"))
            if user is None:
                return self._cognito_reply(400, not_found)
            if op == "AdminGetUser":
                reply = self._user_type(user, "UserAttributes")
                return self._cognito_reply(200, reply)
            if op == "AdminUpdateUserAttributes":
                for a in req.get("UserAttributes", []):
                    user["attributes"][a["Name"]] = a["Value"]
                return self._cognito_reply(200, {})
            if op in ("AdminDisableUser", "AdminEnableUser"):
                user["enabled"] = op == "AdminEnableUser"
                return self._cognito_reply(200, {})
            if op == "AdminSetUserPassword":
                user["status"] = "CONFIRMED" if req.get("Permanent") else "FORCE_CHANGE_PASSWORD"
                return self._cognito_reply(200, {})
            if op == "AdminDeleteUser":
                USERS.pop(user["attributes"].get("email", "").lower(), None)
                return self._cognito_reply(200, {})
        return self._cognito_reply(400, {"__type": "InvalidParameterException",
                                         "message": f"fake Cognito does not implement {op}"})

    def log_message(self, fmt, *args):
        sys.stderr.write("[fake-aws] " + (fmt % args) + "\n")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8025
    print(f"[fake-aws] listening on :{port}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
