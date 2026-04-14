# AMHS/SWIM Gateway Test Tool Security Guide

This guide details the security configuration required to comply with EUR Doc 047 Appendix A (AMHS SEC) and SWIM security standards.

## 1. Keystore Generation
A PKCS#12 keystore is required for mutually-authenticated endpoints. Use `scripts/generate_keystore.sh` to create the keystore with standard testing identities.

## 2. Certificates
The generated certificate `gateway_identity` is bound to `CN=AMHS-SWIM-Gateway` for test environments. Please ensure the trust store includes the issuer if used against external SWIM components.
