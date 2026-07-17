# DNSSR Domain Filtering

DNSSR controls whether network requests to a domain are allowed. Its filtering capabilities differ according to whether application traffic is encrypted and whether that encryption can be decrypted.

## Language

**User CA**:
A certificate authority uniquely generated for one DNSSR installation and installed by the user so participating clients can trust HTTPS inspection. Its private key cannot be exported, and resetting it creates a new authority that must be installed again.
_Avoid_: User certificate

**HTTPS domain filtering**:
Blocking an HTTPS request by domain after its TLS connection has been successfully decrypted and its HTTP authority has been identified.
_Avoid_: Universal HTTPS decryption

**Cleartext HTTP domain filtering**:
Blocking an unencrypted HTTP request by the domain identified in its HTTP authority.
_Avoid_: HTTPS filtering

**HTTP authority**:
The target domain expressed by an HTTP request as `Host` in HTTP/1.1 or `:authority` in HTTP/2 and HTTP/3. It is the final domain identity used by HTTP domain filtering when it has been successfully obtained.
_Avoid_: Host header when referring to all HTTP versions

**Inspection target**:
An application explicitly selected by the user for both HTTPS decryption and cleartext HTTP domain filtering. The set of inspection targets is empty by default.
_Avoid_: All applications, excluded application

**Excluded application**:
An application whose traffic bypasses DNSSR entirely. It cannot simultaneously be an inspection target; selecting either status removes the other.
_Avoid_: Allowed application, uninspected application

**HTTP inspection mode**:
An experimental, independently enabled operating mode that applies HTTP inspection to inspection targets. It is disabled by default and is distinct from DNSSR's DNS-only operation.
_Avoid_: Default mode, DNS mode

**Inspection fallback**:
The runtime state entered after the HTTP inspection data plane fails, where DNSSR continues DNS-only operation and waits for the user to retry inspection.
_Avoid_: Inspection failure, automatic retry

**HTTPS inspection readiness**:
The user-confirmed state that the DNSSR User CA has been installed, allowing HTTPS decryption attempts to begin. It does not assert that every inspection target trusts the User CA.
_Avoid_: CA trusted, compatible application

**Inspection module**:
An optional executable DEX and native-library bundle downloaded and verified by DNSSR to provide the experimental HTTP inspection data plane. It runs with DNSSR's authority and is not an ordinary content update.
_Avoid_: Resource pack, plugin application

**Inspection failure**:
An HTTPS connection whose HTTP authority cannot be obtained through decryption. It remains allowed by default and is distinct from a request that was inspected and allowed.
_Avoid_: Allowed request, blocked request

**Inspection bypass**:
A connection forwarded without inspection because the inspection data plane reached a resource limit. It is recorded separately from an inspection failure and an inspected request.
_Avoid_: Allowed request, inspection failure

**Domain policy**:
The shared allow and block rules, including subscription rules, applied consistently to DNS names and HTTP authorities. An allow rule takes precedence over a matching block rule.
_Avoid_: DNS-only rule, HTTP rule

**Blocked HTTP request**:
An inspected request terminated because its HTTP authority matches the domain policy. It produces no synthetic response or block page and does not terminate unrelated requests sharing the same connection.
_Avoid_: Blocked connection, error response

**Inspected HTTP request**:
One HTTP request independently identified by its authority and evaluated against the domain policy, including when multiple requests or authorities share one transport connection.
_Avoid_: Inspected connection

**Invalid HTTP request**:
A request confirmed as HTTP whose syntax or authority is missing, conflicting, or too ambiguous for one safe filtering decision. It is terminated rather than treated as an inspection failure.
_Avoid_: Inspection failure, allowed request

**Inspection record**:
Minimal filtering metadata containing the application, HTTP authority, protocol, outcome, matching rule, and time. It excludes paths, query parameters, headers, credentials, message bodies, and raw decrypted traffic.
_Avoid_: Traffic capture, HTTP log
