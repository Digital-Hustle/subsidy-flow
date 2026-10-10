"""Генерирует subsidy-realm.json (PLT-05). Секреты — плейсхолдеры env, Keycloak подставляет их при импорте.
Запуск: python3 infra/keycloak/gen_realm.py
"""
import json
import pathlib

SL = chr(47)
HOST = "https:" + SL + SL + "${PUBLIC_HOST}"
ROLES = ["SPECIALIST", "SENIOR_SPECIALIST", "AUDITOR"]
SERVICES = ["package", "docproc", "rules", "assistant"]
# ocr (Python) — не клиент Keycloak, но принимает токены svc-docproc / svc-package с aud=ocr
OCR_CALLERS = {"package", "docproc"}


def env(name: str) -> str:
    return "${" + name + "}"


def user(login: str, name: str, role: str, pwd_var: str) -> dict:
    first, last = name.split(" ", 1)
    return {
        "username": login, "enabled": True, "emailVerified": True,
        "firstName": first, "lastName": last, "email": f"{login}@subsidy.local",
        "credentials": [{"type": "password", "value": env(pwd_var), "temporary": False}],
        "realmRoles": [role],
    }


realm = {
    "realm": "subsidy",
    "enabled": True,
    "displayName": "Субсидии",
    "sslRequired": "external",
    "bruteForceProtected": True,
    "failureFactor": 5,
    "waitIncrementSeconds": 60,
    "maxFailureWaitSeconds": 900,
    "accessTokenLifespan": 300,
    "ssoSessionIdleTimeout": 1800,
    "otpPolicyType": "totp",
    # FE-UI-02: форма входа в стиле фронта (themes/subsidy) и «запомнить меня»
    "loginTheme": "subsidy",
    "rememberMe": True,
    "ssoSessionIdleTimeoutRememberMe": 604800,
    "ssoSessionMaxLifespanRememberMe": 604800,
    "internationalizationEnabled": True,
    "supportedLocales": ["ru"],
    "defaultLocale": "ru",
    "roles": {"realm": [{"name": r} for r in ROLES]},
    "clients": [
        {
            "clientId": "subsidy-specialist",
            "name": "Рабочее место специалиста",
            "publicClient": True,
            "standardFlowEnabled": True,
            "directAccessGrantsEnabled": False,
            "redirectUris": [HOST + SL + "specialist" + SL + "*"],
            "webOrigins": [HOST],
            "attributes": {"pkce.code.challenge.method": "S256", "post.logout.redirect.uris": HOST + SL + "*"},
            "protocolMappers": [*[{
                "name": f"aud-{s}", "protocol": "openid-connect", "protocolMapper": "oidc-audience-mapper",
                "config": {"included.custom.audience": s, "access.token.claim": "true", "id.token.claim": "false"},
            } for s in SERVICES], {
                "name": "realm-roles", "protocol": "openid-connect",
                "protocolMapper": "oidc-usermodel-realm-role-mapper",
                "config": {"claim.name": "roles", "multivalued": "true", "access.token.claim": "true", "jsonType.label": "String"},
            }],
        },
        *[{
            "clientId": f"svc-{s}",
            "publicClient": False,
            "serviceAccountsEnabled": True,
            "standardFlowEnabled": False,
            "secret": env(f"KC_SVC_{s.upper()}_SECRET"),
            "protocolMappers": [{
                "name": f"svc-aud-{t}", "protocol": "openid-connect", "protocolMapper": "oidc-audience-mapper",
                "config": {"included.custom.audience": t, "access.token.claim": "true", "id.token.claim": "false"},
            } for t in [*(x for x in SERVICES if x != s), *(["ocr"] if s in OCR_CALLERS else [])]],
        } for s in SERVICES],
    ],
    "users": [
        user("specialist", "Анна Смирнова", "SPECIALIST", "KC_DEMO_SPECIALIST_PASSWORD"),
        user("senior", "Игорь Волков", "SENIOR_SPECIALIST", "KC_DEMO_SENIOR_PASSWORD"),
        user("auditor", "Мария Лебедева", "AUDITOR", "KC_DEMO_AUDITOR_PASSWORD"),
    ],
}

out = pathlib.Path(__file__).with_name("subsidy-realm.json")
out.write_text(json.dumps(realm, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"записан {out}")
