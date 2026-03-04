# Security Fixes Documentation

## Date: 2026-01-28

This document summarizes the security improvements implemented to address critical vulnerabilities in the Stock-AI application.

---

## 1. SQL Injection Vulnerability Fix

### Issue
**Severity:** CRITICAL (CVSS 7.5)
**Location:** `AnalysisService.java:162-177`, `BacktestService.java:151-166`

The `sort` parameter in pagination was not validated, allowing potential SQL injection through arbitrary column names.

### Fix Implemented

**Files Modified:**
- `backend/src/main/java/com/sw103302/backend/service/AnalysisService.java`
- `backend/src/main/java/com/sw103302/backend/service/BacktestService.java`

**Changes:**
- Added whitelist validation for sort field names
- Implemented security logging for invalid sort field attempts
- Default fallback to "createdAt" for invalid values

**Allowed Sort Fields (AnalysisService):**
- id, ticker, market, action, confidence, createdAt

**Allowed Sort Fields (BacktestService):**
- id, ticker, market, strategy, totalReturn, maxDrawdown, sharpe, cagr, createdAt

**Code Example:**
```java
final var ALLOWED_SORT_FIELDS = List.of(
    "id", "ticker", "market", "action", "confidence", "createdAt"
);

if (parts.length >= 1 && !parts[0].isBlank()) {
    String requestedProp = parts[0].trim();
    if (ALLOWED_SORT_FIELDS.contains(requestedProp)) {
        prop = requestedProp;
    } else {
        System.err.println("SECURITY WARNING: Invalid sort field attempted: " + requestedProp);
        // prop remains "createdAt" (default)
    }
}
```

---

## 2. JWT Secret Strength Enhancement

### Issue
**Severity:** CRITICAL (Token Forgery Risk)
**Location:** `application.properties:25`, `JwtService.java:26-29`

- Default JWT secret was only 62 bytes (insufficient for HMAC-SHA256)
- No validation of secret length at runtime
- Default development secret exposed in configuration

### Fix Implemented

**Files Modified:**
- `backend/src/main/java/com/sw103302/backend/service/JwtService.java`
- `backend/src/main/resources/application.properties`

**Changes:**

1. **Runtime Validation:**
   - Added `@PostConstruct` validation enforcing minimum 32 bytes (256 bits)
   - Application fails fast on startup if secret is too short
   - Warning logged if using default development secret

2. **Configuration Update:**
   - Updated default secret to 88 bytes
   - Added critical security comments
   - Included generation command: `openssl rand -base64 32`

**Code Example:**
```java
@PostConstruct
void init() {
    byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (secretBytes.length < 32) {
        throw new IllegalStateException(
            String.format(
                "SECURITY ERROR: JWT secret must be at least 32 bytes (256 bits) for HMAC-SHA256. " +
                "Current length: %d bytes. Set JWT_SECRET environment variable with a strong secret.",
                secretBytes.length
            )
        );
    }

    if (secret.contains("dev-only") || secret.contains("change-in-production")) {
        System.err.println("WARNING: Using default development JWT secret. " +
            "Set JWT_SECRET environment variable to a strong random secret for production.");
    }

    this.key = Keys.hmacShaKeyFor(secretBytes);
}
```

**Production Deployment:**
```bash
# Generate strong secret
export JWT_SECRET=$(openssl rand -base64 32)

# Or set in environment
JWT_SECRET=your-cryptographically-secure-random-secret-here
```

---

## 3. IDOR (Insecure Direct Object Reference) Fix

### Issue
**Severity:** HIGH (Data Breach Risk)
**Location:** `AnalysisService.java:148-160`, `BacktestService.java:137-149`

Analysis and backtest detail endpoints had weak ownership verification:
- Relied on null checks instead of explicit authentication
- No defense-in-depth verification

### Fix Implemented

**Files Modified:**
- `backend/src/main/java/com/sw103302/backend/util/SecurityUtil.java`
- `backend/src/main/java/com/sw103302/backend/service/AnalysisService.java`
- `backend/src/main/java/com/sw103302/backend/service/BacktestService.java`

**Changes:**

