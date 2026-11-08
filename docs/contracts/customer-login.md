# Customer login

Use the existing POST /api/v1/auth/login endpoint with email and password.
The redundant POST /api/v1/auth/customer-login endpoint and its phone/password
implementation have been removed by owner request on 2026-11-08.
Customers and employees use the existing email/password login and token lifecycle.
Registration now verifies email ownership; see [registration-sms.md](registration-sms.md).
WORK-016 documents the historical phone-login design and is superseded by this decision.
