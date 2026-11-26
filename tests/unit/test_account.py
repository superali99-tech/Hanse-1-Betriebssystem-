from novaos.account import verify


def test_valid_email_and_provider():
    assert verify.valid_email("max.muster@web.de")
    assert not verify.valid_email("max@") and not verify.valid_email("a b@c.de") and not verify.valid_email("x@y")
    assert verify.provider_for("Max@GMX.de").host == "mail.gmx.net"
    assert verify.provider_for("a@gmail.com").app_password
    other = verify.provider_for("info@beispiel-firma.de")
    assert other.key == "other" and other.host == "smtp.beispiel-firma.de" and other.starttls


def test_code_right_wrong_expired_blocked():
    c = verify.Code("123456", sent_at=1000)
    assert c.check("000000", now=1001) == "wrong"
    assert c.check(" 123 456 ", now=1002) == "ok"
    old = verify.Code("123456", sent_at=1000)
    assert old.check("123456", now=1000 + verify.CODE_SECONDS + 1) == "expired"
    b = verify.Code("123456", sent_at=1000)
    results = [b.check("111111", now=1001) for _ in range(verify.CODE_TRIES)]
    assert results[-1] == "blocked" and b.check("123456", now=1002) == "blocked"


def test_new_code_and_resend():
    c = verify.Code.new(now=500)
    assert len(c.value) == 6 and c.value.isdigit()
    assert not c.can_resend(now=500 + verify.RESEND_SECONDS - 1) and c.can_resend(now=500 + verify.RESEND_SECONDS)


def test_message_contains_code():
    m = verify.message("a@web.de", "a@web.de", "042317", german=True)
    assert "042317" in m["Subject"] and "042317" in m.get_content() and m["To"] == "a@web.de"


def test_store_roundtrip_without_password(tmp_path):
    p = str(tmp_path / "novaos" / "account.json")
    verify.save({"email": "a@web.de", "verified": True}, p)
    assert verify.load(p) == {"email": "a@web.de", "verified": True}
    assert "password" not in open(p).read()
    assert verify.load(str(tmp_path / "missing.json")) == {}