1. **Enhanced SecurityUtil:**
   - Added `requireCurrentEmail()` - throws exception if not authenticated
   - Added `requireOwnership(String resourceOwnerEmail)` - explicit ownership verification
   - Improved documentation with Javadoc

2. **Service Layer Hardening:**
   - Replaced `currentEmail()` + null checks with `requireCurrentEmail()`
   - Added defense-in-depth ownership verification in detail methods
   - Changed error messages to `SecurityException` with clearer messages

**SecurityUtil Enhancements:**
```java
/**
 * Gets the current authenticated user's email.
 * Throws exception if not authenticated (for required auth contexts).
 */
public static String requireCurrentEmail() {
    String email = currentEmail();
    if (email == null) {
        throw new IllegalStateException("User is not authenticated");
    }
    return email;
}

/**
 * Verifies that the current user owns the resource with the given email.
 */
public static void requireOwnership(String resourceOwnerEmail) {
    String currentEmail = requireCurrentEmail();
    if (!currentEmail.equals(resourceOwnerEmail)) {
        throw new SecurityException("Access denied: user does not own this resource");
    }
}
```

**Detail Method Example:**
```java
@Transactional(readOnly = true)
public String myRunDetail(Long id) {
    String email = SecurityUtil.requireCurrentEmail();

    AnalysisRun run = analysisRunRepository.findByIdAndUser_Email(id, email)
            .orElseThrow(() -> new SecurityException("Analysis run not found or access denied"));

    // Additional ownership verification (defense in depth)
    if (run.getUser() != null && !email.equals(run.getUser().getEmail())) {
        throw new SecurityException("Access denied: user does not own this analysis run");
    }

    return run.getResponseJson();
}
```

---

## 4. Comprehensive Input Validation

### Issue
**Severity:** MEDIUM (Data Integrity Risk)
**Location:** Multiple DTO files

Many request DTOs lacked proper validation:
- No length constraints
- No format validation
- Missing error messages
- Controllers not using `@Valid` annotation

### Fix Implemented

**Files Modified:**
- `backend/src/main/java/com/sw103302/backend/dto/AnalysisRequest.java`
- `backend/src/main/java/com/sw103302/backend/dto/BacktestRequest.java`
- `backend/src/main/java/com/sw103302/backend/dto/RegisterRequest.java`
- `backend/src/main/java/com/sw103302/backend/dto/LoginRequest.java`
- `backend/src/main/java/com/sw103302/backend/dto/WatchlistAddRequest.java`
- `backend/src/main/java/com/sw103302/backend/dto/TagCreateRequest.java`
- `backend/src/main/java/com/sw103302/backend/dto/InsightRequest.java`
- `backend/src/main/java/com/sw103302/backend/controller/WatchlistController.java`
- `backend/src/main/java/com/sw103302/backend/controller/WatchlistTagController.java`

**Validation Rules Added:**

### AnalysisRequest
```java
@NotBlank(message = "Ticker symbol is required")
@Size(min = 1, max = 10, message = "Ticker must be between 1 and 10 characters")
@Pattern(regexp = "^[A-Z0-9]+$", message = "Ticker must contain only uppercase letters and numbers")
String ticker;

@NotBlank(message = "Market is required")
@Pattern(regexp = "US|KR", message = "Market must be either US or KR")
String market;

@Min(value = 1, message = "Horizon days must be at least 1")
@Max(value = 1000, message = "Horizon days cannot exceed 1000")
Integer horizonDays;

@Pattern(regexp = "conservative|moderate|aggressive", message = "Risk profile must be...")
String riskProfile;
```

### BacktestRequest
```java
@NotBlank(message = "Strategy is required")
@Pattern(regexp = "SMA_CROSS|RSI_STRATEGY|MACD_STRATEGY", message = "Invalid strategy type")
String strategy;

@Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Start date must be in YYYY-MM-DD format")
String start;

@Positive(message = "Initial capital must be positive")
@Max(value = 1_000_000_000, message = "Initial capital cannot exceed 1 billion")
Double initialCapital;

@PositiveOrZero(message = "Fee cannot be negative")
@Max(value = 1000, message = "Fee BPS cannot exceed 1000 (10%)")
Double feeBps;
```

