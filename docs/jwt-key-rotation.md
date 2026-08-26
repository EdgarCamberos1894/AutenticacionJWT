# JWT signing-key rotation

Production access tokens are signed with one active RSA private key and verified against a key ring containing the active public key plus any configured previous public keys.

Spring Security 7.1 derives the JWT `kid` from the RSA JWK RFC 7638 thumbprint. New tokens therefore identify the active signing key without requiring an operator-maintained id.

## Configuration

The active pair remains configured with:

- `JWT_PUBLIC_KEY_LOCATION`
- `JWT_PRIVATE_KEY_LOCATION`

Previous verification-only keys are optional and are supplied as a comma-separated list:

- `JWT_PREVIOUS_PUBLIC_KEY_LOCATIONS`

Never keep previous private keys in the rotation ring. Historical keys exist only so already-issued access tokens can finish their short lifetime.

## Safe rotation procedure

1. Generate a new RSA key pair of at least 3072 bits.
2. Keep the current public key available and add its resource location to `JWT_PREVIOUS_PUBLIC_KEY_LOCATIONS`.
3. Replace `JWT_PUBLIC_KEY_LOCATION` and `JWT_PRIVATE_KEY_LOCATION` with the new active pair in the same deployment.
4. Deploy. New access tokens are signed by the new private key and carry its `kid`; tokens signed by configured previous public keys continue to validate.
5. Wait at least the configured access-token TTL plus the JWT validator clock-skew allowance after the last token could have been issued with the old key.
6. Remove that expired public key from `JWT_PREVIOUS_PUBLIC_KEY_LOCATIONS` in a later deployment.

Do not remove an old public key in the same deployment that activates its replacement. Doing so would invalidate still-live access tokens and force an avoidable re-authentication spike.

## Multiple rotations

The previous-key setting accepts multiple comma-separated public-key locations, which allows a staged rollout or an emergency rotation before an earlier transition window has fully drained. Keep the set minimal and remove keys once no unexpired access token can reference them.

The application rejects duplicate RSA public keys and validates that every configured active or previous production key is at least 3072 bits. The active public/private resources must belong to the same RSA pair.

## Compatibility

The decoder uses the JWK key ring rather than a single public key. Tokens carrying `kid` select the corresponding public key. A token without `kid` can still be verified against the configured ring when its signature matches one of those keys, which preserves compatibility across the first migration from older token issuers.

Issuer, audience, `token_type`, UUID subject/session validation, token TTL and all access-token claims are unchanged by key rotation.