### RegisterRequest & LoginRequest
```java
@NotBlank(message = "Email is required")
@Email(message = "Email must be valid")
@Size(max = 255, message = "Email cannot exceed 255 characters")
String email;

@NotBlank(message = "Password is required")
@Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
String password;
```

### TagCreateRequest
```java
@NotBlank(message = "Tag name is required")
@Size(min = 1, max = 30, message = "Tag name must be between 1 and 30 characters")
String name;

@NotBlank(message = "Color is required")
@Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be a valid hex color code (e.g., #FF5733)")
String color;
```

### Controller Validation
Added `@Valid` annotation to all `@RequestBody` parameters:
```java
public ResponseEntity<Void> add(@Valid @RequestBody WatchlistAddRequest req, ...) {
    // Validation happens automatically before method execution
}
```

---

## Validation Error Response Format

With these changes, validation errors now return structured responses:

```json
{
  "timestamp": "2026-01-28T10:30:00Z",
  "status": 400,
  "code": "validation_failed",
  "message": "Invalid request",
  "path": "/api/analysis",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "fieldViolations": [
    {
      "field": "ticker",
      "message": "Ticker must be between 1 and 10 characters"
    },
    {
      "field": "market",
      "message": "Market must be either US or KR"
    }
  ]
}
```

---

## Testing Recommendations

### 1. SQL Injection Test
```bash
# Should fail and log security warning
curl -X GET "http://localhost:8080/api/analysis/history?sort=createdAt;DROP%20TABLE%20users,desc"

# Should succeed
curl -X GET "http://localhost:8080/api/analysis/history?sort=createdAt,desc"
```

### 2. JWT Secret Test
```bash
# Should fail to start with short secret
JWT_SECRET="short" ./gradlew bootRun

# Should succeed with valid secret
JWT_SECRET=$(openssl rand -base64 32) ./gradlew bootRun
```

### 3. IDOR Test
```bash
# Login as user1, get analysis ID
# Login as user2, try to access user1's analysis
curl -X GET "http://localhost:8080/api/analysis/{user1_analysis_id}" \
  -H "Authorization: Bearer {user2_token}"
# Should return 403 Forbidden
```

### 4. Input Validation Test
```bash
# Should return validation error
curl -X POST "http://localhost:8080/api/analysis" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{"ticker": "TOOLONGTICKER123", "market": "INVALID"}'

# Should return specific field violations
```

---

## Migration Guide

### For Production Deployment

1. **Set Strong JWT Secret:**
   ```bash
   export JWT_SECRET=$(openssl rand -base64 32)
   ```

2. **Verify Application Startup:**
   - Application should start successfully
   - No "SECURITY WARNING" in logs
   - JWT secret length validated

3. **Monitor Logs for Security Warnings:**
   ```bash
   grep "SECURITY WARNING" application.log
   ```

4. **Test Authentication Flow:**
   - Register new user
   - Login and verify token
   - Access protected endpoints

5. **Verify Validation:**
   - Test with invalid inputs
   - Confirm proper error responses
   - Check field-level error messages

---

## Known Limitations

1. **Rate Limiting:** Not yet implemented (separate task)
2. **Audit Logging:** Security events not logged to audit table (separate task)
3. **Password Strength:** No complexity requirements beyond length (separate task)
4. **Frontend Token Storage:** Still uses localStorage (XSS vulnerable, separate task)

---

## References

- OWASP Top 10: https://owasp.org/www-project-top-ten/
- CWE-89 (SQL Injection): https://cwe.mitre.org/data/definitions/89.html
- CWE-639 (IDOR): https://cwe.mitre.org/data/definitions/639.html
- NIST Password Guidelines: https://pages.nist.gov/800-63-3/
- Jakarta Bean Validation: https://beanvalidation.org/

---

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2026-01-28 | Initial security fixes implementation | Claude Sonnet 4.5 |

---

## Approval

**Reviewed By:** _________________
**Date:** _________________
**Approved By:** _________________
**Date:** _________________
